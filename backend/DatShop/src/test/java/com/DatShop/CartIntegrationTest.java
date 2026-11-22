package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.cart.UpdateCartItemRequest;
import com.DatShop.model.Brand;
import com.DatShop.model.CartItem;
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
import com.DatShop.repository.CartItemRepository;
import com.DatShop.repository.CartRepository;
import com.DatShop.repository.CategoryRepository;
import com.DatShop.repository.ColorRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.SizeRepository;
import com.DatShop.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
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
class CartIntegrationTest {

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
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void anonymousStaffAndAdminCannotUseCart() throws Exception {
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(1L, 1))))
                .andExpect(status().isUnauthorized());

        Session staff = session(RoleName.STAFF);
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(staff.token)))
                .andExpect(status().isForbidden());
        Session admin = session(RoleName.ADMIN);
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(admin.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(1L, 1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCartDoesNotCreatePersistedCart() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        assertThat(cartRepository.existsByUser_Id(customer.userId)).isFalse();

        mockMvc.perform(get("/api/cart").header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.totalQuantity").value(0));
        JsonNode body = read(mockMvc.perform(get("/api/cart").header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(body.get("subtotal").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(cartRepository.existsByUser_Id(customer.userId)).isFalse();
    }

    @Test
    void customerCartMergesVariantCalculatesPriceAndDoesNotTouchStock() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture catalog = publishedProduct(new BigDecimal("100000.00"), new BigDecimal("80000.00"), 5);

        JsonNode added = read(postItem(customer.token, catalog.variantId, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.items[0].variantId").value(catalog.variantId))
                .andExpect(jsonPath("$.items[0].sku").value(catalog.sku))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[0].product.name").value(catalog.productName))
                .andExpect(jsonPath("$.items[0].product.primaryImageUrl").value(catalog.imageUrl))
                .andExpect(jsonPath("$.items[0].size.name").value(catalog.sizeName))
                .andExpect(jsonPath("$.items[0].color.name").value(catalog.colorName))
                .andReturn());
        assertThat(added.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("80000.00");
        assertThat(added.get("items").get(0).get("lineSubtotal").decimalValue()).isEqualByComparingTo("160000.00");
        assertThat(added.get("subtotal").decimalValue()).isEqualByComparingTo("160000.00");
        long itemId = added.get("items").get(0).get("id").asLong();
        assertThat(cartRepository.existsByUser_Id(customer.userId)).isTrue();

        postItem(customer.token, catalog.variantId, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(itemId))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.totalQuantity").value(3));

        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(5);

        mockMvc.perform(put("/api/cart/items/" + itemId)
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(4));

        postItem(customer.token, catalog.variantId, 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("stock")));
        mockMvc.perform(put("/api/cart/items/" + itemId)
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("stock")));
        mockMvc.perform(put("/api/cart/items/" + itemId)
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(0))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":" + catalog.variantId + ",\"quantity\":1,\"userId\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: userId"));

        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void deleteReturns204AndClearIsIdempotent() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture catalog = publishedProduct(new BigDecimal("10000.00"), null, 10);

        mockMvc.perform(delete("/api/cart/items").header("Authorization", bearer(customer.token)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(cartRepository.existsByUser_Id(customer.userId)).isFalse();
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);

        JsonNode added = read(postItem(customer.token, catalog.variantId, 2).andExpect(status().isOk()).andReturn());
        long itemId = added.get("items").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/cart/items/" + itemId).header("Authorization", bearer(customer.token)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);

        postItem(customer.token, catalog.variantId, 1).andExpect(status().isOk());
        mockMvc.perform(delete("/api/cart/items").header("Authorization", bearer(customer.token)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(delete("/api/cart/items").header("Authorization", bearer(customer.token)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(cartRepository.existsByUser_Id(customer.userId)).isTrue();
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(
                cartRepository.findByUser_Id(customer.userId).orElseThrow().getId())).isEmpty();
        assertThat(productVariantRepository.findById(catalog.variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void staleStockAndDeactivatedCatalogStayVisible() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture stale = publishedProduct(new BigDecimal("10000.00"), null, 5);
        postItem(customer.token, stale.variantId, 4).andExpect(status().isOk());

        ProductVariant variant = productVariantRepository.findById(stale.variantId).orElseThrow();
        variant.setStockQuantity(2);
        productVariantRepository.saveAndFlush(variant);
        JsonNode afterStock = getCart(customer.token);
        assertThat(afterStock.get("items")).hasSize(1);
        assertThat(afterStock.get("items").get(0).get("quantity").asInt()).isEqualTo(4);
        assertThat(afterStock.get("items").get(0).get("stockQuantity").asInt()).isEqualTo(2);
        assertThat(afterStock.get("items").get(0).get("available").asBoolean()).isFalse();

        variant.setStockQuantity(0);
        productVariantRepository.saveAndFlush(variant);
        JsonNode afterZero = getCart(customer.token);
        assertThat(afterZero.get("items")).hasSize(1);
        assertThat(afterZero.get("items").get(0).get("stockQuantity").asInt()).isEqualTo(0);
        assertThat(afterZero.get("items").get(0).get("available").asBoolean()).isFalse();
        assertThat(productVariantRepository.findById(stale.variantId).orElseThrow().getStockQuantity()).isEqualTo(0);

        variant.setStockQuantity(5);
        variant.setActive(false);
        productVariantRepository.saveAndFlush(variant);
        JsonNode afterInactiveVariant = getCart(customer.token);
        assertThat(afterInactiveVariant.get("items")).hasSize(1);
        assertThat(afterInactiveVariant.get("items").get(0).get("available").asBoolean()).isFalse();

        variant.setActive(true);
        productVariantRepository.saveAndFlush(variant);
        Product product = productRepository.findById(stale.productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        assertThat(getCart(customer.token).get("items").get(0).get("available").asBoolean()).isFalse();
        product.setStatus(ProductStatus.DRAFT);
        productRepository.saveAndFlush(product);
        assertThat(getCart(customer.token).get("items").get(0).get("available").asBoolean()).isFalse();
        assertThat(getCart(customer.token).get("items")).hasSize(1);

        product.setStatus(ProductStatus.ACTIVE);
        productRepository.saveAndFlush(product);
        product.getCategory().setStatus(CatalogStatus.INACTIVE);
        categoryRepository.saveAndFlush(product.getCategory());
        assertThat(getCart(customer.token).get("items").get(0).get("available").asBoolean()).isFalse();
        product.getCategory().setStatus(CatalogStatus.ACTIVE);
        categoryRepository.saveAndFlush(product.getCategory());
        product.getBrand().setStatus(CatalogStatus.INACTIVE);
        brandRepository.saveAndFlush(product.getBrand());
        JsonNode afterBrand = getCart(customer.token);
        assertThat(afterBrand.get("items")).hasSize(1);
        assertThat(afterBrand.get("items").get(0).get("available").asBoolean()).isFalse();
    }

    @Test
    void inactiveSizeOrColorMakesLineUnavailableAndBlocksAdd() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture catalog = publishedProduct(new BigDecimal("10000.00"), null, 5);
        postItem(customer.token, catalog.variantId, 1).andExpect(status().isOk());
        assertThat(getCart(customer.token).get("items").get(0).get("available").asBoolean()).isTrue();

        ProductVariant variant = productVariantRepository.findById(catalog.variantId).orElseThrow();
        Size size = variant.getSize();
        size.setActive(false);
        sizeRepository.saveAndFlush(size);
        assertThat(getCart(customer.token).get("items").get(0).get("available").asBoolean()).isFalse();
        size.setActive(true);
        sizeRepository.saveAndFlush(size);

        Color color = variant.getColor();
        color.setActive(false);
        colorRepository.saveAndFlush(color);
        assertThat(getCart(customer.token).get("items").get(0).get("available").asBoolean()).isFalse();
        color.setActive(true);
        colorRepository.saveAndFlush(color);
        assertThat(getCart(customer.token).get("items").get(0).get("available").asBoolean()).isTrue();

        CatalogFixture blocked = publishedProduct(new BigDecimal("12000.00"), null, 4);
        ProductVariant blockedVariant = productVariantRepository.findById(blocked.variantId).orElseThrow();
        blockedVariant.getSize().setActive(false);
        sizeRepository.saveAndFlush(blockedVariant.getSize());
        postItem(customer.token, blocked.variantId, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This product is not available"));

        CatalogFixture noOptions = catalogProduct(ProductStatus.ACTIVE, true, new BigDecimal("15000.00"), null, 3, true);
        ProductVariant optionless = productVariantRepository.findById(noOptions.variantId).orElseThrow();
        optionless.setSize(null);
        optionless.setColor(null);
        productVariantRepository.saveAndFlush(optionless);
        postItem(customer.token, noOptions.variantId, 1).andExpect(status().isOk());
        assertThat(getCart(customer.token).get("items").get(1).get("available").asBoolean()).isTrue();
    }

    @Test
    void currentPriceFollowsCatalogAndMultipleLinesSumExactly() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture priced = publishedProduct(new BigDecimal("500000.00"), null, 10);
        postItem(customer.token, priced.variantId, 2).andExpect(status().isOk());
        JsonNode firstPrice = getCart(customer.token);
        assertThat(firstPrice.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("500000.00");
        assertThat(firstPrice.get("items").get(0).get("lineSubtotal").decimalValue()).isEqualByComparingTo("1000000.00");

        Product product = productRepository.findById(priced.productId).orElseThrow();
        product.setSalePrice(new BigDecimal("400000.00"));
        productRepository.saveAndFlush(product);
        JsonNode salePrice = getCart(customer.token);
        assertThat(salePrice.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("400000.00");
        assertThat(salePrice.get("items").get(0).get("lineSubtotal").decimalValue()).isEqualByComparingTo("800000.00");

        product.setSalePrice(null);
        product.setPrice(new BigDecimal("450000.00"));
        productRepository.saveAndFlush(product);
        JsonNode latestPrice = getCart(customer.token);
        assertThat(latestPrice.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("450000.00");
        assertThat(latestPrice.get("items").get(0).get("lineSubtotal").decimalValue()).isEqualByComparingTo("900000.00");

        CatalogFixture second = publishedProduct(new BigDecimal("250000.00"), null, 10);
        mockMvc.perform(put("/api/cart/items/" + firstPrice.get("items").get(0).get("id").asLong())
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(2))))
                .andExpect(status().isOk());
        Product firstProduct = productRepository.findById(priced.productId).orElseThrow();
        firstProduct.setPrice(new BigDecimal("100000.00"));
        productRepository.saveAndFlush(firstProduct);
        postItem(customer.token, second.variantId, 3).andExpect(status().isOk());

        JsonNode multi = getCart(customer.token);
        assertThat(multi.get("itemCount").asInt()).isEqualTo(2);
        assertThat(multi.get("totalQuantity").asInt()).isEqualTo(5);
        assertThat(multi.get("subtotal").decimalValue()).isEqualByComparingTo("950000.00");
    }

    @Test
    void sameVariantMergesAndCombinedQuantityIsRejectedAtomically() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture catalog = publishedProduct(new BigDecimal("10000.00"), null, 5);
        postItem(customer.token, catalog.variantId, 2).andExpect(status().isOk());
        postItem(customer.token, catalog.variantId, 3).andExpect(status().isOk());

        Long cartId = cartRepository.findByUser_Id(customer.userId).orElseThrow().getId();
        CartItem merged = cartItemRepository.findByCart_IdAndProductVariant_Id(cartId, catalog.variantId).orElseThrow();
        assertThat(merged.getQuantity()).isEqualTo(5);
        assertThat(cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(
                cartRepository.findByUser_Id(customer.userId).orElseThrow().getId())).hasSize(1);

        CatalogFixture limited = publishedProduct(new BigDecimal("10000.00"), null, 5);
        postItem(customer.token, limited.variantId, 4).andExpect(status().isOk());
        postItem(customer.token, limited.variantId, 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("stock")));
        CartItem remaining = cartItemRepository
                .findByCart_IdAndProductVariant_Id(
                        cartRepository.findByUser_Id(customer.userId).orElseThrow().getId(), limited.variantId)
                .orElseThrow();
        assertThat(remaining.getQuantity()).isEqualTo(4);
        assertThat(productVariantRepository.findById(limited.variantId).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void currentUserCannotAccessAnotherCustomersCartItems() throws Exception {
        Session owner = session(RoleName.CUSTOMER);
        Session other = session(RoleName.CUSTOMER);
        CatalogFixture catalog = publishedProduct(new BigDecimal("50000.00"), null, 3);
        CatalogFixture draft = draftProduct(3);
        CatalogFixture inactive = inactiveVariant(3);

        postItem(owner.token, 999999L, 1).andExpect(status().isNotFound());
        postItem(owner.token, draft.variantId, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This product is not available"));
        postItem(owner.token, inactive.variantId, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This product is not available"));

        JsonNode added = read(postItem(owner.token, catalog.variantId, 1).andExpect(status().isOk()).andReturn());
        long itemId = added.get("items").get(0).get("id").asLong();

        mockMvc.perform(put("/api/cart/items/" + itemId)
                        .header("Authorization", bearer(other.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(2))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/cart/items/" + itemId).header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(other.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(cartRepository.existsByUser_Id(other.userId)).isFalse();

        CatalogFixture otherCatalog = publishedProduct(new BigDecimal("15000.00"), null, 4);
        JsonNode otherAdded = read(postItem(other.token, otherCatalog.variantId, 1).andExpect(status().isOk()).andReturn());
        long otherItemId = otherAdded.get("items").get(0).get("id").asLong();
        JsonNode ownerCart = getCart(owner.token);
        assertThat(ownerCart.get("items")).hasSize(1);
        assertThat(ownerCart.get("items").get(0).get("id").asLong()).isEqualTo(itemId);
        assertThat(ownerCart.get("items").get(0).get("id").asLong()).isNotEqualTo(otherItemId);
    }

    @Test
    void primaryImageIsNullWhenProductHasNoImage() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        CatalogFixture catalog = catalogProduct(ProductStatus.ACTIVE, true, new BigDecimal("10000.00"), null, 3, false);
        JsonNode cart = read(postItem(customer.token, catalog.variantId, 1).andExpect(status().isOk()).andReturn());
        assertThat(cart.get("items").get(0).get("product").get("primaryImageUrl").isNull()).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions postItem(String token, long variantId, int quantity)
            throws Exception {
        return mockMvc.perform(post("/api/cart/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddCartItemRequest(variantId, quantity))));
    }

    private JsonNode getCart(String token) throws Exception {
        return read(mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private CatalogFixture publishedProduct(BigDecimal price, BigDecimal salePrice, int stock) {
        return catalogProduct(ProductStatus.ACTIVE, true, price, salePrice, stock, true);
    }

    private CatalogFixture draftProduct(int stock) {
        return catalogProduct(ProductStatus.DRAFT, true, new BigDecimal("10000.00"), null, stock, true);
    }

    private CatalogFixture inactiveVariant(int stock) {
        return catalogProduct(ProductStatus.ACTIVE, false, new BigDecimal("10000.00"), null, stock, true);
    }

    private CatalogFixture catalogProduct(
            ProductStatus status,
            boolean variantActive,
            BigDecimal price,
            BigDecimal salePrice,
            int stock,
            boolean withImage) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Cart Cat " + suffix);
        category.setSlug("cart-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("Cart Brand " + suffix);
        brand.setSlug("cart-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);

        Product product = new Product();
        String productName = "Cart Shirt " + suffix;
        product.setName(productName);
        product.setSlug("cart-shirt-" + suffix);
        product.setPrice(price);
        product.setSalePrice(salePrice);
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(status);
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
        String sku = "CART-" + suffix;
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setSku(sku);
        variant.setStockQuantity(stock);
        variant.setActive(variantActive);
        productVariantRepository.saveAndFlush(variant);

        String imageUrl = null;
        if (withImage) {
            imageUrl = "https://cdn.example/cart-" + suffix + ".jpg";
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(imageUrl);
            image.setIsPrimary(true);
            image.setSortOrder(1);
            productImageRepository.saveAndFlush(image);
        }

        return new CatalogFixture(product.getId(), variant.getId(), sku, productName, imageUrl, sizeName, colorName);
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "cart_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Cart " + roleName.name());
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
