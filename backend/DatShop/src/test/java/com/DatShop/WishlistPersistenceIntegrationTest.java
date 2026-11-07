package com.DatShop;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Product;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.model.Wishlist;
import com.DatShop.model.WishlistItem;
import com.DatShop.repository.BrandRepository;
import com.DatShop.repository.CategoryRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.UserRepository;
import com.DatShop.repository.WishlistItemRepository;
import com.DatShop.repository.WishlistRepository;
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
class WishlistPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void rejectsSecondWishlistForSameUser() {
        User user = savedUser();
        Wishlist first = new Wishlist();
        first.setUser(user);
        wishlistRepository.saveAndFlush(first);

        Wishlist second = new Wishlist();
        second.setUser(user);
        assertThatThrownBy(() -> wishlistRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateProductInSameWishlist() {
        User user = savedUser();
        Wishlist wishlist = new Wishlist();
        wishlist.setUser(user);
        wishlistRepository.saveAndFlush(wishlist);
        Product product = savedProduct();

        WishlistItem first = new WishlistItem();
        first.setWishlist(wishlist);
        first.setProduct(product);
        wishlistItemRepository.saveAndFlush(first);

        WishlistItem duplicate = new WishlistItem();
        duplicate.setWishlist(wishlist);
        duplicate.setProduct(product);
        assertThatThrownBy(() -> wishlistItemRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User savedUser() {
        String username = "wishp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$placeholder.hash.for.testing.only");
        user.setFullName("Wishlist Persistence");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(roleRepository.findByName(RoleName.CUSTOMER).orElseThrow()));
        return userRepository.saveAndFlush(user);
    }

    private Product savedProduct() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Category category = new Category();
        category.setName("Wish Cat " + suffix);
        category.setSlug("wish-cat-" + suffix);
        category.setStatus(CatalogStatus.ACTIVE);
        categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("Wish Brand " + suffix);
        brand.setSlug("wish-brand-" + suffix);
        brand.setStatus(CatalogStatus.ACTIVE);
        brandRepository.save(brand);

        Product product = new Product();
        product.setName("Wish Product " + suffix);
        product.setSlug("wish-product-" + suffix);
        product.setPrice(new BigDecimal("100000.00"));
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.saveAndFlush(product);
    }
}
