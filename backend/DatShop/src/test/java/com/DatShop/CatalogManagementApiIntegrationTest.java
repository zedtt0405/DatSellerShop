package com.DatShop;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.catalog.BrandCreateRequest;
import com.DatShop.dto.catalog.BrandUpdateRequest;
import com.DatShop.dto.catalog.CategoryCreateRequest;
import com.DatShop.dto.catalog.CategoryUpdateRequest;
import com.DatShop.dto.catalog.ColorCreateRequest;
import com.DatShop.dto.catalog.ColorUpdateRequest;
import com.DatShop.dto.catalog.ProductCreateRequest;
import com.DatShop.dto.catalog.ProductImageCreateRequest;
import com.DatShop.dto.catalog.ProductUpdateRequest;
import com.DatShop.dto.catalog.ProductVariantCreateRequest;
import com.DatShop.dto.catalog.ProductVariantUpdateRequest;
import com.DatShop.dto.catalog.SizeCreateRequest;
import com.DatShop.dto.catalog.SizeUpdateRequest;
import com.DatShop.dto.catalog.StockUpdateRequest;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.UserRepository;
import java.math.BigDecimal;
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
class CatalogManagementApiIntegrationTest {

    private static final String PASSWORD = "S3cure-Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void anonymousAndCustomerCannotAccessAdminCatalog() throws Exception {
        mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        String customerToken = tokenFor(RoleName.CUSTOMER);
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest(
                                "Denied", "denied-" + suffix(), 1L, 1L, ProductStatus.DRAFT))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanManageProductsButNotMasterData() throws Exception {
        String suffix = suffix();
        String adminToken = tokenFor(RoleName.ADMIN);
        String staffToken = tokenFor(RoleName.STAFF);
        long categoryId = idOf(postJson(adminToken, "/api/admin/categories",
                new CategoryCreateRequest("Cat " + suffix, "cat-" + suffix, null, CatalogStatus.ACTIVE, null)));
        long brandId = idOf(postJson(adminToken, "/api/admin/brands",
                new BrandCreateRequest("Brand " + suffix, "brand-" + suffix, null, null, CatalogStatus.ACTIVE)));

        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk());

        MvcResult created = postJson(staffToken, "/api/admin/products",
                productRequest("Shirt " + suffix, "shirt-" + suffix, categoryId, brandId, ProductStatus.DRAFT));
        long productId = idOf(created);
        mockMvc.perform(put("/api/admin/products/" + productId)
                        .header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductUpdateRequest(
                                "Shirt updated " + suffix,
                                "shirt-" + suffix,
                                "Cotton",
                                new BigDecimal("120000.00"),
                                null,
                                categoryId,
                                brandId,
                                ProductStatus.ACTIVE,
                                false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shirt updated " + suffix));

        MvcResult image = postJson(staffToken, "/api/admin/products/" + productId + "/images",
                new ProductImageCreateRequest("https://cdn.example/a.jpg", "a", false, 1));
        mockMvc.perform(get("/api/admin/products/" + productId + "/images")
                        .header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isPrimary").value(true));

        long sizeId = idOf(postJson(adminToken, "/api/admin/sizes", new SizeCreateRequest("M-" + suffix, 1, true)));
        long colorId = idOf(postJson(adminToken, "/api/admin/colors",
                new ColorCreateRequest("Black-" + suffix, "#000000", true)));
        MvcResult variant = postJson(staffToken, "/api/admin/products/" + productId + "/variants",
                new ProductVariantCreateRequest(productId, sizeId, colorId, "SKU-" + suffix, 3, true));
        mockMvc.perform(patch("/api/admin/products/" + productId + "/variants/" + idOf(variant) + "/stock")
                        .header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockUpdateRequest(8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(8));

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryCreateRequest(
                                "No " + suffix, "no-cat-" + suffix, null, CatalogStatus.ACTIVE, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/brands")
                        .header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BrandCreateRequest(
                                "No " + suffix, "no-brand-" + suffix, null, null, CatalogStatus.ACTIVE))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/sizes")
                        .header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SizeCreateRequest("No-" + suffix, 1, true))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/colors")
                        .header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ColorCreateRequest("No-" + suffix, "#111111", true))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/products/" + productId + "/images/" + idOf(image))
                        .header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductImageCreateRequest("https://cdn.example/b.jpg", "b", true, 1))))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanManageMasterDataAndRejectsDuplicates() throws Exception {
        String suffix = suffix();
        String token = tokenFor(RoleName.ADMIN);

        MvcResult parent = postJson(token, "/api/admin/categories",
                new CategoryCreateRequest("Men " + suffix, "men-" + suffix, null, CatalogStatus.ACTIVE, null));
        long parentId = idOf(parent);
        mockMvc.perform(put("/api/admin/categories/" + parentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryUpdateRequest(
                                "Menwear " + suffix, "men-" + suffix, "Updated", CatalogStatus.ACTIVE, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Menwear " + suffix));
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryCreateRequest(
                                "Dup " + suffix, "men-" + suffix, null, CatalogStatus.ACTIVE, null))))
                .andExpect(status().isConflict());

        MvcResult child = postJson(token, "/api/admin/categories",
                new CategoryCreateRequest("Shirts " + suffix, "shirts-" + suffix, null, CatalogStatus.ACTIVE, parentId));
        mockMvc.perform(put("/api/admin/categories/" + parentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryUpdateRequest(
                                "Menwear " + suffix, "men-" + suffix, null, CatalogStatus.ACTIVE, idOf(child)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cycle")));

        postJson(token, "/api/admin/brands",
                new BrandCreateRequest("Nike " + suffix, "nike-" + suffix, null, null, CatalogStatus.ACTIVE));
        mockMvc.perform(post("/api/admin/brands")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BrandCreateRequest(
                                "Nike " + suffix, "nike-other-" + suffix, null, null, CatalogStatus.ACTIVE))))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/brands/" + idOf(postJson(token, "/api/admin/brands",
                        new BrandCreateRequest("Puma " + suffix, "puma-" + suffix, null, null, CatalogStatus.ACTIVE))))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BrandUpdateRequest(
                                "Puma " + suffix, "puma-" + suffix, "Logo", null, CatalogStatus.INACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        postJson(token, "/api/admin/sizes", new SizeCreateRequest("XL-" + suffix, 5, true));
        mockMvc.perform(post("/api/admin/sizes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SizeCreateRequest("XL-" + suffix, 1, true))))
                .andExpect(status().isConflict());
        long sizeId = idOf(postJson(token, "/api/admin/sizes", new SizeCreateRequest("S-" + suffix, 1, true)));
        mockMvc.perform(put("/api/admin/sizes/" + sizeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SizeUpdateRequest("S-" + suffix, 2, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        postJson(token, "/api/admin/colors", new ColorCreateRequest("Red-" + suffix, "#ff0000", true));
        mockMvc.perform(post("/api/admin/colors")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ColorCreateRequest("Red-" + suffix, "#111111", true))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/colors")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ColorCreateRequest("Bad-" + suffix, "red", true))))
                .andExpect(status().isBadRequest());
        long colorId = idOf(postJson(token, "/api/admin/colors",
                new ColorCreateRequest("Blue-" + suffix, "#0000ff", true)));
        mockMvc.perform(put("/api/admin/colors/" + colorId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ColorUpdateRequest("Blue-" + suffix, "#0000FF", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/admin/categories").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("men-" + suffix)));
    }

    @Test
    void adminProductCreateUpdateAndPublicVisibility() throws Exception {
        String suffix = suffix();
        String token = tokenFor(RoleName.ADMIN);
        Fixture fixture = fixture(token, suffix);

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest(
                                "Live " + suffix, "live-" + suffix, fixture.categoryId, fixture.brandId,
                                ProductStatus.ACTIVE))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/products/")))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        long draftId = idOf(postJson(token, "/api/admin/products", productRequest(
                "Draft " + suffix, "draft-" + suffix, fixture.categoryId, fixture.brandId, ProductStatus.DRAFT)));
        mockMvc.perform(get("/api/admin/products").param("status", "DRAFT").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem("draft-" + suffix)));
        mockMvc.perform(get("/api/products").param("q", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem("live-" + suffix)))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem("draft-" + suffix))));

        mockMvc.perform(put("/api/admin/products/" + draftId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductUpdateRequest(
                                "Draft " + suffix, "draft-" + suffix, null, new BigDecimal("100000.00"),
                                null, fixture.categoryId, fixture.brandId, ProductStatus.DRAFT, false))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest(
                                "Dup " + suffix, "live-" + suffix, fixture.categoryId, fixture.brandId,
                                ProductStatus.DRAFT))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCreateRequest(
                                "Sale " + suffix, "sale-" + suffix, null, new BigDecimal("10.00"),
                                new BigDecimal("11.00"), fixture.categoryId, fixture.brandId,
                                ProductStatus.DRAFT, false))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest(
                                "Missing cat " + suffix, "missing-cat-" + suffix, -1L, fixture.brandId,
                                ProductStatus.DRAFT))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest(
                                "Missing brand " + suffix, "missing-brand-" + suffix, fixture.categoryId, -1L,
                                ProductStatus.DRAFT))))
                .andExpect(status().isNotFound());

        long inactiveCategory = idOf(postJson(token, "/api/admin/categories",
                new CategoryCreateRequest("Old " + suffix, "old-" + suffix, null, CatalogStatus.INACTIVE, null)));
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest(
                                "Bad active " + suffix, "bad-active-" + suffix, inactiveCategory, fixture.brandId,
                                ProductStatus.ACTIVE))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/products/live-" + suffix)).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/draft-" + suffix)).andExpect(status().isNotFound());
    }

    @Test
    void adminImageAndVariantRules() throws Exception {
        String suffix = suffix();
        String token = tokenFor(RoleName.ADMIN);
        Fixture fixture = fixture(token, suffix);
        long productA = idOf(postJson(token, "/api/admin/products", productRequest(
                "A " + suffix, "a-" + suffix, fixture.categoryId, fixture.brandId, ProductStatus.ACTIVE)));
        long productB = idOf(postJson(token, "/api/admin/products", productRequest(
                "B " + suffix, "b-" + suffix, fixture.categoryId, fixture.brandId, ProductStatus.ACTIVE)));

        long first = idOf(postJson(token, "/api/admin/products/" + productA + "/images",
                new ProductImageCreateRequest("https://cdn.example/1.jpg", "one", false, 2)));
        mockMvc.perform(get("/api/admin/products/" + productA + "/images").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].isPrimary").value(true));
        long second = idOf(postJson(token, "/api/admin/products/" + productA + "/images",
                new ProductImageCreateRequest("https://cdn.example/2.jpg", "two", true, 1)));
        mockMvc.perform(get("/api/admin/products/" + productA + "/images").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].isPrimary").value(true))
                .andExpect(jsonPath("$[1].isPrimary").value(false));
        mockMvc.perform(put("/api/admin/products/" + productB + "/images/" + first)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductImageCreateRequest("https://cdn.example/x.jpg", "x", true, 0))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/products/" + productA + "/images/" + first)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/products/" + productA + "/images").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].isPrimary").value(true));

        long third = idOf(postJson(token, "/api/admin/products/" + productA + "/images",
                new ProductImageCreateRequest("https://cdn.example/3.jpg", "three", false, 3)));
        mockMvc.perform(put("/api/admin/products/" + productA + "/images/" + second)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductImageCreateRequest("https://cdn.example/2.jpg", "two", false, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(false));
        mockMvc.perform(get("/api/admin/products/" + productA + "/images").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].isPrimary").value(false))
                .andExpect(jsonPath("$[1].id").value(third))
                .andExpect(jsonPath("$[1].isPrimary").value(true));
        mockMvc.perform(delete("/api/admin/products/" + productA + "/images/" + third)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/products/" + productA + "/images/" + second)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/products/" + productA + "/images").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));

        long sizeId = idOf(postJson(token, "/api/admin/sizes", new SizeCreateRequest("M-" + suffix, 1, true)));
        long colorId = idOf(postJson(token, "/api/admin/colors",
                new ColorCreateRequest("Black-" + suffix, "#000000", true)));
        MvcResult variant = postJson(token, "/api/admin/products/" + productA + "/variants",
                new ProductVariantCreateRequest(productA, sizeId, colorId, "SKU-A-" + suffix, 2, true));
        long variantId = idOf(variant);
        mockMvc.perform(post("/api/admin/products/" + productA + "/variants")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductVariantCreateRequest(
                                productA, sizeId, colorId, "SKU-B-" + suffix, 1, true))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/products/" + productA + "/variants")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductVariantCreateRequest(
                                productA, null, null, "SKU-N1-" + suffix, 1, true))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/products/" + productA + "/variants")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductVariantCreateRequest(
                                productA, null, null, "SKU-N2-" + suffix, 1, true))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/products/" + productA + "/variants")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductVariantCreateRequest(
                                productA, sizeId, colorId, "SKU-A-" + suffix, 1, true))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/products/" + productA + "/variants")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductVariantCreateRequest(
                                productA, sizeId, colorId, "SKU-NEG-" + suffix, -1, true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/products/" + productB + "/variants/" + variantId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductVariantUpdateRequest(
                                productB, sizeId, colorId, "SKU-MOVE-" + suffix, 1, true))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/products/" + productA + "/variants/" + variantId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductVariantUpdateRequest(
                                productB, sizeId, colorId, "SKU-MOVE-" + suffix, 1, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("productId")));
        mockMvc.perform(put("/api/admin/products/" + productA + "/variants/" + variantId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductVariantUpdateRequest(
                                productA, sizeId, colorId, "SKU-A-" + suffix, 2, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/admin/products/" + productA)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[*].sku", hasItem("SKU-A-" + suffix)));
        mockMvc.perform(get("/api/products/a-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[*].sku", not(hasItem("SKU-A-" + suffix))));
        mockMvc.perform(patch("/api/admin/products/" + productA + "/variants/" + variantId + "/stock")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockUpdateRequest(15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(15))
                .andExpect(jsonPath("$.active").value(false));
    }

    private Fixture fixture(String token, String suffix) throws Exception {
        long categoryId = idOf(postJson(token, "/api/admin/categories",
                new CategoryCreateRequest("Cat " + suffix, "cat-" + suffix, null, CatalogStatus.ACTIVE, null)));
        long brandId = idOf(postJson(token, "/api/admin/brands",
                new BrandCreateRequest("Brand " + suffix, "brand-" + suffix, null, null, CatalogStatus.ACTIVE)));
        return new Fixture(categoryId, brandId);
    }

    private ProductCreateRequest productRequest(
            String name, String slug, Long categoryId, Long brandId, ProductStatus status) {
        return new ProductCreateRequest(
                name, slug, null, new BigDecimal("100000.00"), null, categoryId, brandId, status, false);
    }

    private MvcResult postJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private String tokenFor(RoleName roleName) throws Exception {
        String username = "adm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Catalog " + roleName.name());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(roleRepository.findByName(roleName).orElseThrow()));
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record Fixture(long categoryId, long brandId) {
    }
}
