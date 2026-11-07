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
import com.DatShop.model.OrderItem;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
class OrderManagementIntegrationTest {

    private static final String PASSWORD = "S3cure-Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

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

    @Test
    void customerCancelRequiresOwnerAndPendingOrConfirmed() throws Exception {
        mockMvc.perform(patch("/api/orders/ORD-MISSING/cancel")).andExpect(status().isUnauthorized());
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        Session owner = session(RoleName.CUSTOMER);
        Session other = session(RoleName.CUSTOMER);
        mockMvc.perform(patch("/api/orders/ORD-MISSING/cancel").header("Authorization", bearer(staff.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/orders/ORD-MISSING/cancel").header("Authorization", bearer(admin.token)))
                .andExpect(status().isForbidden());

        long addressId = createAddress(owner.token);
        CatalogFixture catalog = publishedProduct(new BigDecimal("100000.00"), null, 10);
        addToCart(owner.token, catalog.variantId, 3);
        persistVoucher("CANCEL10", VoucherType.PERCENT, "10.00", null, null, null, true);
        JsonNode created = checkout(owner.token, addressId, "CANCEL10");
        String orderNumber = created.get("orderNumber").asText();
        BigDecimal subtotal = created.get("subtotal").decimalValue();
        BigDecimal total = created.get("totalAmount").decimalValue();
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(7);
        assertThat(voucherRepository.findByCodeIgnoreCase("CANCEL10").orElseThrow().getUsedCount()).isEqualTo(1);

        mockMvc.perform(patch("/api/orders/" + orderNumber + "/cancel").header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(7);

        JsonNode cancelled = cancelCustomer(owner.token, orderNumber);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("subtotal").decimalValue()).isEqualByComparingTo(subtotal);
        assertThat(cancelled.get("totalAmount").decimalValue()).isEqualByComparingTo(total);
        assertThat(cancelled.get("voucher").get("code").asText()).isEqualTo("CANCEL10");
        assertThat(cancelled.get("voucher").get("discountValue").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(voucherRepository.findByCodeIgnoreCase("CANCEL10").orElseThrow().getUsedCount()).isZero();
        Long cartId = cartRepository.findByUser_Id(owner.userId).orElseThrow().getId();
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cartId)).isEmpty();

        JsonNode second = cancelCustomer(owner.token, orderNumber);
        assertThat(second.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(voucherRepository.findByCodeIgnoreCase("CANCEL10").orElseThrow().getUsedCount()).isZero();

        JsonNode history = read(mockMvc.perform(get("/api/orders").header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(history.get("content").get(0).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(history.get("content").get(0).get("totalAmount").decimalValue()).isEqualByComparingTo(total);
    }

    @Test
    void customerCanCancelConfirmedButNotLaterStatuses() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        long addressId = createAddress(customer.token);
        CatalogFixture pending = publishedProduct(new BigDecimal("50000.00"), null, 5);
        addToCart(customer.token, pending.variantId, 1);
        String confirmedNumber = checkout(customer.token, addressId, null).get("orderNumber").asText();
        patchAdminStatus(staff.token, confirmedNumber, OrderStatus.CONFIRMED);
        JsonNode cancelled = cancelCustomer(customer.token, confirmedNumber);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(productVariantRepository.findById(pending.variantId).orElseThrow().getStockQuantity()).isEqualTo(5);

        CatalogFixture later = publishedProduct(new BigDecimal("50000.00"), null, 5);
        addToCart(customer.token, later.variantId, 1);
        String processingNumber = checkout(customer.token, addressId, null).get("orderNumber").asText();
        patchAdminStatus(staff.token, processingNumber, OrderStatus.CONFIRMED);
        patchAdminStatus(staff.token, processingNumber, OrderStatus.PROCESSING);
        mockMvc.perform(patch("/api/orders/" + processingNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order cannot be cancelled"));
        assertThat(productVariantRepository.findById(later.variantId).orElseThrow().getStockQuantity()).isEqualTo(4);

        patchAdminStatus(staff.token, processingNumber, OrderStatus.SHIPPING);
        mockMvc.perform(patch("/api/orders/" + processingNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isBadRequest());
        patchAdminStatus(staff.token, processingNumber, OrderStatus.DELIVERED);
        mockMvc.perform(patch("/api/orders/" + processingNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isBadRequest());
        assertThat(productVariantRepository.findById(later.variantId).orElseThrow().getStockQuantity()).isEqualTo(4);
    }

    @Test
    void multiItemCancelRestoresBothAndUnsafeRestoreRollsBack() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        long addressId = createAddress(customer.token);
        CatalogFixture a = publishedProduct(new BigDecimal("100000.00"), null, 10);
        CatalogFixture b = publishedProduct(new BigDecimal("250000.00"), null, 10);
        addToCart(customer.token, a.variantId, 2);
        addToCart(customer.token, b.variantId, 4);
        persistVoucher("MULTI10", VoucherType.PERCENT, "10.00", null, null, null, true);
        JsonNode created = checkout(customer.token, addressId, "MULTI10");
        String orderNumber = created.get("orderNumber").asText();
        assertThat(productVariantRepository.findById(a.variantId).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(productVariantRepository.findById(b.variantId).orElseThrow().getStockQuantity()).isEqualTo(6);

        ProductVariant inactive = productVariantRepository.findById(a.variantId).orElseThrow();
        inactive.setActive(false);
        productVariantRepository.saveAndFlush(inactive);
        JsonNode cancelled = cancelCustomer(customer.token, orderNumber);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(productVariantRepository.findById(a.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productVariantRepository.findById(b.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(voucherRepository.findByCodeIgnoreCase("MULTI10").orElseThrow().getUsedCount()).isZero();

        CatalogFixture c = publishedProduct(new BigDecimal("80000.00"), null, 9);
        CatalogFixture d = publishedProduct(new BigDecimal("90000.00"), null, 9);
        addToCart(customer.token, c.variantId, 1);
        addToCart(customer.token, d.variantId, 1);
        persistVoucher("UNSAFE10", VoucherType.PERCENT, "10.00", null, null, null, true);
        JsonNode unsafe = checkout(customer.token, addressId, "UNSAFE10");
        OrderItem firstItem = orderItemRepository.findByOrder_IdOrderByIdAsc(unsafe.get("id").asLong()).getFirst();
        firstItem.setVariantId(999_999_999L);
        orderItemRepository.saveAndFlush(firstItem);
        mockMvc.perform(patch("/api/orders/" + unsafe.get("orderNumber").asText() + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order inventory cannot be restored safely"));
        assertThat(orderRepository.findByOrderNumber(unsafe.get("orderNumber").asText()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(productVariantRepository.findById(c.variantId).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(productVariantRepository.findById(d.variantId).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(voucherRepository.findByCodeIgnoreCase("UNSAFE10").orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void voucherUsedCountCannotGoNegative() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        long addressId = createAddress(customer.token);
        CatalogFixture catalog = publishedProduct(new BigDecimal("120000.00"), null, 6);
        addToCart(customer.token, catalog.variantId, 1);
        persistVoucher("ZERO10", VoucherType.PERCENT, "10.00", null, null, null, true);
        JsonNode created = checkout(customer.token, addressId, "ZERO10");
        Voucher voucher = voucherRepository.findByCodeIgnoreCase("ZERO10").orElseThrow();
        voucher.setUsedCount(0);
        voucherRepository.saveAndFlush(voucher);
        mockMvc.perform(patch("/api/orders/" + created.get("orderNumber").asText() + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Voucher usage cannot be restored safely"));
        assertThat(orderRepository.findByOrderNumber(created.get("orderNumber").asText()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(voucherRepository.findByCodeIgnoreCase("ZERO10").orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void adminListAndTransitionsAndStaffCancellation() throws Exception {
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer(customer.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer(admin.token)))
                .andExpect(status().isOk());

        long addressId = createAddress(customer.token);
        CatalogFixture catalog = publishedProduct(new BigDecimal("70000.00"), null, 8);
        addToCart(customer.token, catalog.variantId, 2);
        persistVoucher("STAFF10", VoucherType.PERCENT, "10.00", null, null, null, true);
        JsonNode created = checkout(customer.token, addressId, "STAFF10");
        String orderNumber = created.get("orderNumber").asText();
        int stockAfterCheckout = 6;

        mockMvc.perform(get("/api/admin/orders?status=PENDING&q=" + orderNumber)
                        .header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        mockMvc.perform(get("/api/admin/orders?status=NOT_A_STATUS").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/orders?page=-1").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/orders?size=101").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/orders/" + orderNumber).header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(70000.0));
        mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\",\"totalAmount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: totalAmount"));

        assertThat(patchAdminStatus(staff.token, orderNumber, OrderStatus.CONFIRMED).get("status").asText())
                .isEqualTo("CONFIRMED");
        assertThat(patchAdminStatus(admin.token, orderNumber, OrderStatus.PROCESSING).get("status").asText())
                .isEqualTo("PROCESSING");
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity())
                .isEqualTo(stockAfterCheckout);

        mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid order status transition"));

        JsonNode staffCancelled = patchAdminStatus(staff.token, orderNumber, OrderStatus.CANCELLED);
        assertThat(staffCancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(staffCancelled.get("totalAmount").decimalValue())
                .isEqualByComparingTo(created.get("totalAmount").decimalValue());
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(voucherRepository.findByCodeIgnoreCase("STAFF10").orElseThrow().getUsedCount()).isZero();
        JsonNode alreadyCancelled = patchAdminStatus(staff.token, orderNumber, OrderStatus.CANCELLED);
        assertThat(alreadyCancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(voucherRepository.findByCodeIgnoreCase("STAFF10").orElseThrow().getUsedCount()).isZero();
        mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminForwardPathDoesNotChangeStockAndRejectsInvalidJumps() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        long addressId = createAddress(customer.token);
        CatalogFixture catalog = publishedProduct(new BigDecimal("40000.00"), null, 5);
        addToCart(customer.token, catalog.variantId, 1);
        String orderNumber = checkout(customer.token, addressId, null).get("orderNumber").asText();

        mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.SHIPPING))))
                .andExpect(status().isBadRequest());
        assertThat(patchAdminStatus(staff.token, orderNumber, OrderStatus.CONFIRMED).get("status").asText())
                .isEqualTo("CONFIRMED");
        assertThat(patchAdminStatus(staff.token, orderNumber, OrderStatus.PROCESSING).get("status").asText())
                .isEqualTo("PROCESSING");
        assertThat(patchAdminStatus(staff.token, orderNumber, OrderStatus.SHIPPING).get("status").asText())
                .isEqualTo("SHIPPING");
        mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CANCELLED))))
                .andExpect(status().isBadRequest());
        assertThat(patchAdminStatus(staff.token, orderNumber, OrderStatus.DELIVERED).get("status").asText())
                .isEqualTo("DELIVERED");
        mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.PROCESSING))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CANCELLED))))
                .andExpect(status().isBadRequest());
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(4);
        assertThat(orderRepository.findByOrderNumber(orderNumber).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
    }

    private JsonNode cancelCustomer(String token, String orderNumber) throws Exception {
        return read(mockMvc.perform(patch("/api/orders/" + orderNumber + "/cancel")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode patchAdminStatus(String token, String orderNumber, OrderStatus status) throws Exception {
        return read(mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(status))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode checkout(String token, long addressId, String voucherCode) throws Exception {
        return read(mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, voucherCode, null))))
                .andExpect(status().isCreated())
                .andReturn());
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
                                "Nguyen Van A",
                                "0901234567",
                                "Ha Noi",
                                "Ba Dinh",
                                "Cong Vi",
                                "12 Hang Tre",
                                true))))
                .andExpect(status().isCreated())
                .andReturn());
        return body.get("id").asLong();
    }

    private Voucher persistVoucher(
            String code,
            VoucherType type,
            String discount,
            String minOrder,
            String maxDiscount,
            Integer usageLimit,
            boolean active) {
        Instant now = clock.instant();
        Voucher voucher = new Voucher();
        voucher.setCode(code);
        voucher.setType(type);
        voucher.setDiscountValue(new BigDecimal(discount));
        voucher.setMinOrderAmount(minOrder == null ? null : new BigDecimal(minOrder));
        voucher.setMaxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        voucher.setUsageLimit(usageLimit);
        voucher.setUsedCount(0);
        voucher.setStartAt(now.minusSeconds(60));
        voucher.setEndAt(now.plusSeconds(3600));
        voucher.setActive(true);
        voucher.setActive(active);
        return voucherRepository.saveAndFlush(voucher);
    }

    private CatalogFixture publishedProduct(BigDecimal price, BigDecimal salePrice, int stock) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Mgmt Cat " + suffix);
        category.setSlug("mgmt-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);
        Brand brand = new Brand();
        brand.setName("Mgmt Brand " + suffix);
        brand.setSlug("mgmt-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);
        Product product = new Product();
        product.setName("Mgmt Tee " + suffix);
        product.setSlug("mgmt-tee-" + suffix);
        product.setPrice(price);
        product.setSalePrice(salePrice);
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
        variant.setSku("MGT-" + suffix);
        variant.setStockQuantity(stock);
        variant.setActive(true);
        productVariantRepository.saveAndFlush(variant);
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl("https://cdn.example/mgmt-" + suffix + ".jpg");
        image.setIsPrimary(true);
        image.setSortOrder(1);
        productImageRepository.saveAndFlush(image);
        return new CatalogFixture(variant.getId());
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "mgmt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Mgmt " + roleName.name());
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

    private record CatalogFixture(long variantId) {
    }
}
