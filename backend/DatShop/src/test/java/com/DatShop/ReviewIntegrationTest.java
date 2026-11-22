package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.order.CheckoutRequest;
import com.DatShop.dto.order.OrderStatusUpdateRequest;
import com.DatShop.dto.review.ReviewCreateRequest;
import com.DatShop.dto.review.ReviewStatusUpdateRequest;
import com.DatShop.dto.review.ReviewUpdateRequest;
import com.DatShop.dto.wishlist.AddWishlistItemRequest;
import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Color;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Product;
import com.DatShop.model.ProductImage;
import com.DatShop.model.ProductReview;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.ReviewStatus;
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
import com.DatShop.repository.OrderRepository;
import com.DatShop.repository.PaymentRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductReviewRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.SizeRepository;
import com.DatShop.repository.UserRepository;
import com.DatShop.repository.VoucherRepository;
import com.DatShop.repository.WishlistItemRepository;
import com.DatShop.repository.WishlistRepository;
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
class ReviewIntegrationTest {

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
    private ProductReviewRepository productReviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Test
    void reviewRequiresDeliveredPurchase() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        PreparedOrder order = checkoutOrder(customer, "90000.00", 6, 1, null);
        assertCreateRejected(customer.token, order.productId);

        patchOrder(staff.token, order.orderNumber, OrderStatus.CONFIRMED);
        assertCreateRejected(customer.token, order.productId);
        patchOrder(staff.token, order.orderNumber, OrderStatus.PROCESSING);
        assertCreateRejected(customer.token, order.productId);
        patchOrder(staff.token, order.orderNumber, OrderStatus.SHIPPING);
        assertCreateRejected(customer.token, order.productId);
        patchOrder(staff.token, order.orderNumber, OrderStatus.DELIVERED);
        JsonNode created = createReview(customer.token, order.productId, 5, "Great fit", 201);
        assertThat(created.get("rating").asInt()).isEqualTo(5);
        assertThat(created.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(created.get("product").get("id").asLong()).isEqualTo(order.productId);

        PreparedOrder cancelled = checkoutOrder(customer, "70000.00", 4, 1, null);
        mockMvc.perform(patch("/api/orders/" + cancelled.orderNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk());
        assertCreateRejected(customer.token, cancelled.productId);
    }

    @Test
    void createValidatesRatingProductAndDuplicate() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewCreateRequest(1L, 5, "x"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewCreateRequest(1L, 5, "x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewCreateRequest(1L, 5, "x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/me").header("Authorization", bearer(staff.token)))
                .andExpect(status().isForbidden());

        PreparedOrder order = deliveredOrder(customer, staff, "80000.00", 5, 1, null);
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + order.productId + ",\"rating\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + order.productId + ",\"rating\":6}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewCreateRequest(9_999_999_999L, 5, "x"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + order.productId + ",\"rating\":5,\"userId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: userId"));
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + order.productId + ",\"rating\":5,\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: status"));
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + order.productId + ",\"rating\":5,\"orderId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: orderId"));

        Product product = productRepository.findById(order.productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        JsonNode created = createReview(customer.token, order.productId, 5, "Still good", 201);
        assertThat(created.get("rating").asInt()).isEqualTo(5);
        createReview(customer.token, order.productId, 4, "again", 409);
        assertThat(productReviewRepository.existsByUser_IdAndProduct_Id(customer.userId, order.productId)).isTrue();
    }

    @Test
    void ownershipUpdateAndHiddenStayHidden() throws Exception {
        Session owner = session(RoleName.CUSTOMER);
        Session other = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        PreparedOrder owned = deliveredOrder(owner, staff, "65000.00", 4, 1, null);
        PreparedOrder others = deliveredOrder(other, staff, "66000.00", 4, 1, null);
        JsonNode mine = createReview(owner.token, owned.productId, 5, "Mine", 201);
        JsonNode theirs = createReview(other.token, others.productId, 3, "Theirs", 201);
        long mineId = mine.get("id").asLong();
        long theirsId = theirs.get("id").asLong();

        mockMvc.perform(put("/api/reviews/" + theirsId)
                        .header("Authorization", bearer(owner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewUpdateRequest(1, "hack"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/reviews/" + theirsId).header("Authorization", bearer(owner.token)))
                .andExpect(status().isNotFound());

        JsonNode me = read(mockMvc.perform(get("/api/reviews/me").header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(me.get("content")).hasSize(1);
        assertThat(me.get("content").get(0).get("id").asLong()).isEqualTo(mineId);
        assertThat(me.get("content").get(0).get("product").get("primaryImageUrl").asText()).isEqualTo(owned.imageUrl);

        mockMvc.perform(put("/api/reviews/" + mineId)
                        .header("Authorization", bearer(owner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"updated\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: status"));
        JsonNode updated = read(mockMvc.perform(put("/api/reviews/" + mineId)
                        .header("Authorization", bearer(owner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewUpdateRequest(4, "  updated comment  "))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updated.get("rating").asInt()).isEqualTo(4);
        assertThat(updated.get("comment").asText()).isEqualTo("updated comment");
        assertThat(updated.get("status").asText()).isEqualTo("PUBLISHED");

        patchReviewStatus(staff.token, mineId, ReviewStatus.HIDDEN);
        JsonNode stillHidden = read(mockMvc.perform(put("/api/reviews/" + mineId)
                        .header("Authorization", bearer(owner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewUpdateRequest(2, "still hidden"))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(stillHidden.get("status").asText()).isEqualTo("HIDDEN");
        assertThat(stillHidden.get("rating").asInt()).isEqualTo(2);
        JsonNode meHidden = read(mockMvc.perform(get("/api/reviews/me").header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(meHidden.get("content").get(0).get("status").asText()).isEqualTo("HIDDEN");
    }

    @Test
    void publicReviewsAggregateAndHideFromAverage() throws Exception {
        Session staff = session(RoleName.STAFF);
        Session a = session(RoleName.CUSTOMER);
        Session b = session(RoleName.CUSTOMER);
        Session c = session(RoleName.CUSTOMER);
        CatalogFixture catalog = publishedProduct(new BigDecimal("50000.00"), 5);
        PreparedOrder orderA = checkoutExisting(a, catalog, 1, null);
        PreparedOrder orderB = checkoutExisting(b, catalog, 1, null);
        PreparedOrder orderC = checkoutExisting(c, catalog, 1, null);
        deliver(staff.token, orderA.orderNumber);
        deliver(staff.token, orderB.orderNumber);
        deliver(staff.token, orderC.orderNumber);
        long fiveStarId = createReview(a.token, catalog.productId, 5, "five", 201).get("id").asLong();
        createReview(b.token, catalog.productId, 4, "four-b", 201);
        createReview(c.token, catalog.productId, 4, "four-c", 201);

        JsonNode published = read(mockMvc.perform(get("/api/products/" + catalog.slug + "/reviews"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(published.get("reviewCount").asLong()).isEqualTo(3);
        assertThat(published.get("averageRating").decimalValue()).isEqualByComparingTo("4.33");
        assertThat(published.get("content")).hasSize(3);

        JsonNode page = read(mockMvc.perform(get("/api/products/" + catalog.slug + "/reviews?page=0&size=1"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("size").asInt()).isEqualTo(1);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("reviewCount").asLong()).isEqualTo(3);
        assertThat(page.get("averageRating").decimalValue()).isEqualByComparingTo("4.33");
        assertThat(page.get("first").asBoolean()).isTrue();
        assertThat(page.get("last").asBoolean()).isFalse();

        patchReviewStatus(staff.token, fiveStarId, ReviewStatus.HIDDEN);
        JsonNode afterHide = read(mockMvc.perform(get("/api/products/" + catalog.slug + "/reviews"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(afterHide.get("reviewCount").asLong()).isEqualTo(2);
        assertThat(afterHide.get("averageRating").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(afterHide.get("content")).hasSize(2);
        for (JsonNode review : afterHide.get("content")) {
            assertThat(review.get("rating").asInt()).isEqualTo(4);
            assertThat(review.get("reviewerName").asText()).isNotBlank();
            assertThat(review.has("email")).isFalse();
            assertThat(review.has("userId")).isFalse();
        }

        mockMvc.perform(get("/api/products/unknown-review-slug/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));
        mockMvc.perform(get("/api/products/" + catalog.slug + "/reviews?size=101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/products/" + catalog.slug + "/reviews?sort=email,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicReviewsRequirePubliclyVisibleProduct() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        PreparedOrder order = deliveredOrder(customer, staff, "52000.00", 3, 1, null);
        JsonNode created = createReview(customer.token, order.productId, 5, "visible only while public", 201);
        long reviewId = created.get("id").asLong();

        mockMvc.perform(get("/api/products/" + order.slug + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1));

        Product product = productRepository.findWithCatalogById(order.productId).orElseThrow();
        product.setStatus(ProductStatus.DRAFT);
        productRepository.saveAndFlush(product);
        assertPublicNotFound(order.slug);

        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        assertPublicNotFound(order.slug);

        product.setStatus(ProductStatus.ACTIVE);
        productRepository.saveAndFlush(product);
        product.getCategory().setStatus(CatalogStatus.INACTIVE);
        categoryRepository.saveAndFlush(product.getCategory());
        assertPublicNotFound(order.slug);

        product.getCategory().setStatus(CatalogStatus.ACTIVE);
        categoryRepository.saveAndFlush(product.getCategory());
        product.getBrand().setStatus(CatalogStatus.INACTIVE);
        brandRepository.saveAndFlush(product.getBrand());
        assertPublicNotFound(order.slug);

        mockMvc.perform(get("/api/reviews/me").header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(reviewId));
        mockMvc.perform(get("/api/admin/reviews/" + reviewId).header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reviewId))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(put("/api/reviews/" + reviewId)
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewUpdateRequest(4, "still own review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4));

        product.getBrand().setStatus(CatalogStatus.ACTIVE);
        brandRepository.saveAndFlush(product.getBrand());
        mockMvc.perform(get("/api/products/" + order.slug + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.content[0].rating").value(4));
        mockMvc.perform(get("/api/products/unknown-review-slug/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void oneReviewPerProductEvenAfterMultipleDeliveriesAndDeleteAllowsRecreate() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        CatalogFixture catalog = publishedProduct(new BigDecimal("45000.00"), 4);
        PreparedOrder first = checkoutExisting(customer, catalog, 1, null);
        deliver(staff.token, first.orderNumber);
        PreparedOrder second = checkoutExisting(customer, catalog, 1, null);
        deliver(staff.token, second.orderNumber);
        JsonNode created = createReview(customer.token, catalog.productId, 5, "first", 201);
        createReview(customer.token, catalog.productId, 4, "second", 409);

        mockMvc.perform(delete("/api/reviews/" + created.get("id").asLong())
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isNoContent());
        JsonNode publicEmpty = read(mockMvc.perform(get("/api/products/" + catalog.slug + "/reviews"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(publicEmpty.get("reviewCount").asLong()).isZero();
        assertThat(publicEmpty.get("averageRating").decimalValue()).isEqualByComparingTo("0.00");
        createReview(customer.token, catalog.productId, 4, "recreated", 201);
    }

    @Test
    void moderationSecurityUniqueConstraintAndNoSideEffects() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        mockMvc.perform(get("/api/admin/reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/reviews").header("Authorization", bearer(customer.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reviews").header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/reviews").header("Authorization", bearer(admin.token)))
                .andExpect(status().isOk());

        PreparedOrder order = deliveredOrder(customer, staff, "55000.00", 8, 1, "REV10");
        mockMvc.perform(post("/api/wishlist/items")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddWishlistItemRequest(order.productId))))
                .andExpect(status().isOk());
        addToCart(customer.token, order.variantId, 1);

        int stockBefore = productVariantRepository.findById(order.variantId).orElseThrow().getStockQuantity();
        int voucherBefore = voucherRepository.findByCodeIgnoreCase("REV10").orElseThrow().getUsedCount();
        OrderStatus orderBefore = orderRepository.findById(order.orderId).orElseThrow().getStatus();
        long paymentsBefore = paymentRepository.count();
        int cartBefore = cartItemCount(customer.userId);
        int wishlistBefore = wishlistItemCount(customer.userId);

        JsonNode created = createReview(customer.token, order.productId, 5, "side-effect check", 201);
        long reviewId = created.get("id").asLong();
        mockMvc.perform(get("/api/admin/reviews/" + reviewId).header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewer.username").exists())
                .andExpect(jsonPath("$.reviewer.fullName").exists())
                .andExpect(jsonPath("$.reviewer.passwordHash").doesNotExist());
        JsonNode hidden = patchReviewStatus(admin.token, reviewId, ReviewStatus.HIDDEN);
        assertThat(hidden.get("status").asText()).isEqualTo("HIDDEN");
        mockMvc.perform(get("/api/admin/reviews")
                        .header("Authorization", bearer(staff.token))
                        .param("status", "HIDDEN")
                        .param("rating", "5")
                        .param("productId", String.valueOf(order.productId))
                        .param("q", "Rev Tee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(reviewId));
        mockMvc.perform(patch("/api/admin/reviews/" + reviewId + "/status")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\",\"orderStatus\":\"DELIVERED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: orderStatus"));
        mockMvc.perform(get("/api/admin/reviews?status=NOT_A_STATUS").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/reviews?size=101").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());

        assertThat(productVariantRepository.findById(order.variantId).orElseThrow().getStockQuantity()).isEqualTo(stockBefore);
        assertThat(voucherRepository.findByCodeIgnoreCase("REV10").orElseThrow().getUsedCount()).isEqualTo(voucherBefore);
        assertThat(orderRepository.findById(order.orderId).orElseThrow().getStatus()).isEqualTo(orderBefore);
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
        assertThat(cartItemCount(customer.userId)).isEqualTo(cartBefore);
        assertThat(wishlistItemCount(customer.userId)).isEqualTo(wishlistBefore);

        ProductReview duplicate = new ProductReview();
        duplicate.setUser(userRepository.findById(customer.userId).orElseThrow());
        duplicate.setProduct(productRepository.findById(order.productId).orElseThrow());
        duplicate.setRating(1);
        duplicate.setStatus(ReviewStatus.PUBLISHED);
        assertThatThrownBy(() -> productReviewRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertPublicNotFound(String slug) throws Exception {
        mockMvc.perform(get("/api/products/" + slug + "/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));
    }

    private void assertCreateRejected(String token, long productId) throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewCreateRequest(productId, 5, "too soon"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Product can only be reviewed after delivery"));
    }

    private JsonNode createReview(String token, long productId, int rating, String comment, int status) throws Exception {
        return read(mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewCreateRequest(productId, rating, comment))))
                .andExpect(status().is(status))
                .andReturn());
    }

    private JsonNode patchReviewStatus(String token, long reviewId, ReviewStatus status) throws Exception {
        return read(mockMvc.perform(patch("/api/admin/reviews/" + reviewId + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewStatusUpdateRequest(status))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private PreparedOrder deliveredOrder(
            Session customer, Session staff, String price, int stock, int quantity, String voucherCode) throws Exception {
        PreparedOrder order = checkoutOrder(customer, price, stock, quantity, voucherCode);
        deliver(staff.token, order.orderNumber);
        return order;
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

    private PreparedOrder checkoutOrder(
            Session customer, String price, int stock, int quantity, String voucherCode) throws Exception {
        CatalogFixture catalog = publishedProduct(new BigDecimal(price), stock);
        return checkoutExisting(customer, catalog, quantity, voucherCode);
    }

    private PreparedOrder checkoutExisting(
            Session customer, CatalogFixture catalog, int quantity, String voucherCode) throws Exception {
        long addressId = createAddress(customer.token);
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
                catalog.productId,
                catalog.slug,
                catalog.imageUrl);
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
        if (voucherRepository.existsByCodeIgnoreCase(code)) {
            return;
        }
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

    private int cartItemCount(long userId) {
        return cartRepository.findByUser_Id(userId)
                .map(cart -> cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cart.getId()).size())
                .orElse(0);
    }

    private int wishlistItemCount(long userId) {
        return wishlistRepository.findByUser_Id(userId)
                .map(wishlist -> wishlistItemRepository.findByWishlist_IdOrderByCreatedAtDescIdDesc(wishlist.getId()).size())
                .orElse(0);
    }

    private CatalogFixture publishedProduct(BigDecimal price, int stock) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Rev Cat " + suffix);
        category.setSlug("rev-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);
        Brand brand = new Brand();
        brand.setName("Rev Brand " + suffix);
        brand.setSlug("rev-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);
        Product product = new Product();
        product.setName("Rev Tee " + suffix);
        product.setSlug("rev-tee-" + suffix);
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
        variant.setSku("REV-" + suffix);
        variant.setStockQuantity(stock);
        variant.setActive(true);
        productVariantRepository.saveAndFlush(variant);
        String imageUrl = "https://cdn.example/rev-" + suffix + ".jpg";
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(imageUrl);
        image.setIsPrimary(true);
        image.setSortOrder(1);
        productImageRepository.saveAndFlush(image);
        return new CatalogFixture(product.getId(), variant.getId(), product.getSlug(), imageUrl);
    }

    private Session session(RoleName roleName) throws Exception {
        String username = "rev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Rev " + roleName.name());
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
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(long userId, String token) {
    }

    private record CatalogFixture(long productId, long variantId, String slug, String imageUrl) {
    }

    private record PreparedOrder(
            long orderId, String orderNumber, long variantId, long productId, String slug, String imageUrl) {
    }
}
