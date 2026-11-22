package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.admin.CustomerStatusUpdateRequest;
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
class CustomerManagementIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Test
    void staffIsReadOnlyAndOnlyCustomerRoleAppears() throws Exception {
        String token = unique();
        Session customerA = session(RoleName.CUSTOMER, "cmA_" + token, "Alpha " + token, "0901111" + token.substring(0, 4));
        Session customerB = session(RoleName.CUSTOMER, "cmB_" + token, "Beta " + token, null);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        Session otherCustomer = session(RoleName.CUSTOMER);

        mockMvc.perform(get("/api/admin/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(otherCustomer.token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/customers").header("Authorization", bearer(admin.token)))
                .andExpect(status().isOk());

        JsonNode listed = read(mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(listed.get("content")).hasSize(2);
        assertThat(texts(listed.get("content"), "username")).containsExactlyInAnyOrder(customerA.username, customerB.username);
        assertThat(longs(listed.get("content"), "id")).doesNotContain(staff.userId, admin.userId);

        mockMvc.perform(get("/api/admin/customers/" + staff.userId).header("Authorization", bearer(admin.token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
        mockMvc.perform(get("/api/admin/customers/" + admin.userId).header("Authorization", bearer(staff.token)))
                .andExpect(status().isNotFound());
        UserStatus staffBefore = userRepository.findById(staff.userId).orElseThrow().getStatus();
        mockMvc.perform(patch("/api/admin/customers/" + staff.userId + "/status")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CustomerStatusUpdateRequest(UserStatus.BLOCKED))))
                .andExpect(status().isNotFound());
        assertThat(userRepository.findById(staff.userId).orElseThrow().getStatus()).isEqualTo(staffBefore);

        mockMvc.perform(patch("/api/admin/customers/" + customerA.userId + "/status")
                        .header("Authorization", bearer(staff.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CustomerStatusUpdateRequest(UserStatus.BLOCKED))))
                .andExpect(status().isForbidden());
        JsonNode blocked = read(mockMvc.perform(patch("/api/admin/customers/" + customerA.userId + "/status")
                        .header("Authorization", bearer(admin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CustomerStatusUpdateRequest(UserStatus.BLOCKED))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(blocked.get("status").asText()).isEqualTo("BLOCKED");
        assertThat(blocked.has("passwordHash")).isFalse();
        assertThat(userRepository.findById(customerA.userId).orElseThrow().getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    void searchAndStatusFiltersAreCaseInsensitive() throws Exception {
        String token = unique();
        Session active = session(RoleName.CUSTOMER, "cmAct_" + token, "Full " + token, "0902222" + token.substring(0, 4));
        Session blocked = session(RoleName.CUSTOMER, "cmBlk_" + token, "Blocked " + token, null);
        Session inactive = session(RoleName.CUSTOMER, "cmIna_" + token, "Inactive " + token, null);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);
        patchStatus(admin.token, blocked.userId, UserStatus.BLOCKED);
        patchStatus(admin.token, inactive.userId, UserStatus.INACTIVE);

        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", active.username.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(active.userId));
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", active.username.toUpperCase() + "@EXAMPLE.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(active.userId));
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", "full " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(active.userId));
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", "0902222" + token.substring(0, 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(active.userId));
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", "no-such-customer-" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(active.userId))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", token)
                        .param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(blocked.userId));
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(staff.token))
                        .param("q", token)
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(inactive.userId));
        mockMvc.perform(get("/api/admin/customers?status=NOT_A_STATUS").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/customers?size=101").header("Authorization", bearer(staff.token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailMetricsAndOrderHistoryUseSnapshots() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session other = session(RoleName.CUSTOMER);
        Session staff = session(RoleName.STAFF);
        Session admin = session(RoleName.ADMIN);

        PreparedOrder pending = checkoutOrder(customer, "100.00", 5, 1);
        PreparedOrder confirmed = checkoutOrder(customer, "200.00", 5, 1);
        PreparedOrder delivered = checkoutOrder(customer, "300.00", 5, 1);
        PreparedOrder cancelled = checkoutOrder(customer, "400.00", 5, 1);
        PreparedOrder otherOrder = checkoutOrder(other, "999.00", 3, 1);
        patchOrder(staff.token, confirmed.orderNumber, OrderStatus.CONFIRMED);
        deliver(staff.token, delivered.orderNumber);
        mockMvc.perform(patch("/api/orders/" + cancelled.orderNumber + "/cancel")
                        .header("Authorization", bearer(customer.token)))
                .andExpect(status().isOk());

        JsonNode detail = read(mockMvc.perform(get("/api/admin/customers/" + customer.userId)
                        .header("Authorization", bearer(staff.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.get("id").asLong()).isEqualTo(customer.userId);
        assertThat(detail.get("username").asText()).isEqualTo(customer.username);
        assertThat(detail.get("orderCount").asLong()).isEqualTo(4);
        assertThat(detail.get("totalSpent").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(detail.get("addresses")).hasSize(4);
        assertThat(detail.has("passwordHash")).isFalse();
        assertThat(detail.has("password")).isFalse();
        assertThat(detail.get("latestOrders")).isNotEmpty();

        JsonNode history = read(mockMvc.perform(get("/api/admin/customers/" + customer.userId + "/orders")
                        .header("Authorization", bearer(admin.token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(history.get("totalElements").asLong()).isEqualTo(4);
        assertThat(texts(history.get("content"), "orderNumber")).containsExactly(
                cancelled.orderNumber, delivered.orderNumber, confirmed.orderNumber, pending.orderNumber);
        assertThat(texts(history.get("content"), "orderNumber")).doesNotContain(otherOrder.orderNumber);

        JsonNode page = read(mockMvc.perform(get("/api/admin/customers/" + customer.userId + "/orders")
                        .header("Authorization", bearer(staff.token))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("orderNumber").asText()).isEqualTo(cancelled.orderNumber);
        assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        assertThat(page.get("first").asBoolean()).isTrue();
        assertThat(page.get("last").asBoolean()).isFalse();

        mockMvc.perform(get("/api/admin/customers/" + customer.userId + "/orders")
                        .header("Authorization", bearer(staff.token))
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value(pending.orderNumber))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void statusChangeStopsOldJwtAndDoesNotTouchCommerceData() throws Exception {
        Session customer = session(RoleName.CUSTOMER);
        Session inactiveCustomer = session(RoleName.CUSTOMER);
        Session realAdmin = session(RoleName.ADMIN);
        PreparedOrder order = checkoutOrder(customer, "150.00", 4, 1);
        int stockBefore = productVariantRepository.findById(order.variantId).orElseThrow().getStockQuantity();
        OrderStatus orderBefore = orderRepository.findById(order.orderId).orElseThrow().getStatus();
        long paymentsBefore = paymentRepository.count();
        long reviewsBefore = productReviewRepository.count();

        mockMvc.perform(patch("/api/admin/customers/" + customer.userId + "/status")
                        .header("Authorization", bearer(realAdmin.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\",\"username\":\"hacked\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: username"));

        patchStatus(realAdmin.token, customer.userId, UserStatus.BLOCKED);
        mockMvc.perform(get("/api/profile").header("Authorization", bearer(customer.token)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", customer.username, "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        patchStatus(realAdmin.token, inactiveCustomer.userId, UserStatus.INACTIVE);
        mockMvc.perform(get("/api/profile").header("Authorization", bearer(inactiveCustomer.token)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", inactiveCustomer.username, "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        patchStatus(realAdmin.token, customer.userId, UserStatus.ACTIVE);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", customer.username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        assertThat(productVariantRepository.findById(order.variantId).orElseThrow().getStockQuantity()).isEqualTo(stockBefore);
        assertThat(orderRepository.findById(order.orderId).orElseThrow().getStatus()).isEqualTo(orderBefore);
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
        assertThat(productReviewRepository.count()).isEqualTo(reviewsBefore);
        assertThat(userRepository.findById(customer.userId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private JsonNode patchStatus(String adminToken, long customerId, UserStatus status) throws Exception {
        return read(mockMvc.perform(patch("/api/admin/customers/" + customerId + "/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CustomerStatusUpdateRequest(status))))
                .andExpect(status().isOk())
                .andReturn());
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

    private PreparedOrder checkoutOrder(Session customer, String price, int stock, int quantity) throws Exception {
        long addressId = createAddress(customer.token);
        CatalogFixture catalog = publishedProduct(new BigDecimal(price), stock);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(catalog.variantId, quantity))))
                .andExpect(status().isOk());
        JsonNode created = read(mockMvc.perform(post("/api/checkout")
                        .header("Authorization", bearer(customer.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest(addressId, null, null))))
                .andExpect(status().isCreated())
                .andReturn());
        return new PreparedOrder(created.get("id").asLong(), created.get("orderNumber").asText(), catalog.variantId);
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
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Cm Cat " + suffix);
        category.setSlug("cm-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);
        Brand brand = new Brand();
        brand.setName("Cm Brand " + suffix);
        brand.setSlug("cm-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);
        Product product = new Product();
        product.setName("Cm Tee " + suffix);
        product.setSlug("cm-tee-" + suffix);
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
        variant.setSku("CM-" + suffix);
        variant.setStockQuantity(stock);
        variant.setActive(true);
        productVariantRepository.saveAndFlush(variant);
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl("https://cdn.example/cm-" + suffix + ".jpg");
        image.setIsPrimary(true);
        image.setSortOrder(1);
        productImageRepository.saveAndFlush(image);
        return new CatalogFixture(variant.getId());
    }

    private Session session(RoleName roleName) throws Exception {
        return session(roleName, "cm_" + unique(), "Cm " + roleName.name(), null);
    }

    private Session session(RoleName roleName, String username, String fullName, String phone) throws Exception {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(fullName);
        user.setPhoneNumber(phone);
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
        return new Session(saved.getId(), username, token);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static java.util.List<String> texts(JsonNode array, String field) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (JsonNode node : array) {
            values.add(node.get(field).asText());
        }
        return values;
    }

    private static java.util.List<Long> longs(JsonNode array, String field) {
        java.util.List<Long> values = new java.util.ArrayList<>();
        for (JsonNode node : array) {
            values.add(node.get(field).asLong());
        }
        return values;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Session(long userId, String username, String token) {
    }

    private record CatalogFixture(long variantId) {
    }

    private record PreparedOrder(long orderId, String orderNumber, long variantId) {
    }
}
