package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.voucher.VoucherCreateRequest;
import com.DatShop.dto.voucher.VoucherUpdateRequest;
import com.DatShop.dto.voucher.VoucherValidationRequest;
import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Color;
import com.DatShop.model.Product;
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
class VoucherIntegrationTest {

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
    private VoucherRepository voucherRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private SizeRepository sizeRepository;

    @Autowired
    private ColorRepository colorRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void validateRequiresCustomerAndAdminRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/vouchers/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherValidationRequest("WELCOME10"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/vouchers")).andExpect(status().isUnauthorized());

        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);

        mockMvc.perform(post("/api/vouchers/validate")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherValidationRequest("WELCOME10"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/vouchers/validate")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherValidationRequest("WELCOME10"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/vouchers").header("Authorization", bearer(customer.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/vouchers").header("Authorization", bearer(staff.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/vouchers").header("Authorization", bearer(admin.token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/vouchers/validate")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherValidationRequest("MISSING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @Test
    void adminCreatesUpdatesAndRejectsInvalidVouchers() throws Exception {
        Session admin = session(RoleName.ADMIN);
        Instant start = clock.instant().minusSeconds(60);
        Instant end = clock.instant().plusSeconds(3600);

        JsonNode created = read(mockMvc.perform(post("/api/admin/vouchers")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherCreateRequest(
                                " welcome10 ",
                                VoucherType.PERCENT,
                                new BigDecimal("10.00"),
                                new BigDecimal("500000.00"),
                                new BigDecimal("150000.00"),
                                10,
                                start,
                                end,
                                true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WELCOME10"))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andReturn());
        long id = created.get("id").asLong();

        mockMvc.perform(post("/api/admin/vouchers")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherCreateRequest(
                                "welcome10",
                                VoucherType.PERCENT,
                                new BigDecimal("10.00"),
                                null,
                                null,
                                null,
                                start,
                                end,
                                true))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/vouchers")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherCreateRequest(
                                "SALE50K",
                                VoucherType.FIXED,
                                new BigDecimal("50000.00"),
                                null,
                                null,
                                null,
                                start,
                                end,
                                true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SALE50K"));

        mockMvc.perform(post("/api/admin/vouchers")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherCreateRequest(
                                "BADPCT",
                                VoucherType.PERCENT,
                                new BigDecimal("101.00"),
                                null,
                                null,
                                null,
                                start,
                                end,
                                true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/vouchers")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherCreateRequest(
                                "BADMONEY",
                                VoucherType.FIXED,
                                new BigDecimal("-1.00"),
                                null,
                                null,
                                null,
                                start,
                                end,
                                true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/vouchers")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherCreateRequest(
                                "BADDATE",
                                VoucherType.FIXED,
                                new BigDecimal("10000.00"),
                                null,
                                null,
                                null,
                                end,
                                start,
                                true))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/vouchers/" + id)
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherUpdateRequest(
                                "WELCOME10",
                                VoucherType.PERCENT,
                                new BigDecimal("12.00"),
                                new BigDecimal("500000.00"),
                                new BigDecimal("150000.00"),
                                10,
                                start,
                                end,
                                true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountValue").value(12.0))
                .andExpect(jsonPath("$.usedCount").value(0));

        mockMvc.perform(put("/api/admin/vouchers/" + id)
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WELCOME10","type":"PERCENT","discountValue":12.00,\
                                "minOrderAmount":500000.00,"maxDiscountAmount":150000.00,"usageLimit":10,\
                                "startAt":"%s","endAt":"%s","active":true,"usedCount":99}
                                """.formatted(start, end)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: usedCount"));
        assertThat(voucherRepository.findById(id).orElseThrow().getUsedCount()).isEqualTo(0);
    }

    @Test
    void customerPreviewUsesCurrentCartAndDoesNotConsumeVoucher() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Instant start = clock.instant().minusSeconds(60);
        Instant end = clock.instant().plusSeconds(3600);
        Voucher percent = persisted("WELCOME10", VoucherType.PERCENT, "10.00", "0.00", "150000.00", null, start, end, true);
        CatalogFixture catalog = publishedProduct(new BigDecimal("500000.00"), new BigDecimal("400000.00"), 8);

        mockMvc.perform(post("/api/vouchers/validate")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WELCOME10\",\"subtotal\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: subtotal"));

        JsonNode empty = validate(customer.token, "WELCOME10");
        assertThat(empty.get("valid").asBoolean()).isFalse();
        assertThat(empty.get("message").asText()).isEqualTo("Cart is empty");
        assertThat(cartRepository.existsByUser_Id(customer.userId)).isFalse();

        addToCart(customer.token, catalog.variantId, 2);
        JsonNode preview = validate(customer.token, " welcome10 ");
        assertThat(preview.get("valid").asBoolean()).isTrue();
        assertThat(preview.get("code").asText()).isEqualTo("WELCOME10");
        assertThat(preview.get("subtotal").decimalValue()).isEqualByComparingTo("800000.00");
        assertThat(preview.get("discountAmount").decimalValue()).isEqualByComparingTo("80000.00");
        assertThat(preview.get("totalAfterDiscount").decimalValue()).isEqualByComparingTo("720000.00");
        assertThat(voucherRepository.findById(percent.getId()).orElseThrow().getUsedCount()).isEqualTo(0);
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(
                cartRepository.findByUser_Id(customer.userId).orElseThrow().getId())).hasSize(1);

        JsonNode missing = validate(customer.token, "DOES_NOT_EXIST");
        assertThat(missing.get("valid").asBoolean()).isFalse();
        assertThat(missing.get("message").asText()).isEqualTo("Voucher is invalid");

        Product product = productRepository.findById(catalog.productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        JsonNode unavailable = validate(customer.token, "WELCOME10");
        assertThat(unavailable.get("valid").asBoolean()).isFalse();
        assertThat(unavailable.get("message").asText()).isEqualTo("Cart contains unavailable items");
        assertThat(voucherRepository.findById(percent.getId()).orElseThrow().getUsedCount()).isEqualTo(0);
    }

    @Test
    void customerPreviewHonorsTimeLimitAndMinOrder() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Instant now = clock.instant();
        CatalogFixture catalog = publishedProduct(new BigDecimal("500000.00"), null, 5);
        addToCart(customer.token, catalog.variantId, 1);

        persisted("FUTURE10", VoucherType.PERCENT, "10.00", null, null, null, now.plusSeconds(3600), now.plusSeconds(7200), true);
        persisted("PAST10", VoucherType.PERCENT, "10.00", null, null, null, now.minusSeconds(7200), now.minusSeconds(3600), true);
        persisted("OFF10", VoucherType.PERCENT, "10.00", null, null, null, now.minusSeconds(60), now.plusSeconds(3600), false);
        Voucher limited = persisted("LIMIT1", VoucherType.PERCENT, "10.00", null, null, 1, now.minusSeconds(60), now.plusSeconds(3600), true);
        limited.setUsedCount(1);
        voucherRepository.saveAndFlush(limited);
        persisted("OPEN10", VoucherType.PERCENT, "10.00", null, null, null, now.minusSeconds(60), now.plusSeconds(3600), true);
        persisted("MIN500", VoucherType.PERCENT, "10.00", "500001.00", null, null, now.minusSeconds(60), now.plusSeconds(3600), true);

        assertThat(validate(customer.token, "FUTURE10").get("valid").asBoolean()).isFalse();
        assertThat(validate(customer.token, "PAST10").get("valid").asBoolean()).isFalse();
        assertThat(validate(customer.token, "OFF10").get("valid").asBoolean()).isFalse();
        assertThat(validate(customer.token, "LIMIT1").get("valid").asBoolean()).isFalse();
        assertThat(validate(customer.token, "MIN500").get("valid").asBoolean()).isFalse();
        JsonNode open = validate(customer.token, "OPEN10");
        assertThat(open.get("valid").asBoolean()).isTrue();
        assertThat(open.get("discountAmount").decimalValue()).isEqualByComparingTo("50000.00");
        assertThat(voucherRepository.findByCodeIgnoreCase("LIMIT1").orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(voucherRepository.findByCodeIgnoreCase("OPEN10").orElseThrow().getUsedCount()).isEqualTo(0);
    }

    private JsonNode validate(String token, String code) throws Exception {
        return read(mockMvc.perform(post("/api/vouchers/validate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VoucherValidationRequest(code))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void addToCart(String token, long variantId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(variantId, quantity))))
                .andExpect(status().isOk());
    }

    private Voucher persisted(
            String code,
            VoucherType type,
            String discount,
            String minOrder,
            String maxDiscount,
            Integer usageLimit,
            Instant start,
            Instant end,
            boolean active) {
        Voucher voucher = new Voucher();
        voucher.setCode(code);
        voucher.setType(type);
        voucher.setDiscountValue(new BigDecimal(discount));
        voucher.setMinOrderAmount(minOrder == null ? null : new BigDecimal(minOrder));
        voucher.setMaxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        voucher.setUsageLimit(usageLimit);
        voucher.setUsedCount(0);
        voucher.setStartAt(start);
        voucher.setEndAt(end);
        voucher.setActive(active);
        return voucherRepository.saveAndFlush(voucher);
    }

    private CatalogFixture publishedProduct(BigDecimal price, BigDecimal salePrice, int stock) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Voucher Cat " + suffix);
        category.setSlug("voucher-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("Voucher Brand " + suffix);
        brand.setSlug("voucher-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);

        Product product = new Product();
        product.setName("Voucher Tee " + suffix);
        product.setSlug("voucher-tee-" + suffix);
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
        variant.setSku("VCH-" + suffix);
        variant.setStockQuantity(stock);
        variant.setActive(true);
        productVariantRepository.saveAndFlush(variant);
        return new CatalogFixture(product.getId(), variant.getId());
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "vch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Voucher " + roleName.name());
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

    private record CatalogFixture(long productId, long variantId) {
    }
}
