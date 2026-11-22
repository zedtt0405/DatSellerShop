package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.order.CheckoutRequest;
import com.DatShop.dto.order.OrderStatusUpdateRequest;
import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Color;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Product;
import com.DatShop.model.ProductImage;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.RoleName;
import com.DatShop.model.Size;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.repository.BrandRepository;
import com.DatShop.repository.CategoryRepository;
import com.DatShop.repository.ColorRepository;
import com.DatShop.repository.OrderRepository;
import com.DatShop.repository.PaymentRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductReviewRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.SizeRepository;
import com.DatShop.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTest {

    private static final String PASSWORD = "S3cure-Passw0rd";
    private static final LocalDate RANGE_DAY = LocalDate.of(2099, 7, 10);
    private static final LocalDate CHART_DAY1 = LocalDate.of(2099, 8, 1);
    private static final LocalDate CHART_DAY2 = LocalDate.of(2099, 8, 2);
    private static final LocalDate CHART_DAY3 = LocalDate.of(2099, 8, 3);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

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
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Test
    void dashboardRequiresStaffOrAdmin() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        mockMvc.perform(get("/api/admin/dashboard/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/dashboard/revenue-chart")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/dashboard/best-selling-products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/dashboard/summary").header("Authorization", bearer(customer.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard/revenue-chart").header("Authorization", bearer(customer.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard/best-selling-products").header("Authorization", bearer(customer.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard/summary").header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/dashboard/revenue-chart").header("Authorization", bearer(admin.token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/dashboard/best-selling-products").header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk());
    }

    @Test
    void summaryCountsRevenueEligibleOrdersAndAllTimeDeltas() throws Exception {
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        JsonNode before = summary(staff.token, null, null);
        Session customerA = session(RoleName.CUSTOMER);
        session(RoleName.CUSTOMER);

        CatalogFixture a = publishedProduct(new BigDecimal("100.00"), 20);
        CatalogFixture b = publishedProduct(new BigDecimal("100.00"), 20);
        CatalogFixture c = publishedProduct(new BigDecimal("100.00"), 20);
        PreparedOrder pending = checkout(customerA, a, 1);
        PreparedOrder confirmed = checkout(customerA, a, 2);
        PreparedOrder processing = checkout(customerA, b, 3);
        PreparedOrder shipping = checkout(customerA, b, 4);
        PreparedOrder delivered = checkout(customerA, c, 5);
        PreparedOrder cancelled = checkout(customerA, c, 6);
        patchOrder(staff.token, confirmed.orderNumber, OrderStatus.CONFIRMED);
        patchOrder(staff.token, processing.orderNumber, OrderStatus.CONFIRMED);
        patchOrder(staff.token, processing.orderNumber, OrderStatus.PROCESSING);
        patchOrder(staff.token, shipping.orderNumber, OrderStatus.CONFIRMED);
        patchOrder(staff.token, shipping.orderNumber, OrderStatus.PROCESSING);
        patchOrder(staff.token, shipping.orderNumber, OrderStatus.SHIPPING);
        deliver(staff.token, delivered.orderNumber);
        mockMvc.perform(patch("/api/orders/" + cancelled.orderNumber + "/cancel")
                        .header("Authorization", bearer(customerA.token)))
                .andExpect(status().isOk());
        for (PreparedOrder order : java.util.List.of(pending, confirmed, processing, shipping, delivered, cancelled)) {
            setCreatedAt(order.orderId, RANGE_DAY);
        }

        JsonNode ranged = summary(admin.token, RANGE_DAY, RANGE_DAY);
        assertThat(ranged.get("orderCount").asLong()).isEqualTo(6);
        assertThat(ranged.get("revenue").decimalValue()).isEqualByComparingTo("1400.00");
        assertThat(ranged.get("pendingOrderCount").asLong()).isEqualTo(1);
        assertThat(ranged.get("deliveredOrderCount").asLong()).isEqualTo(1);

        JsonNode after = summary(staff.token, null, null);
        assertThat(after.get("customerCount").asLong()).isEqualTo(before.get("customerCount").asLong() + 2);
        assertThat(after.get("productCount").asLong()).isEqualTo(before.get("productCount").asLong() + 3);
        assertThat(after.get("orderCount").asLong()).isEqualTo(before.get("orderCount").asLong() + 6);
        assertThat(after.get("revenue").decimalValue())
                .isEqualByComparingTo(before.get("revenue").decimalValue().add(new BigDecimal("1400.00")));
        assertThat(admin.userId).isNotEqualTo(customerA.userId);
    }

    @Test
    void cancellingEligibleOrderRemovesItFromRevenueAndBestSellers() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        CatalogFixture catalog = publishedProduct(new BigDecimal("250.00"), 5);
        PreparedOrder order = checkout(customer, catalog, 2);
        patchOrder(staff.token, order.orderNumber, OrderStatus.CONFIRMED);
        setCreatedAt(order.orderId, LocalDate.of(2099, 9, 1));

        JsonNode before = summary(staff.token, LocalDate.of(2099, 9, 1), LocalDate.of(2099, 9, 1));
        assertThat(before.get("revenue").decimalValue()).isEqualByComparingTo("500.00");
        JsonNode sellersBefore = bestSellers(staff.token, LocalDate.of(2099, 9, 1), LocalDate.of(2099, 9, 1), 10);
        assertThat(sellersBefore).hasSize(1);
        assertThat(sellersBefore.get(0).get("quantitySold").asLong()).isEqualTo(2);

        mockMvc.perform(patch("/api/orders/" + order.orderNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk());

        JsonNode after = summary(staff.token, LocalDate.of(2099, 9, 1), LocalDate.of(2099, 9, 1));
        assertThat(after.get("revenue").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(after.get("orderCount").asLong()).isEqualTo(1);
        JsonNode sellersAfter = bestSellers(staff.token, LocalDate.of(2099, 9, 1), LocalDate.of(2099, 9, 1), 10);
        assertThat(sellersAfter).isEmpty();
    }

    @Test
    void bestSellersGroupVariantsAndKeepSnapshotRevenue() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        TwoVariantProduct productA = publishedProductWithTwoVariants(new BigDecimal("10.00"), 10);
        CatalogFixture productB = publishedProduct(new BigDecimal("20.00"), 10);
        addToCart(customer.token, productA.variantId1, 2);
        addToCart(customer.token, productA.variantId2, 3);
        long addressId = createAddress(customer.token);
        JsonNode orderA = read(mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, null, null))))
                .andExpect(status().isCreated())
                .andReturn());
        PreparedOrder orderB = checkout(customer, productB, 4);
        patchOrder(staff.token, orderA.get("orderNumber").asText(), OrderStatus.CONFIRMED);
        patchOrder(staff.token, orderB.orderNumber, OrderStatus.CONFIRMED);
        LocalDate day = LocalDate.of(2099, 10, 5);
        setCreatedAt(orderA.get("id").asLong(), day);
        setCreatedAt(orderB.orderId, day);

        Product current = productRepository.findById(productA.productId).orElseThrow();
        current.setPrice(new BigDecimal("9999.00"));
        productRepository.saveAndFlush(current);

        JsonNode sellers = bestSellers(staff.token, day, day, 10);
        assertThat(sellers).hasSize(2);
        assertThat(sellers.get(0).get("productId").asLong()).isEqualTo(productA.productId);
        assertThat(sellers.get(0).get("quantitySold").asLong()).isEqualTo(5);
        assertThat(sellers.get(0).get("revenue").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(sellers.get(0).get("productName").asText()).isEqualTo(productA.name);
        assertThat(sellers.get(1).get("productId").asLong()).isEqualTo(productB.productId);
        assertThat(sellers.get(1).get("quantitySold").asLong()).isEqualTo(4);
        assertThat(sellers.get(1).get("revenue").decimalValue()).isEqualByComparingTo("80.00");

        PreparedOrder pending = checkout(customer, productB, 1);
        setCreatedAt(pending.orderId, day);
        JsonNode afterPending = bestSellers(staff.token, day, day, 10);
        assertThat(afterPending.get(1).get("quantitySold").asLong()).isEqualTo(4);
    }

    @Test
    void revenueChartZeroFillsAndIgnoresPendingCancelled() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        CatalogFixture catalog = publishedProduct(new BigDecimal("100.00"), 20);
        PreparedOrder first = checkout(customer, catalog, 1);
        PreparedOrder second = checkout(customer, catalog, 2);
        PreparedOrder third = checkout(customer, catalog, 3);
        PreparedOrder pending = checkout(customer, catalog, 1);
        PreparedOrder cancelled = checkout(customer, catalog, 2);
        patchOrder(staff.token, first.orderNumber, OrderStatus.CONFIRMED);
        patchOrder(staff.token, second.orderNumber, OrderStatus.CONFIRMED);
        deliver(staff.token, third.orderNumber);
        mockMvc.perform(patch("/api/orders/" + cancelled.orderNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk());
        setCreatedAt(first.orderId, CHART_DAY1);
        setCreatedAt(second.orderId, CHART_DAY1);
        setCreatedAt(third.orderId, CHART_DAY3);
        setCreatedAt(pending.orderId, CHART_DAY3);
        setCreatedAt(cancelled.orderId, CHART_DAY3);

        JsonNode chart = read(mockMvc.perform(get("/api/admin/dashboard/revenue-chart")
                        .header("Authorization", bearer(staff.token))
                        .param("from", CHART_DAY1.toString())
                        .param("to", CHART_DAY3.toString()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(chart).hasSize(3);
        assertThat(chart.get(0).get("date").asText()).isEqualTo(CHART_DAY1.toString());
        assertThat(chart.get(0).get("revenue").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(chart.get(0).get("orderCount").asLong()).isEqualTo(2);
        assertThat(chart.get(1).get("date").asText()).isEqualTo(CHART_DAY2.toString());
        assertThat(chart.get(1).get("revenue").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(chart.get(1).get("orderCount").asLong()).isZero();
        assertThat(chart.get(2).get("date").asText()).isEqualTo(CHART_DAY3.toString());
        assertThat(chart.get(2).get("revenue").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(chart.get(2).get("orderCount").asLong()).isEqualTo(1);

        int stock = productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity();
        long payments = paymentRepository.count();
        long reviews = productReviewRepository.count();
        mockMvc.perform(get("/api/admin/dashboard/summary").header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk());
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(stock);
        assertThat(paymentRepository.count()).isEqualTo(payments);
        assertThat(productReviewRepository.count()).isEqualTo(reviews);
        assertThat(orderRepository.findById(first.orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void dashboardValidatesRangeAndLimit() throws Exception {
        Session staff = session(RoleName.STAFF);
        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", bearer(staff.token))
                        .param("from", "2026-12-31")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/dashboard/revenue-chart")
                        .header("Authorization", bearer(staff.token))
                        .param("from", "2020-01-01")
                        .param("to", "2022-01-02"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/dashboard/best-selling-products")
                        .header("Authorization", bearer(staff.token))
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/dashboard/best-selling-products")
                        .header("Authorization", bearer(staff.token))
                        .param("limit", "51"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode summary(String token, LocalDate from, LocalDate to) throws Exception {
        var request = get("/api/admin/dashboard/summary").header("Authorization", bearer(token));
        if (from != null) {
            request = request.param("from", from.toString());
        }
        if (to != null) {
            request = request.param("to", to.toString());
        }
        return read(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private JsonNode bestSellers(String token, LocalDate from, LocalDate to, int limit) throws Exception {
        return read(mockMvc.perform(get("/api/admin/dashboard/best-selling-products")
                        .header("Authorization", bearer(token))
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void setCreatedAt(long orderId, LocalDate date) {
        LocalDateTime at = date.atTime(12, 0);
        entityManager.createNativeQuery("UPDATE orders SET created_at = :at WHERE id = :id")
                .setParameter("at", at)
                .setParameter("id", orderId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private void deliver(String staffToken, String orderNumber) throws Exception {
        patchOrder(staffToken, orderNumber, OrderStatus.CONFIRMED);
        patchOrder(staffToken, orderNumber, OrderStatus.PROCESSING);
        patchOrder(staffToken, orderNumber, OrderStatus.SHIPPING);
        patchOrder(staffToken, orderNumber, OrderStatus.DELIVERED);
    }

    private JsonNode patchOrder(String token, String orderNumber, OrderStatus status) throws Exception {
        return read(mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(status))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private PreparedOrder checkout(Session customer, CatalogFixture catalog, int quantity) throws Exception {
        long addressId = createAddress(customer.token);
        addToCart(customer.token, catalog.variantId, quantity);
        JsonNode created = read(mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, null, null))))
                .andExpect(status().isCreated())
                .andReturn());
        return new PreparedOrder(
                created.get("id").asLong(),
                created.get("orderNumber").asText(),
                catalog.productId,
                catalog.variantId);
    }

    private void addToCart(String token, long variantId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(variantId, quantity))))
                .andExpect(status().isOk());
    }

    private long createAddress(String token) throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressCreateRequest(
                                "Nguyen Van A", "0901234567", "Ha Noi", "Ba Dinh", "Cong Vi", "12 Hang Tre", true))))
                .andExpect(status().isCreated())
                .andReturn());
        return body.get("id").asLong();
    }

    private CatalogFixture publishedProduct(BigDecimal price, int stock) {
        return publishedProduct(price, stock, "Dash Tee");
    }

    private CatalogFixture publishedProduct(BigDecimal price, int stock, String namePrefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Dash Cat " + suffix);
        category.setSlug("dash-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);
        Brand brand = new Brand();
        brand.setName("Dash Brand " + suffix);
        brand.setSlug("dash-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);
        Product product = new Product();
        product.setName(namePrefix + " " + suffix);
        product.setSlug("dash-tee-" + suffix);
        product.setPrice(price);
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
        ProductVariant variant = variant(product, size, color, "DASH-" + suffix, stock);
        productVariantRepository.saveAndFlush(variant);
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl("https://cdn.example/dash-" + suffix + ".jpg");
        image.setIsPrimary(true);
        image.setSortOrder(1);
        productImageRepository.saveAndFlush(image);
        return new CatalogFixture(product.getId(), variant.getId(), product.getName(), product.getSlug());
    }

    private TwoVariantProduct publishedProductWithTwoVariants(BigDecimal price, int stock) {
        CatalogFixture first = publishedProduct(price, stock, "Dash Dual");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Size size = new Size();
        size.setName("L-" + suffix);
        size.setDisplayOrder(2);
        size.setActive(true);
        sizeRepository.save(size);
        Color color = new Color();
        color.setName("Red-" + suffix);
        color.setHexCode("#FF0000");
        color.setActive(true);
        colorRepository.save(color);
        Product product = productRepository.findById(first.productId).orElseThrow();
        ProductVariant second = variant(product, size, color, "DASH2-" + suffix, stock);
        productVariantRepository.saveAndFlush(second);
        return new TwoVariantProduct(first.productId, first.variantId, second.getId(), first.name);
    }

    private ProductVariant variant(Product product, Size size, Color color, String sku, int stock) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setSku(sku);
        variant.setStockQuantity(stock);
        variant.setActive(true);
        return variant;
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "dash_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Dash " + roleName.name());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(roleRepository.findByName(roleName).orElseThrow()));
        User saved = userRepository.save(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        return new Session(saved.getId(), token);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(long userId, String token) {
    }

    private record CatalogFixture(long productId, long variantId, String name, String slug) {
    }

    private record TwoVariantProduct(long productId, long variantId1, long variantId2, String name) {
    }

    private record PreparedOrder(long orderId, String orderNumber, long productId, long variantId) {
    }
}
