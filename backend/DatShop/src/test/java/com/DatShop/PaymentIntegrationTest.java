package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.order.CheckoutRequest;
import com.DatShop.dto.order.OrderStatusUpdateRequest;
import com.DatShop.dto.payment.PaymentCreateRequest;
import com.DatShop.dto.payment.PaymentStatusUpdateRequest;
import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Color;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Payment;
import com.DatShop.model.PaymentMethod;
import com.DatShop.model.PaymentStatus;
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
import com.DatShop.repository.CategoryRepository;
import com.DatShop.repository.ColorRepository;
import com.DatShop.repository.PaymentRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
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
class PaymentIntegrationTest {

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
    private VoucherRepository voucherRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void customerPaymentRequiresOwner() throws Exception {
        mockMvc.perform(post("/api/orders/ORD-MISSING/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentCreateRequest(PaymentMethod.COD))))
                .andExpect(status().isUnauthorized());
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        Session owner = session(RoleName.CUSTOMER);
        Session other = session(RoleName.CUSTOMER);
        mockMvc.perform(post("/api/orders/ORD-MISSING/payment")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentCreateRequest(PaymentMethod.COD))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/ORD-MISSING/payment").header("Authorization", bearer(admin.token)))
                .andExpect(status().isForbidden());

        PreparedOrder order = checkoutOrder(owner, "100000.00", 5, 1, "PAY10");
        mockMvc.perform(get("/api/orders/" + order.orderNumber + "/payment")
                        .header("Authorization", bearer(owner.token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Payment not found"));
        mockMvc.perform(post("/api/orders/" + order.orderNumber + "/payment")
                        .header("Authorization", bearer(other.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentCreateRequest(PaymentMethod.COD))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + order.orderNumber + "/payment")
                        .header("Authorization", bearer(owner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"COD\",\"amount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: amount"));
    }

    @Test
    void codPaymentIsIdempotentAndBlocksMethodChange() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        PreparedOrder order = checkoutOrder(customer, "150000.00", 6, 2, null);
        JsonNode created = createPayment(customer.token, order.orderNumber, PaymentMethod.COD, 201);
        assertThat(created.get("method").asText()).isEqualTo("COD");
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo(order.total);
        assertThat(created.get("qrContent") == null || created.get("qrContent").isNull()).isTrue();
        assertThat(created.get("paymentReference").asText()).startsWith("PAY-");
        assertThat(paymentRepository.findByOrder_Id(order.orderId)).isPresent();

        JsonNode repeated = createPayment(customer.token, order.orderNumber, PaymentMethod.COD, 200);
        assertThat(repeated.get("paymentReference").asText()).isEqualTo(created.get("paymentReference").asText());
        assertThat(paymentRepository.findByOrder_Id(order.orderId)).isPresent();

        mockMvc.perform(post("/api/orders/" + order.orderNumber + "/payment")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentCreateRequest(PaymentMethod.BANK_QR))))
                .andExpect(status().isConflict());
        assertThat(paymentRepository.findByOrder_Id(order.orderId).orElseThrow().getMethod())
                .isEqualTo(PaymentMethod.COD);
    }

    @Test
    void bankQrIsDemoManualAndBlocksConfirmUntilPaid() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        PreparedOrder order = checkoutOrder(customer, "200000.00", 8, 1, null);
        JsonNode created = createPayment(customer.token, order.orderNumber, PaymentMethod.BANK_QR, 201);
        String qr = created.get("qrContent").asText();
        assertThat(qr).startsWith("DATSHOP;");
        assertThat(qr).contains("ORDER=" + order.orderNumber);
        assertThat(qr).contains("PAYMENT=" + created.get("paymentReference").asText());
        assertThat(qr).contains("AMOUNT=");
        assertThat(qr).doesNotContain("Bearer").doesNotContain("eyJ");
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo(order.total);
        createPayment(customer.token, order.orderNumber, PaymentMethod.BANK_QR, 200);

        mockMvc.perform(patch("/api/admin/orders/" + order.orderNumber + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("BANK_QR payment must be paid before the order can be confirmed"));

        String reference = created.get("paymentReference").asText();
        JsonNode paid = patchPayment(staff.token, reference, PaymentStatus.PAID);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("paidAt").asText()).isNotBlank();
        String paidAt = paid.get("paidAt").asText();
        JsonNode paidAgain = patchPayment(staff.token, reference, PaymentStatus.PAID);
        assertThat(paidAgain.get("paidAt").asText()).isEqualTo(paidAt);

        assertThat(patchOrder(staff.token, order.orderNumber, OrderStatus.CONFIRMED).get("status").asText())
                .isEqualTo("CONFIRMED");
        mockMvc.perform(patch("/api/admin/payments/" + reference + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentStatusUpdateRequest(PaymentStatus.FAILED))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codCanBePaidOnlyAfterDelivered() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        PreparedOrder order = checkoutOrder(customer, "80000.00", 4, 1, null);
        JsonNode payment = createPayment(customer.token, order.orderNumber, PaymentMethod.COD, 201);
        String reference = payment.get("paymentReference").asText();
        patchOrder(staff.token, order.orderNumber, OrderStatus.CONFIRMED);
        patchOrder(staff.token, order.orderNumber, OrderStatus.PROCESSING);
        patchOrder(staff.token, order.orderNumber, OrderStatus.SHIPPING);
        mockMvc.perform(patch("/api/admin/payments/" + reference + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentStatusUpdateRequest(PaymentStatus.PAID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("COD can be marked PAID only after the order is delivered"));
        patchOrder(staff.token, order.orderNumber, OrderStatus.DELIVERED);
        JsonNode pending = getPayment(customer.token, order.orderNumber);
        assertThat(pending.get("status").asText()).isEqualTo("PENDING");
        JsonNode paid = patchPayment(staff.token, reference, PaymentStatus.PAID);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("paidAt").asText()).isNotBlank();
    }

    @Test
    void pendingPaymentCancelsWithOrderAndPaidOrderCannotCancel() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        PreparedOrder pendingOrder = checkoutOrder(customer, "90000.00", 7, 1, "QR10");
        JsonNode qr = createPayment(customer.token, pendingOrder.orderNumber, PaymentMethod.BANK_QR, 201);
        JsonNode cancelled = read(mockMvc.perform(patch("/api/orders/" + pendingOrder.orderNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("totalAmount").decimalValue()).isEqualByComparingTo(pendingOrder.total);
        assertThat(paymentRepository.findByPaymentReference(qr.get("paymentReference").asText())
                .orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(productVariantRepository.findById(pendingOrder.variantId).orElseThrow().getStockQuantity())
                .isEqualTo(7);
        assertThat(voucherRepository.findByCodeIgnoreCase("QR10").orElseThrow().getUsedCount()).isZero();
        JsonNode second = read(mockMvc.perform(patch("/api/orders/" + pendingOrder.orderNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(second.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(productVariantRepository.findById(pendingOrder.variantId).orElseThrow().getStockQuantity())
                .isEqualTo(7);
        assertThat(voucherRepository.findByCodeIgnoreCase("QR10").orElseThrow().getUsedCount()).isZero();
        assertThat(paymentRepository.findByPaymentReference(qr.get("paymentReference").asText())
                .orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        PreparedOrder paidOrder = checkoutOrder(customer, "110000.00", 5, 1, "PAID10");
        JsonNode paidPayment = createPayment(customer.token, paidOrder.orderNumber, PaymentMethod.BANK_QR, 201);
        patchPayment(staff.token, paidPayment.get("paymentReference").asText(), PaymentStatus.PAID);
        mockMvc.perform(patch("/api/orders/" + paidOrder.orderNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Paid order requires refund handling"));
        assertThat(productVariantRepository.findById(paidOrder.variantId).orElseThrow().getStockQuantity()).isEqualTo(4);
        assertThat(voucherRepository.findByCodeIgnoreCase("PAID10").orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(paymentRepository.findByPaymentReference(paidPayment.get("paymentReference").asText())
                .orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void failedPaymentAllowsCancellationAndAdminListFilters() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        mockMvc.perform(get("/api/admin/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/payments").header("Authorization", bearer(customer.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/payments").header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk());

        PreparedOrder order = checkoutOrder(customer, "60000.00", 3, 1, null);
        JsonNode payment = createPayment(customer.token, order.orderNumber, PaymentMethod.BANK_QR, 201);
        String reference = payment.get("paymentReference").asText();
        patchPayment(admin.token, reference, PaymentStatus.FAILED);
        mockMvc.perform(patch("/api/admin/payments/" + reference + "/status")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentStatusUpdateRequest(PaymentStatus.PAID))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/payments/" + reference + "/status")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\",\"paidAt\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: paidAt"));

        JsonNode cancelled = read(mockMvc.perform(patch("/api/orders/" + order.orderNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(paymentRepository.findByPaymentReference(reference).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(productVariantRepository.findById(order.variantId).orElseThrow().getStockQuantity()).isEqualTo(3);

        mockMvc.perform(get("/api/admin/payments?status=FAILED&method=BANK_QR&q=" + reference)
                        .header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentReference").value(reference));
        mockMvc.perform(get("/api/admin/payments?status=NOT_A_STATUS").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/payments?size=101").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());

        PreparedOrder second = checkoutOrder(customer, "50000.00", 4, 1, null);
        createPayment(customer.token, second.orderNumber, PaymentMethod.COD, 201);
        Payment existing = paymentRepository.findByOrder_Id(second.orderId).orElseThrow();
        existing.setPaymentReference(reference);
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(existing))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private JsonNode createPayment(String token, String orderNumber, PaymentMethod method, int status) throws Exception {
        return read(mockMvc.perform(post("/api/orders/" + orderNumber + "/payment")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentCreateRequest(method))))
                .andExpect(status().is(status))
                .andReturn());
    }

    private JsonNode getPayment(String token, String orderNumber) throws Exception {
        return read(mockMvc.perform(get("/api/orders/" + orderNumber + "/payment")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode patchPayment(String token, String reference, PaymentStatus status) throws Exception {
        return read(mockMvc.perform(patch("/api/admin/payments/" + reference + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentStatusUpdateRequest(status))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode patchOrder(String token, String orderNumber, OrderStatus status) throws Exception {
        return read(mockMvc.perform(patch("/api/admin/orders/" + orderNumber + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(status))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private PreparedOrder checkoutOrder(
            Session customer, String price, int stock, int quantity, String voucherCode) throws Exception {
        long addressId = createAddress(customer.token);
        CatalogFixture catalog = publishedProduct(new BigDecimal(price), stock);
        addToCart(customer.token, catalog.variantId, quantity);
        if (voucherCode != null) {
            persistVoucher(voucherCode);
        }
        JsonNode created = read(mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, voucherCode, null))))
                .andExpect(status().isCreated())
                .andReturn());
        return new PreparedOrder(
                created.get("id").asLong(),
                created.get("orderNumber").asText(),
                catalog.variantId,
                created.get("totalAmount").decimalValue());
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

    private void persistVoucher(String code) {
        Instant now = clock.instant();
        Voucher voucher = new Voucher();
        voucher.setCode(code);
        voucher.setType(VoucherType.PERCENT);
        voucher.setDiscountValue(new BigDecimal("10.00"));
        voucher.setUsedCount(0);
        voucher.setStartAt(now.minusSeconds(60));
        voucher.setEndAt(now.plusSeconds(3600));
        voucher.setActive(true);
        voucherRepository.saveAndFlush(voucher);
    }

    private CatalogFixture publishedProduct(BigDecimal price, int stock) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Pay Cat " + suffix);
        category.setSlug("pay-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);
        Brand brand = new Brand();
        brand.setName("Pay Brand " + suffix);
        brand.setSlug("pay-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);
        Product product = new Product();
        product.setName("Pay Tee " + suffix);
        product.setSlug("pay-tee-" + suffix);
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
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setSku("PAY-" + suffix);
        variant.setStockQuantity(stock);
        variant.setActive(true);
        productVariantRepository.saveAndFlush(variant);
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl("https://cdn.example/pay-" + suffix + ".jpg");
        image.setIsPrimary(true);
        image.setSortOrder(1);
        productImageRepository.saveAndFlush(image);
        return new CatalogFixture(variant.getId());
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Pay " + roleName.name());
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

    private record PreparedOrder(long orderId, String orderNumber, long variantId, BigDecimal total) {
    }
}
