package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.catalog.StockUpdateRequest;
import com.DatShop.dto.order.CheckoutRequest;
import com.DatShop.model.Brand;
import com.DatShop.model.CartItem;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Color;
import com.DatShop.model.Order;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Product;
import com.DatShop.model.ProductImage;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.RoleName;
import com.DatShop.model.Size;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.model.Voucher;
import com.DatShop.model.VoucherType;
import com.DatShop.repository.BrandRepository;
import com.DatShop.repository.CartItemRepository;
import com.DatShop.repository.CartRepository;
import com.DatShop.repository.CategoryRepository;
import com.DatShop.repository.ColorRepository;
import com.DatShop.repository.OrderItemRepository;
import com.DatShop.repository.OrderRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.SizeRepository;
import com.DatShop.repository.UserRepository;
import com.DatShop.repository.VoucherRepository;
import com.DatShop.service.ProductVariantService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real MySQL concurrency tests against {@code smart_fashion_shop_concurrency_test} only.
 * This class is not {@code @Transactional}: fixtures must be committed before worker threads start.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = ConcurrencyTestDatabaseInitializer.class)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ConcurrentInventoryIntegrationTest {

    private static final String PASSWORD = "S3cure-Passw0rd";
    private static final String OVER_STOCK = "Requested quantity exceeds available stock";
    private static final String TEST_DB = ConcurrencyTestDatabaseInitializer.TEST_DATABASE;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private Clock clock;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ProductVariantService productVariantService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private ProductImageRepository productImageRepository;
    @Autowired
    private SizeRepository sizeRepository;
    @Autowired
    private ColorRepository colorRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;

    @BeforeEach
    void assertIsolatedDatabase() throws Exception {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertThat(database).isEqualTo(TEST_DB);
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(url).contains(TEST_DB);
            assertThat(ConcurrencyTestDatabaseInitializer.containsCreateDatabaseIfNotExist(url)).isFalse();
        }
    }

    @Test
    void twoCustomersConcurrentCheckout_lastUnit_oneCreatedOneRejected() throws Exception {
        CatalogFixture catalog = publishedProduct(1);
        Voucher voucher = persistUnusedVoucher();
        Session buyerA = customerWithCart(catalog.variantId);
        Session buyerB = customerWithCart(catalog.variantId);
        long ordersBefore = orderRepository.count();
        long itemsBefore = orderItemRepository.count();

        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TimedCheckout> first = executor.submit(() -> checkoutAfter(start, buyerA));
            Future<TimedCheckout> second = executor.submit(() -> checkoutAfter(start, buyerB));
            TimedCheckout timedA = awaitTimedCheckout(first);
            TimedCheckout timedB = awaitTimedCheckout(second);
            MvcResult resultA = timedA.result();
            MvcResult resultB = timedB.result();

            int statusA = resultA.getResponse().getStatus();
            int statusB = resultB.getResponse().getStatus();
            assertNoLockFailure(statusA, body(resultA));
            assertNoLockFailure(statusB, body(resultB));

            if (statusA == 201 && statusB == 201) {
                fail("STOP D3: overselling. Both checkouts returned 201. "
                        + "CheckoutService.checkout / ProductVariantRepository.lockByIdsOrderByIdAsc. "
                        + "Do not change production code without approval.");
            }
            assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(201, 400);

            Session winner = statusA == 201 ? buyerA : buyerB;
            Session loser = statusA == 201 ? buyerB : buyerA;
            MvcResult rejected = statusA == 201 ? resultB : resultA;
            JsonNode created = read(statusA == 201 ? resultA : resultB);
            JsonNode error = read(rejected);
            String orderNumber = created.get("orderNumber").asText();
            long winnerElapsedMs = statusA == 201 ? timedA.elapsedMs() : timedB.elapsedMs();
            long loserElapsedMs = statusA == 201 ? timedB.elapsedMs() : timedA.elapsedMs();

            assertThat(error.get("message").asText()).isEqualTo(OVER_STOCK);
            assertThat(orderNumber).startsWith("ORD-");
            assertThat(created.get("items")).hasSize(1);
            assertThat(created.get("items").get(0).get("quantity").asInt()).isEqualTo(1);
            assertThat(loserElapsedMs).isGreaterThanOrEqualTo(0);
            assertThat(winnerElapsedMs).isGreaterThanOrEqualTo(0);

            assertCommittedLastUnitState(
                    catalog.variantId, winner.userId, loser.userId, orderNumber, ordersBefore, itemsBefore);
            assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getUsedCount()).isZero();
        }
    }

    @Test
    void sameCustomerConcurrentCheckout_oneCreatedOneEmptyCart() throws Exception {
        CatalogFixture catalog = publishedProduct(4);
        Voucher voucher = persistUnusedVoucher();
        Session buyer = customerWithCart(catalog.variantId);
        long ordersBefore = orderRepository.count();
        int stockBefore = productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity();

        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TimedCheckout> first = executor.submit(() -> checkoutAfter(start, buyer, voucher.getCode()));
            Future<TimedCheckout> second = executor.submit(() -> checkoutAfter(start, buyer, voucher.getCode()));
            TimedCheckout timedA = awaitTimedCheckout(first);
            TimedCheckout timedB = awaitTimedCheckout(second);
            MvcResult resultA = timedA.result();
            MvcResult resultB = timedB.result();
            int statusA = resultA.getResponse().getStatus();
            int statusB = resultB.getResponse().getStatus();
            assertNoLockFailure(statusA, body(resultA));
            assertNoLockFailure(statusB, body(resultB));
            assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(201, 400);

            MvcResult createdResult = statusA == 201 ? resultA : resultB;
            MvcResult rejected = statusA == 201 ? resultB : resultA;
            JsonNode created = read(createdResult);
            assertThat(read(rejected).get("message").asText()).isEqualTo("Cart is empty");
            String orderNumber = created.get("orderNumber").asText();
            assertThat(orderNumber).startsWith("ORD-");

            String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            assertThat(database).isEqualTo(TEST_DB);
            Integer stock = jdbcTemplate.queryForObject(
                    "SELECT stock_quantity FROM product_variants WHERE id = ?", Integer.class, catalog.variantId);
            assertThat(stock).isEqualTo(stockBefore - 1);
            Integer buyerOrders = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, buyer.userId);
            Integer allOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
            assertThat(buyerOrders).isEqualTo(1);
            assertThat(allOrders).isEqualTo((int) ordersBefore + 1);
            Integer payments = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM payments p
                            JOIN orders o ON o.id = p.order_id
                            WHERE o.order_number = ?
                            """,
                    Integer.class,
                    orderNumber);
            assertThat(payments).isZero();
            assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
            assertThat(cartItems(buyer.userId)).isEmpty();
        }
    }

    @Test
    void adminUpdateStockThenCheckout_realServices_adminCommitFirst() throws Exception {
        CatalogFixture catalog = publishedProduct(1);
        Session buyer = customerWithCart(catalog.variantId);
        productVariantService.updateStock(catalog.productId, catalog.variantId, new StockUpdateRequest(10));
        MvcResult checkout = performCheckout(buyer);
        assertThat(checkout.getResponse().getStatus()).isEqualTo(201);
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(9);
        assertThat(orderRepository.countByUser_Id(buyer.userId)).isEqualTo(1);
        assertThat(cartItems(buyer.userId)).isEmpty();
    }

    @Test
    void checkoutThenAdminUpdateStock_realServices_checkoutCommitFirst() throws Exception {
        CatalogFixture catalog = publishedProduct(5);
        Session buyer = customerWithCart(catalog.variantId);
        MvcResult checkout = performCheckout(buyer);
        assertThat(checkout.getResponse().getStatus()).isEqualTo(201);
        String orderNumber = read(checkout).get("orderNumber").asText();
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(4);
        productVariantService.updateStock(catalog.productId, catalog.variantId, new StockUpdateRequest(20));
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(20);
        assertThat(orderRepository.countByUser_Id(buyer.userId)).isEqualTo(1);
        Order order = orderRepository.findByOrderNumberAndUser_Id(orderNumber, buyer.userId).orElseThrow();
        assertThat(orderItemRepository.findByOrder_IdOrderByIdAsc(order.getId())).hasSize(1);
        assertThat(cartItems(buyer.userId)).isEmpty();
    }

    @Test
    void adminHoldsProductAndVariantRowLockThenCheckout_mysqlLockNotFullUpdateStock() throws Exception {
        CatalogFixture catalog = publishedProduct(1);
        Session buyer = customerWithCart(catalog.variantId);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch checkoutStarted = new CountDownLatch(1);
        CountDownLatch adminMayCommit = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> adminLock = executor.submit(() -> holdAdminLockOrder(
                    catalog.productId, catalog.variantId, 10, lockHeld, checkoutStarted, adminMayCommit));
            assertThat(lockHeld.await(20, TimeUnit.SECONDS)).isTrue();
            Future<MvcResult> checkout = executor.submit(() -> {
                checkoutStarted.countDown();
                return performCheckout(buyer);
            });
            assertThat(checkoutStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(checkout.isDone()).isFalse();
            adminMayCommit.countDown();
            adminLock.get(30, TimeUnit.SECONDS);
            MvcResult result = awaitCheckout(checkout);
            assertNoLockFailure(result.getResponse().getStatus(), body(result));
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity())
                    .isEqualTo(9);
            assertThat(orderRepository.countByUser_Id(buyer.userId)).isEqualTo(1);
        }
    }

    @Test
    void checkoutLockOrderHoldsVariantThenAdminUpdateStock_mysqlLockNotFullCheckout() throws Exception {
        CatalogFixture catalog = publishedProduct(5);
        Session buyer = customerWithCart(catalog.variantId);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch adminStarted = new CountDownLatch(1);
        CountDownLatch checkoutMayCommit = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> checkoutLock = executor.submit(() -> holdCheckoutLockOrder(
                    buyer.userId, catalog.variantId, lockHeld, adminStarted, checkoutMayCommit));
            assertThat(lockHeld.await(20, TimeUnit.SECONDS)).isTrue();
            Future<?> admin = executor.submit(() -> {
                adminStarted.countDown();
                return productVariantService.updateStock(
                        catalog.productId, catalog.variantId, new StockUpdateRequest(20));
            });
            assertThat(adminStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(admin.isDone()).isFalse();
            checkoutMayCommit.countDown();
            checkoutLock.get(30, TimeUnit.SECONDS);
            admin.get(30, TimeUnit.SECONDS);
            assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity())
                    .isEqualTo(20);
            assertThat(orderRepository.countByUser_Id(buyer.userId)).isZero();
            assertThat(cartItems(buyer.userId)).hasSize(1);
        }
    }

    @Test
    void realUpdateStockAndCheckoutAtBarrier_consistentFinalState() throws Exception {
        CatalogFixture catalog = publishedProduct(5);
        Session buyer = customerWithCart(catalog.variantId);
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> checkout = executor.submit(() -> {
                start.await(15, TimeUnit.SECONDS);
                return performCheckout(buyer);
            });
            Future<Integer> admin = executor.submit(() -> {
                start.await(15, TimeUnit.SECONDS);
                return productVariantService.updateStock(
                        catalog.productId, catalog.variantId, new StockUpdateRequest(20)).stockQuantity();
            });
            MvcResult result = awaitCheckout(checkout);
            int adminStock = admin.get(30, TimeUnit.SECONDS);
            assertNoLockFailure(result.getResponse().getStatus(), body(result));
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            int stock = productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity();
            assertThat(stock).isGreaterThanOrEqualTo(0);
            assertThat(adminStock).isEqualTo(20);
            assertThat(stock).isIn(19, 20);
            assertThat(orderRepository.countByUser_Id(buyer.userId)).isEqualTo(1);
            assertThat(cartItems(buyer.userId)).isEmpty();
        }
    }

    private void holdAdminLockOrder(
            long productId,
            long variantId,
            int stock,
            CountDownLatch lockHeld,
            CountDownLatch checkoutStarted,
            CountDownLatch adminMayCommit) {
        requiresNew().executeWithoutResult(status -> {
            productRepository.findByIdForUpdate(productId).orElseThrow();
            ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId).orElseThrow();
            variant.setStockQuantity(stock);
            productVariantRepository.saveAndFlush(variant);
            lockHeld.countDown();
            awaitLatch(checkoutStarted, "Checkout thread did not start while admin held the row lock");
            awaitLatch(adminMayCommit, "Timed out holding admin variant row lock");
        });
    }

    private void holdCheckoutLockOrder(
            long userId,
            long variantId,
            CountDownLatch lockHeld,
            CountDownLatch adminStarted,
            CountDownLatch checkoutMayCommit) {
        requiresNew().executeWithoutResult(status -> {
            userRepository.findByIdForUpdate(userId).orElseThrow();
            List<ProductVariant> locked = productVariantRepository.lockByIdsOrderByIdAsc(List.of(variantId));
            assertThat(locked).hasSize(1);
            lockHeld.countDown();
            awaitLatch(adminStarted, "Admin thread did not start while checkout held the row lock");
            awaitLatch(checkoutMayCommit, "Timed out holding checkout variant row lock");
        });
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(40);
        return template;
    }

    private static void awaitLatch(CountDownLatch latch, String message) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                fail("STOP D3: " + message);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail("STOP D3: interrupted while waiting for the other transaction");
        }
    }

    private void assertCommittedLastUnitState(
            long variantId,
            long winnerUserId,
            long loserUserId,
            String orderNumber,
            long ordersBefore,
            long itemsBefore) {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertThat(database).isEqualTo(TEST_DB);
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product_variants WHERE id = ?", Integer.class, variantId);
        assertThat(stock).isZero();
        Integer negative = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_variants WHERE id = ? AND stock_quantity < 0",
                Integer.class,
                variantId);
        assertThat(negative).isZero();
        Integer orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        Integer itemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class);
        assertThat(orderCount).isEqualTo((int) ordersBefore + 1);
        assertThat(itemCount).isEqualTo((int) itemsBefore + 1);
        Integer winnerOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, winnerUserId);
        Integer loserOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, loserUserId);
        assertThat(winnerOrders).isEqualTo(1);
        assertThat(loserOrders).isZero();
        Integer orderItems = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM order_items i
                        JOIN orders o ON o.id = i.order_id
                        WHERE o.order_number = ? AND o.user_id = ? AND i.variant_id = ? AND i.quantity = 1
                        """,
                Integer.class,
                orderNumber,
                winnerUserId,
                variantId);
        assertThat(orderItems).isEqualTo(1);
        Integer winnerCartItems = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM cart_items ci
                        JOIN carts c ON c.id = ci.cart_id
                        WHERE c.user_id = ?
                        """,
                Integer.class,
                winnerUserId);
        Integer loserCartItems = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM cart_items ci
                        JOIN carts c ON c.id = ci.cart_id
                        WHERE c.user_id = ? AND ci.product_variant_id = ?
                        """,
                Integer.class,
                loserUserId,
                variantId);
        assertThat(winnerCartItems).isZero();
        assertThat(loserCartItems).isEqualTo(1);
        Order order = orderRepository.findByOrderNumberAndUser_Id(orderNumber, winnerUserId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(cartItems(loserUserId)).hasSize(1);
    }

    private TimedCheckout checkoutAfter(CyclicBarrier start, Session buyer) throws Exception {
        return checkoutAfter(start, buyer, null);
    }

    private TimedCheckout checkoutAfter(CyclicBarrier start, Session buyer, String voucherCode) throws Exception {
        start.await(15, TimeUnit.SECONDS);
        long started = System.nanoTime();
        MvcResult result = performCheckout(buyer, voucherCode);
        return new TimedCheckout(result, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private TimedCheckout awaitTimedCheckout(Future<TimedCheckout> future) throws Exception {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            assertNoLockFailure(-1, String.valueOf(ex));
            throw ex;
        }
    }

    private MvcResult awaitCheckout(Future<MvcResult> future) throws Exception {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            assertNoLockFailure(-1, String.valueOf(ex));
            throw ex;
        }
    }

    private MvcResult performCheckout(Session buyer) throws Exception {
        return performCheckout(buyer, null);
    }

    private MvcResult performCheckout(Session buyer, String voucherCode) throws Exception {
        return mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(buyer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(buyer.addressId, voucherCode, null))))
                .andReturn();
    }

    private static void assertNoLockFailure(int status, String body) {
        String haystack = (body == null ? "" : body).toLowerCase();
        if (haystack.contains("deadlock") || haystack.contains("lock wait timeout") || status == 500) {
            fail("STOP D3: deadlock, lock timeout, or HTTP 500 during concurrency. "
                    + "CheckoutService.checkout / ProductVariantService.updateStock. "
                    + "Do not change production code without approval.");
        }
    }

    private Session customerWithCart(long variantId) throws Exception {
        Session session = session(RoleName.CUSTOMER);
        long addressId = createAddress(session.token);
        addToCart(session.token, variantId, 1);
        return new Session(session.userId, session.token, addressId);
    }

    private List<CartItem> cartItems(long userId) {
        Long cartId = cartRepository.findByUser_Id(userId).orElseThrow().getId();
        return cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cartId);
    }

    private void addToCart(String token, long variantId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(variantId, quantity))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }

    private long createAddress(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressCreateRequest(
                                "Nguyen Van A",
                                "0901234567",
                                "Ha Noi",
                                "Ba Dinh",
                                "Cong Vi",
                                "12 Hang Tre",
                                true))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return read(result).get("id").asLong();
    }

    private Voucher persistUnusedVoucher() {
        Instant now = clock.instant();
        Voucher voucher = new Voucher();
        voucher.setCode("CONC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        voucher.setType(VoucherType.PERCENT);
        voucher.setDiscountValue(new BigDecimal("10.00"));
        voucher.setUsedCount(0);
        voucher.setStartAt(now.minusSeconds(60));
        voucher.setEndAt(now.plusSeconds(3600));
        voucher.setActive(true);
        return voucherRepository.saveAndFlush(voucher);
    }

    private CatalogFixture publishedProduct(int stock) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Conc Cat " + suffix);
        category.setSlug("conc-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("Conc Brand " + suffix);
        brand.setSlug("conc-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);

        Product product = new Product();
        product.setName("Conc Tee " + suffix);
        product.setSlug("conc-tee-" + suffix);
        product.setPrice(new BigDecimal("100000.00"));
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        Size size = new Size();
        size.setName("M-" + suffix);
        size.setDisplayOrder(1);
        size.setActive(true);
        sizeRepository.save(size);
        Color color = new Color();
        color.setName("Navy-" + suffix);
        color.setHexCode("#001F5B");
        color.setActive(true);
        colorRepository.save(color);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setSku("CONC-" + suffix);
        variant.setStockQuantity(stock);
        variant.setActive(true);
        productVariantRepository.saveAndFlush(variant);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl("https://cdn.example/conc-" + suffix + ".jpg");
        image.setIsPrimary(true);
        image.setSortOrder(1);
        productImageRepository.saveAndFlush(image);
        return new CatalogFixture(product.getId(), variant.getId());
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "conc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Conc " + roleName.name());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(roleRepository.findByName(roleName).orElseThrow()));
        User saved = userRepository.saveAndFlush(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String token = read(result).get("accessToken").asText();
        return new Session(saved.getId(), token, 0L);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(body(result));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record TimedCheckout(MvcResult result, long elapsedMs) {
    }

    private record Session(long userId, String token, long addressId) {
    }

    private record CatalogFixture(long productId, long variantId) {
    }
}
