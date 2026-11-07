package com.DatShop;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.DatShop.model.Brand;
import com.DatShop.model.Cart;
import com.DatShop.model.CartItem;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Product;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.repository.BrandRepository;
import com.DatShop.repository.CartItemRepository;
import com.DatShop.repository.CartRepository;
import com.DatShop.repository.CategoryRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CartPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Test
    void rejectsSecondCartForSameUser() {
        User user = savedUser();
        Cart first = new Cart();
        first.setUser(user);
        cartRepository.saveAndFlush(first);

        Cart second = new Cart();
        second.setUser(user);
        assertThatThrownBy(() -> cartRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateVariantInSameCart() {
        User user = savedUser();
        Cart cart = new Cart();
        cart.setUser(user);
        cartRepository.saveAndFlush(cart);
        ProductVariant variant = savedVariant();

        CartItem first = new CartItem();
        first.setCart(cart);
        first.setProductVariant(variant);
        first.setQuantity(1);
        cartItemRepository.saveAndFlush(first);

        CartItem duplicate = new CartItem();
        duplicate.setCart(cart);
        duplicate.setProductVariant(variant);
        duplicate.setQuantity(2);
        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User savedUser() {
        String username = "cartp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$placeholder.hash.for.testing.only");
        user.setFullName("Cart Persistence");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(roleRepository.findByName(RoleName.CUSTOMER).orElseThrow()));
        return userRepository.saveAndFlush(user);
    }

    private ProductVariant savedVariant() {
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
        product.setName("Cart Product " + suffix);
        product.setSlug("cart-product-" + suffix);
        product.setPrice(new BigDecimal("100000.00"));
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("CART-SKU-" + suffix);
        variant.setStockQuantity(5);
        variant.setActive(true);
        return productVariantRepository.saveAndFlush(variant);
    }
}
