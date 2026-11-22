package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.account.AddressUpdateRequest;
import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.order.CheckoutRequest;
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
class CheckoutIntegrationTest {

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
    void checkoutAndOrdersRequireCustomer() throws Exception {
        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(1L, null, null))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/ORD-MISSING")).andExpect(status().isUnauthorized());

        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        Session customer = session(RoleName.CUSTOMER);
        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(1L, null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(1L, null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders").header("Authorization", bearer(staff.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders").header("Authorization", bearer(admin.token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(1L, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cart is empty"));
        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":1,\"subtotal\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: subtotal"));
        mockMvc.perform(get("/api/orders").header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/orders/ORD-DOES-NOT-EXIST").header("Authorization", bearer(customer.token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void successfulCheckoutWithoutVoucherSnapshotsAndClearsCart() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session other = session(RoleName.CUSTOMER);
        long addressId = createAddress(customer.token);
        long foreignAddressId = createAddress(other.token);
        CatalogFixture a = publishedProduct(new BigDecimal("100000.00"), null, 10);
        addToCart(customer.token, a.variantId, 3);

        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(foreignAddressId, null, null))))
                .andExpect(status().isNotFound());
        assertThat(productVariantRepository.findById(a.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(orderRepository.countByUser_Id(customer.userId)).isZero();

        JsonNode created = read(mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(addressId, null, "Please call"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/orders/ORD-")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.shippingFee").value(0.0))
                .andExpect(jsonPath("$.voucher").value(org.hamcrest.Matchers.nullValue()))
                .andReturn());
        assertThat(created.get("orderNumber").asText()).startsWith("ORD-");
        assertThat(created.get("subtotal").decimalValue()).isEqualByComparingTo("300000.00");
        assertThat(created.get("discountAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("300000.00");
        assertThat(created.get("items")).hasSize(1);
        assertThat(created.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(created.get("items").get(0).get("productName").asText()).isEqualTo(a.productName);
        assertThat(created.get("items").get(0).get("sku").asText()).isEqualTo(a.sku);
        assertThat(created.get("items").get(0).get("productImageUrl").asText()).isEqualTo(a.imageUrl);
        assertThat(created.get("shippingAddress").get("recipientName").asText()).isEqualTo("Nguyen Van A");
        assertThat(created.get("customerNote").asText()).isEqualTo("Please call");

        assertThat(productVariantRepository.findById(a.variantId).orElseThrow().getStockQuantity()).isEqualTo(7);
        Long cartId = cartRepository.findByUser_Id(customer.userId).orElseThrow().getId();
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cartId)).isEmpty();
        assertThat(orderItemRepository.findByOrder_IdOrderByIdAsc(created.get("id").asLong())).hasSize(1);
    }

    @Test
    void exactStockSucceedsAndInsufficientStockRollsBack() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        long addressId = createAddress(customer.token);
        CatalogFixture exact = publishedProduct(new BigDecimal("20000.00"), null, 3);
        addToCart(customer.token, exact.variantId, 3);
        JsonNode created = checkout(customer.token, addressId, null);
        assertThat(created.get("status").asText()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(productVariantRepository.findById(exact.variantId).orElseThrow().getStockQuantity()).isZero();

        CatalogFixture shortStock = publishedProduct(new BigDecimal("20000.00"), null, 5);
        addToCart(customer.token, shortStock.variantId, 3);
        ProductVariant limited = productVariantRepository.findById(shortStock.variantId).orElseThrow();
        limited.setStockQuantity(2);
        productVariantRepository.saveAndFlush(limited);
        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Requested quantity exceeds available stock"));
        assertThat(productVariantRepository.findById(shortStock.variantId).orElseThrow().getStockQuantity()).isEqualTo(2);
        Long cartId = cartRepository.findByUser_Id(customer.userId).orElseThrow().getId();
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cartId)).hasSize(1);
        assertThat(orderRepository.countByUser_Id(customer.userId)).isEqualTo(1);
    }

    @Test
    void multiItemStockFailureRollsBackCompletely() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        long addressId = createAddress(customer.token);
        CatalogFixture enough = publishedProduct(new BigDecimal("100000.00"), null, 10);
        CatalogFixture notEnough = publishedProduct(new BigDecimal("250000.00"), null, 5);
        addToCart(customer.token, enough.variantId, 2);
        addToCart(customer.token, notEnough.variantId, 3);
        ProductVariant reduced = productVariantRepository.findById(notEnough.variantId).orElseThrow();
        reduced.setStockQuantity(1);
        productVariantRepository.saveAndFlush(reduced);
        Voucher voucher = persistVoucher("ROLLBACK10", VoucherType.PERCENT, "10.00", null, null, 5, true);

        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest(addressId, "ROLLBACK10", null))))
                .andExpect(status().isBadRequest());

        assertThat(orderRepository.countByUser_Id(customer.userId)).isZero();
        assertThat(productVariantRepository.findById(enough.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(productVariantRepository.findById(notEnough.variantId).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getUsedCount()).isZero();
        Long cartId = cartRepository.findByUser_Id(customer.userId).orElseThrow().getId();
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cartId)).hasSize(2);
    }

    @Test
    void voucherDiscountAndFailureRollbacks() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        long addressId = createAddress(customer.token);
        CatalogFixture a = publishedProduct(new BigDecimal("100000.00"), null, 10);
        CatalogFixture b = publishedProduct(new BigDecimal("250000.00"), null, 10);
        addToCart(customer.token, a.variantId, 2);
        addToCart(customer.token, b.variantId, 3);
        persistVoucher("SALE10", VoucherType.PERCENT, "10.00", null, null, null, true);

        JsonNode percent = checkout(customer.token, addressId, "SALE10");
        assertThat(percent.get("subtotal").decimalValue()).isEqualByComparingTo("950000.00");
        assertThat(percent.get("discountAmount").decimalValue()).isEqualByComparingTo("95000.00");
        assertThat(percent.get("shippingFee").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(percent.get("totalAmount").decimalValue()).isEqualByComparingTo("855000.00");
        assertThat(percent.get("voucher").get("code").asText()).isEqualTo("SALE10");
        assertThat(voucherRepository.findByCodeIgnoreCase("SALE10").orElseThrow().getUsedCount()).isEqualTo(1);

        CatalogFixture capProduct = publishedProduct(new BigDecimal("2000000.00"), null, 5);
        addToCart(customer.token, capProduct.variantId, 1);
        persistVoucher("CAP10", VoucherType.PERCENT, "10.00", null, "150000.00", null, true);
        JsonNode capped = checkout(customer.token, addressId, "CAP10");
        assertThat(capped.get("discountAmount").decimalValue()).isEqualByComparingTo("150000.00");
        assertThat(capped.get("totalAmount").decimalValue()).isEqualByComparingTo("1850000.00");

        CatalogFixture fixedProduct = publishedProduct(new BigDecimal("300000.00"), null, 5);
        addToCart(customer.token, fixedProduct.variantId, 1);
        persistVoucher("FIX50K", VoucherType.FIXED, "50000.00", null, null, null, true);
        JsonNode fixed = checkout(customer.token, addressId, "FIX50K");
        assertThat(fixed.get("discountAmount").decimalValue()).isEqualByComparingTo("50000.00");
        assertThat(fixed.get("totalAmount").decimalValue()).isEqualByComparingTo("250000.00");

        CatalogFixture remaining = publishedProduct(new BigDecimal("100000.00"), null, 8);
        addToCart(customer.token, remaining.variantId, 1);
        persistVoucher("EXPIRED", VoucherType.PERCENT, "10.00", null, null, null, true);
        Voucher expired = voucherRepository.findByCodeIgnoreCase("EXPIRED").orElseThrow();
        expired.setStartAt(clock.instant().minusSeconds(7200));
        expired.setEndAt(clock.instant().minusSeconds(60));
        voucherRepository.saveAndFlush(expired);

        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, "EXPIRED", null))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, "NOPE", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Voucher is invalid"));
        assertThat(productVariantRepository.findById(remaining.variantId).orElseThrow().getStockQuantity()).isEqualTo(8);
        Long cartId = cartRepository.findByUser_Id(customer.userId).orElseThrow().getId();
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cartId)).hasSize(1);
        assertThat(voucherRepository.findByCodeIgnoreCase("EXPIRED").orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void voucherUsageIsConsumedOnceAndExhaustedRollsBack() throws Exception {
        Session first = session(RoleName.CUSTOMER);
        Session second = session(RoleName.CUSTOMER);
        long firstAddress = createAddress(first.token);
        long secondAddress = createAddress(second.token);
        CatalogFixture product = publishedProduct(new BigDecimal("200000.00"), null, 10);
        persistVoucher("ONCE", VoucherType.PERCENT, "10.00", null, null, 1, true);
        addToCart(first.token, product.variantId, 1);
        checkout(first.token, firstAddress, "ONCE");
        assertThat(voucherRepository.findByCodeIgnoreCase("ONCE").orElseThrow().getUsedCount()).isEqualTo(1);

        addToCart(second.token, product.variantId, 1);
        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(second.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(secondAddress, "ONCE", null))))
                .andExpect(status().isBadRequest());
        assertThat(orderRepository.countByUser_Id(second.userId)).isZero();
        assertThat(productVariantRepository.findById(product.variantId).orElseThrow().getStockQuantity()).isEqualTo(9);
        assertThat(voucherRepository.findByCodeIgnoreCase("ONCE").orElseThrow().getUsedCount()).isEqualTo(1);
        Long secondCart = cartRepository.findByUser_Id(second.userId).orElseThrow().getId();
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(secondCart)).hasSize(1);
    }

    @Test
    void staleCatalogAndInactiveSizeColorFailAtomically() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        long addressId = createAddress(customer.token);

        CatalogFixture variantOff = publishedProduct(new BigDecimal("10000.00"), null, 5);
        addToCart(customer.token, variantOff.variantId, 1);
        ProductVariant variant = productVariantRepository.findById(variantOff.variantId).orElseThrow();
        variant.setActive(false);
        productVariantRepository.saveAndFlush(variant);
        assertCheckoutUnchanged(customer, addressId, variantOff.variantId, 5);

        CatalogFixture productOff = publishedProduct(new BigDecimal("10000.00"), null, 5);
        addToCart(customer.token, productOff.variantId, 1);
        Product product = productRepository.findById(productOff.productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        assertCheckoutUnchanged(customer, addressId, productOff.variantId, 5);

        CatalogFixture categoryOff = publishedProduct(new BigDecimal("10000.00"), null, 5);
        addToCart(customer.token, categoryOff.variantId, 1);
        Product categoryProduct = productRepository.findById(categoryOff.productId).orElseThrow();
        categoryProduct.getCategory().setStatus(CatalogStatus.INACTIVE);
        categoryRepository.saveAndFlush(categoryProduct.getCategory());
        assertCheckoutUnchanged(customer, addressId, categoryOff.variantId, 5);

        CatalogFixture brandOff = publishedProduct(new BigDecimal("10000.00"), null, 5);
        addToCart(customer.token, brandOff.variantId, 1);
        Product brandProduct = productRepository.findById(brandOff.productId).orElseThrow();
        brandProduct.getBrand().setStatus(CatalogStatus.INACTIVE);
        brandRepository.saveAndFlush(brandProduct.getBrand());
        assertCheckoutUnchanged(customer, addressId, brandOff.variantId, 5);

        CatalogFixture sizeOff = publishedProduct(new BigDecimal("10000.00"), null, 5);
        addToCart(customer.token, sizeOff.variantId, 1);
        productVariantRepository.findById(sizeOff.variantId).orElseThrow().getSize().setActive(false);
        sizeRepository.saveAndFlush(productVariantRepository.findById(sizeOff.variantId).orElseThrow().getSize());
        assertCheckoutUnchanged(customer, addressId, sizeOff.variantId, 5);

        CatalogFixture colorOff = publishedProduct(new BigDecimal("10000.00"), null, 5);
        addToCart(customer.token, colorOff.variantId, 1);
        productVariantRepository.findById(colorOff.variantId).orElseThrow().getColor().setActive(false);
        colorRepository.saveAndFlush(productVariantRepository.findById(colorOff.variantId).orElseThrow().getColor());
        assertCheckoutUnchanged(customer, addressId, colorOff.variantId, 5);
    }

    @Test
    void snapshotsSurviveCatalogAddressAndVoucherChanges() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session other = session(RoleName.CUSTOMER);
        long addressId = createAddress(customer.token);
        CatalogFixture catalog = publishedProduct(new BigDecimal("500000.00"), null, 10);
        addToCart(customer.token, catalog.variantId, 1);
        Product priced = productRepository.findById(catalog.productId).orElseThrow();
        priced.setSalePrice(new BigDecimal("400000.00"));
        productRepository.saveAndFlush(priced);
        persistVoucher("SNAP10", VoucherType.PERCENT, "10.00", null, null, null, true);

        JsonNode created = checkout(customer.token, addressId, "SNAP10");
        String orderNumber = created.get("orderNumber").asText();
        assertThat(created.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("400000.00");
        assertThat(created.get("discountAmount").decimalValue()).isEqualByComparingTo("40000.00");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("360000.00");

        Product mutated = productRepository.findById(catalog.productId).orElseThrow();
        mutated.setName("Changed Name");
        mutated.setPrice(new BigDecimal("999999.00"));
        mutated.setSalePrice(new BigDecimal("1.00"));
        productRepository.saveAndFlush(mutated);
        Size size = productVariantRepository.findById(catalog.variantId).orElseThrow().getSize();
        size.setName("XXL-changed");
        sizeRepository.saveAndFlush(size);
        Color color = productVariantRepository.findById(catalog.variantId).orElseThrow().getColor();
        color.setName("Changed-Color");
        colorRepository.saveAndFlush(color);
        mockMvc.perform(put("/api/addresses/" + addressId)
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressUpdateRequest(
                                "New Name", "0987654321", "HCM", "1", "Ben Nghe", "1 Dong Khoi", true))))
                .andExpect(status().isOk());
        Voucher voucher = voucherRepository.findByCodeIgnoreCase("SNAP10").orElseThrow();
        voucher.setDiscountValue(new BigDecimal("50.00"));
        voucherRepository.saveAndFlush(voucher);

        JsonNode detail = read(mockMvc.perform(get("/api/orders/" + orderNumber)
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.get("items").get(0).get("productName").asText()).isEqualTo(catalog.productName);
        assertThat(detail.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("400000.00");
        assertThat(detail.get("items").get(0).get("sizeName").asText()).isEqualTo(catalog.sizeName);
        assertThat(detail.get("items").get(0).get("colorName").asText()).isEqualTo(catalog.colorName);
        assertThat(detail.get("items").get(0).get("productImageUrl").asText()).isEqualTo(catalog.imageUrl);
        assertThat(detail.get("shippingAddress").get("recipientName").asText()).isEqualTo("Nguyen Van A");
        assertThat(detail.get("shippingAddress").get("province").asText()).isEqualTo("Ha Noi");
        assertThat(detail.get("voucher").get("discountValue").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(detail.get("discountAmount").decimalValue()).isEqualByComparingTo("40000.00");

        mockMvc.perform(get("/api/orders/" + orderNumber).header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());

        CatalogFixture second = publishedProduct(new BigDecimal("10000.00"), null, 5);
        addToCart(customer.token, second.variantId, 1);
        checkout(customer.token, addressId, null);
        JsonNode page = read(mockMvc.perform(get("/api/orders?page=0&size=1")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn());
        assertThat(page.get("content")).hasSize(1);
        mockMvc.perform(get("/api/orders").header("Authorization", bearer(other.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    private void assertCheckoutUnchanged(Session customer, long addressId, long variantId, int stock) throws Exception {
        long ordersBefore = orderRepository.countByUser_Id(customer.userId);
        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This product is not available"));
        assertThat(orderRepository.countByUser_Id(customer.userId)).isEqualTo(ordersBefore);
        assertThat(productVariantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(stock);
        Long cartId = cartRepository.findByUser_Id(customer.userId).orElseThrow().getId();
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cartId)).isNotEmpty();
        cartItemRepository.deleteAll(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cartId));
        cartItemRepository.flush();
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
        voucher.setActive(active);
        return voucherRepository.saveAndFlush(voucher);
    }

    private CatalogFixture publishedProduct(BigDecimal price, BigDecimal salePrice, int stock) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Order Cat " + suffix);
        category.setSlug("order-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("Order Brand " + suffix);
        brand.setSlug("order-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);

        Product product = new Product();
        String productName = "Order Tee " + suffix;
        product.setName(productName);
        product.setSlug("order-tee-" + suffix);
        product.setPrice(price);
        product.setSalePrice(salePrice);
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        Size size = new Size();
        String sizeName = "M-" + suffix;
        size.setName(sizeName);
        size.setDisplayOrder(1);
        size.setActive(true);
        sizeRepository.save(size);
        Color color = new Color();
        String colorName = "Navy-" + suffix;
        color.setName(colorName);
        color.setHexCode("#001F5B");
        color.setActive(true);
        colorRepository.save(color);

        ProductVariant variant = new ProductVariant();
        String sku = "ORD-" + suffix;
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setSku(sku);
        variant.setStockQuantity(stock);
        variant.setActive(true);
        productVariantRepository.saveAndFlush(variant);

        String imageUrl = "https://cdn.example/order-" + suffix + ".jpg";
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(imageUrl);
        image.setIsPrimary(true);
        image.setSortOrder(1);
        productImageRepository.saveAndFlush(image);
        return new CatalogFixture(product.getId(), variant.getId(), sku, productName, imageUrl, sizeName, colorName);
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Order " + roleName.name());
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

    private record CatalogFixture(
            long productId,
            long variantId,
            String sku,
            String productName,
            String imageUrl,
            String sizeName,
            String colorName) {
    }
}
