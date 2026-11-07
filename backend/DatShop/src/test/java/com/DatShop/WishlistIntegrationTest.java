package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.wishlist.AddWishlistItemRequest;
import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Color;
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
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.SizeRepository;
import com.DatShop.repository.UserRepository;
import com.DatShop.repository.WishlistItemRepository;
import com.DatShop.repository.WishlistRepository;
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
class WishlistIntegrationTest {

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
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Test
    void anonymousStaffAndAdminCannotUseWishlist() throws Exception {
        mockMvc.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/wishlist/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddWishlistItemRequest(1L))))
                .andExpect(status().isUnauthorized());

        Session staff = session(RoleName.STAFF);
        mockMvc.perform(get("/api/wishlist").header("Authorization", bearer(staff.token)))
                .andExpect(status().isForbidden());
        Session admin = session(RoleName.ADMIN);
        mockMvc.perform(get("/api/wishlist").header("Authorization", bearer(admin.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/wishlist/items")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddWishlistItemRequest(1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWishlistDoesNotCreateRowAndStatusDoesNotCreateRow() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        assertThat(wishlistRepository.existsByUser_Id(customer.userId)).isFalse();

        mockMvc.perform(get("/api/wishlist").header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.itemCount").value(0));
        assertThat(wishlistRepository.existsByUser_Id(customer.userId)).isFalse();

        mockMvc.perform(get("/api/wishlist/items/99/status").header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(99))
                .andExpect(jsonPath("$.wishlisted").value(false));
        assertThat(wishlistRepository.existsByUser_Id(customer.userId)).isFalse();
    }

    @Test
    void addIsLazyIdempotentAndSupportsOutOfStockPublicProduct() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture first = publishedProduct(new BigDecimal("500000.00"), null, 4);
        CatalogFixture second = publishedProduct(new BigDecimal("100000.00"), null, 0);

        mockMvc.perform(get("/api/wishlist/items/" + first.productId + "/status")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(jsonPath("$.wishlisted").value(false));

        JsonNode added = read(postItem(customer.token, first.productId).andExpect(status().isOk()).andReturn());
        assertThat(wishlistRepository.existsByUser_Id(customer.userId)).isTrue();
        assertThat(added.get("itemCount").asInt()).isEqualTo(1);
        assertThat(added.get("items").get(0).get("productId").asLong()).isEqualTo(first.productId);
        assertThat(added.get("items").get(0).get("effectivePrice").decimalValue()).isEqualByComparingTo("500000.00");
        assertThat(added.get("items").get(0).get("available").asBoolean()).isTrue();
        assertThat(added.get("items").get(0).get("inStock").asBoolean()).isTrue();
        assertThat(added.get("items").get(0).get("primaryImageUrl").asText()).isEqualTo(first.imageUrl);

        postItem(customer.token, first.productId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1));
        Long wishlistId = wishlistRepository.findByUser_Id(customer.userId).orElseThrow().getId();
        assertThat(wishlistItemRepository.findByWishlist_IdOrderByCreatedAtDescIdDesc(wishlistId)).hasSize(1);

        postItem(customer.token, second.productId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2));
        JsonNode withZeroStock = getWishlist(customer.token);
        JsonNode zeroItem = itemFor(withZeroStock, second.productId);
        assertThat(zeroItem.get("available").asBoolean()).isTrue();
        assertThat(zeroItem.get("inStock").asBoolean()).isFalse();
        assertThat(productVariantRepository.findById(second.variantId).orElseThrow().getStockQuantity()).isEqualTo(0);

        mockMvc.perform(get("/api/wishlist/items/" + first.productId + "/status")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(jsonPath("$.wishlisted").value(true));
    }

    @Test
    void rejectsNonPublicProductsOnAdd() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture draft = product(ProductStatus.DRAFT, CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, 3);
        CatalogFixture inactive = product(ProductStatus.INACTIVE, CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, 3);
        CatalogFixture inactiveCategory = product(ProductStatus.ACTIVE, CatalogStatus.INACTIVE, CatalogStatus.ACTIVE, 3);
        CatalogFixture inactiveBrand = product(ProductStatus.ACTIVE, CatalogStatus.ACTIVE, CatalogStatus.INACTIVE, 3);

        postItem(customer.token, 999999L).andExpect(status().isNotFound());
        postItem(customer.token, draft.productId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This product is not available"));
        postItem(customer.token, inactive.productId).andExpect(status().isBadRequest());
        postItem(customer.token, inactiveCategory.productId).andExpect(status().isBadRequest());
        postItem(customer.token, inactiveBrand.productId).andExpect(status().isBadRequest());
        assertThat(wishlistRepository.existsByUser_Id(customer.userId)).isFalse();
    }

    @Test
    void staleCatalogChangesStayVisibleAndFollowCurrentPrice() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture catalog = publishedProduct(new BigDecimal("500000.00"), null, 2);
        postItem(customer.token, catalog.productId).andExpect(status().isOk());

        JsonNode before = getWishlist(customer.token);
        assertThat(before.get("items").get(0).get("effectivePrice").decimalValue()).isEqualByComparingTo("500000.00");

        Product product = productRepository.findById(catalog.productId).orElseThrow();
        product.setSalePrice(new BigDecimal("400000.00"));
        productRepository.saveAndFlush(product);
        JsonNode afterSale = getWishlist(customer.token);
        assertThat(afterSale.get("items").get(0).get("effectivePrice").decimalValue()).isEqualByComparingTo("400000.00");
        assertThat(afterSale.get("items")).hasSize(1);

        ProductVariant variant = productVariantRepository.findById(catalog.variantId).orElseThrow();
        variant.setStockQuantity(0);
        productVariantRepository.saveAndFlush(variant);
        JsonNode afterZero = getWishlist(customer.token);
        assertThat(afterZero.get("items")).hasSize(1);
        assertThat(afterZero.get("items").get(0).get("available").asBoolean()).isTrue();
        assertThat(afterZero.get("items").get(0).get("inStock").asBoolean()).isFalse();
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(0);

        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        assertThat(getWishlist(customer.token).get("items").get(0).get("available").asBoolean()).isFalse();
        product.setStatus(ProductStatus.DRAFT);
        productRepository.saveAndFlush(product);
        assertThat(getWishlist(customer.token).get("items")).hasSize(1);
        assertThat(getWishlist(customer.token).get("items").get(0).get("available").asBoolean()).isFalse();

        product.setStatus(ProductStatus.ACTIVE);
        productRepository.saveAndFlush(product);
        product.getCategory().setStatus(CatalogStatus.INACTIVE);
        categoryRepository.saveAndFlush(product.getCategory());
        assertThat(getWishlist(customer.token).get("items").get(0).get("available").asBoolean()).isFalse();
        product.getCategory().setStatus(CatalogStatus.ACTIVE);
        categoryRepository.saveAndFlush(product.getCategory());
        product.getBrand().setStatus(CatalogStatus.INACTIVE);
        brandRepository.saveAndFlush(product.getBrand());
        JsonNode afterBrand = getWishlist(customer.token);
        assertThat(afterBrand.get("items")).hasSize(1);
        assertThat(afterBrand.get("items").get(0).get("available").asBoolean()).isFalse();
    }

    @Test
    void wishlistsAreOwnerScopedAndRemoveClearAreIdempotent() throws Exception {
        Session owner = session(RoleName.CUSTOMER);
        Session other = session(RoleName.CUSTOMER);
        CatalogFixture ownerProduct = publishedProduct(new BigDecimal("20000.00"), null, 5);
        CatalogFixture otherProduct = publishedProduct(new BigDecimal("30000.00"), null, 5);

        mockMvc.perform(delete("/api/wishlist/items/" + ownerProduct.productId)
                        .header("Authorization", bearer(owner.token)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(delete("/api/wishlist/items").header("Authorization", bearer(owner.token)))
                .andExpect(status().isNoContent());
        assertThat(wishlistRepository.existsByUser_Id(owner.userId)).isFalse();

        postItem(owner.token, ownerProduct.productId).andExpect(status().isOk());
        postItem(other.token, otherProduct.productId).andExpect(status().isOk());

        JsonNode ownerCart = getWishlist(owner.token);
        assertThat(ownerCart.get("itemCount").asInt()).isEqualTo(1);
        assertThat(ownerCart.get("items").get(0).get("productId").asLong()).isEqualTo(ownerProduct.productId);

        mockMvc.perform(delete("/api/wishlist/items/" + otherProduct.productId)
                        .header("Authorization", bearer(owner.token)))
                .andExpect(status().isNoContent());
        assertThat(getWishlist(other.token).get("itemCount").asInt()).isEqualTo(1);
        assertThat(getWishlist(owner.token).get("itemCount").asInt()).isEqualTo(1);

        mockMvc.perform(delete("/api/wishlist/items/" + ownerProduct.productId)
                        .header("Authorization", bearer(owner.token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/wishlist/items/" + ownerProduct.productId + "/status")
                        .header("Authorization", bearer(owner.token)))
                .andExpect(jsonPath("$.wishlisted").value(false));
        assertThat(wishlistRepository.existsByUser_Id(owner.userId)).isTrue();
        assertThat(wishlistItemRepository.findByWishlist_IdOrderByCreatedAtDescIdDesc(
                wishlistRepository.findByUser_Id(owner.userId).orElseThrow().getId())).isEmpty();

        postItem(owner.token, ownerProduct.productId).andExpect(status().isOk());
        mockMvc.perform(delete("/api/wishlist/items").header("Authorization", bearer(owner.token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/wishlist/items").header("Authorization", bearer(owner.token)))
                .andExpect(status().isNoContent());
        assertThat(getWishlist(other.token).get("itemCount").asInt()).isEqualTo(1);
        assertThat(productVariantRepository.findById(ownerProduct.variantId).orElseThrow().getStockQuantity())
                .isEqualTo(5);
        assertThat(productVariantRepository.findById(otherProduct.variantId).orElseThrow().getStockQuantity())
                .isEqualTo(5);
    }

    private org.springframework.test.web.servlet.ResultActions postItem(String token, long productId) throws Exception {
        return mockMvc.perform(post("/api/wishlist/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddWishlistItemRequest(productId))));
    }

    private JsonNode getWishlist(String token) throws Exception {
        return read(mockMvc.perform(get("/api/wishlist").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static JsonNode itemFor(JsonNode wishlist, long productId) {
        for (JsonNode item : wishlist.get("items")) {
            if (item.get("productId").asLong() == productId) {
                return item;
            }
        }
        throw new AssertionError("Missing product " + productId);
    }

    private CatalogFixture publishedProduct(BigDecimal price, BigDecimal salePrice, int stock) {
        return catalogProduct(
                ProductStatus.ACTIVE, CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, true, price, salePrice, stock, true);
    }

    private CatalogFixture product(
            ProductStatus status, CatalogStatus categoryStatus, CatalogStatus brandStatus, int stock) {
        return catalogProduct(status, categoryStatus, brandStatus, true, new BigDecimal("10000.00"), null, stock, true);
    }

    private CatalogFixture catalogProduct(
            ProductStatus status,
            CatalogStatus categoryStatus,
            CatalogStatus brandStatus,
            boolean variantActive,
            BigDecimal price,
            BigDecimal salePrice,
            int stock,
            boolean withImage) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Wish Cat " + suffix);
        category.setSlug("wish-cat-" + suffix);
        category.setStatus(categoryStatus);
        categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("Wish Brand " + suffix);
        brand.setSlug("wish-brand-" + suffix);
        brand.setStatus(brandStatus);
        brandRepository.save(brand);

        Product product = new Product();
        String productName = "Wish Shirt " + suffix;
        product.setName(productName);
        product.setSlug("wish-shirt-" + suffix);
        product.setPrice(price);
        product.setSalePrice(salePrice);
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(status);
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
        variant.setSku("WISH-" + suffix);
        variant.setStockQuantity(stock);
        variant.setActive(variantActive);
        productVariantRepository.saveAndFlush(variant);

        String imageUrl = null;
        if (withImage) {
            imageUrl = "https://cdn.example/wish-" + suffix + ".jpg";
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(imageUrl);
            image.setIsPrimary(true);
            image.setSortOrder(1);
            productImageRepository.saveAndFlush(image);
        }

        return new CatalogFixture(product.getId(), variant.getId(), productName, imageUrl);
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "wish_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Wish " + roleName.name());
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

    private record CatalogFixture(long productId, long variantId, String productName, String imageUrl) {
    }
}
