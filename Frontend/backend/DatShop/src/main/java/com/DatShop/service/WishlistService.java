package com.DatShop.service;

import com.DatShop.dto.wishlist.AddWishlistItemRequest;
import com.DatShop.dto.wishlist.WishlistItemResponse;
import com.DatShop.dto.wishlist.WishlistResponse;
import com.DatShop.dto.wishlist.WishlistStatusResponse;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.WishlistMapper;
import com.DatShop.model.Product;
import com.DatShop.model.User;
import com.DatShop.model.Wishlist;
import com.DatShop.model.WishlistItem;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.WishlistItemRepository;
import com.DatShop.repository.WishlistRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishlistService {

    private static final String UNAVAILABLE = "This product is not available";

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CurrentUserService currentUserService;

    public WishlistService(
            WishlistRepository wishlistRepository,
            WishlistItemRepository wishlistItemRepository,
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            ProductVariantRepository productVariantRepository,
            CurrentUserService currentUserService) {
        this.wishlistRepository = wishlistRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.productVariantRepository = productVariantRepository;
        this.currentUserService = currentUserService;
    }

    public WishlistResponse getWishlist(Authentication authentication) {
        User user = currentUserService.requireUser(authentication);
        return wishlistRepository.findByUser_Id(user.getId())
                .map(this::toResponse)
                .orElseGet(WishlistMapper::unpersistedEmpty);
    }

    public WishlistStatusResponse isWishlisted(Authentication authentication, Long productId) {
        User user = currentUserService.requireUser(authentication);
        boolean wishlisted = wishlistRepository.findByUser_Id(user.getId())
                .map(wishlist -> wishlistItemRepository.existsByWishlist_IdAndProduct_Id(wishlist.getId(), productId))
                .orElse(false);
        return new WishlistStatusResponse(productId, wishlisted);
    }

    @Transactional
    public WishlistResponse addProduct(Authentication authentication, AddWishlistItemRequest request) {
        User user = currentUserService.lockCurrentUser(authentication);
        Product product = requireWishlistableProduct(request.productId());
        Wishlist wishlist = wishlistRepository.findByUser_Id(user.getId()).orElse(null);
        if (wishlist != null
                && wishlistItemRepository.existsByWishlist_IdAndProduct_Id(wishlist.getId(), product.getId())) {
            return toResponse(wishlist);
        }
        if (wishlist == null) {
            wishlist = persistWishlist(user);
        }
        WishlistItem item = new WishlistItem();
        item.setWishlist(wishlist);
        item.setProduct(product);
        wishlistItemRepository.saveAndFlush(item);
        return toResponse(wishlist);
    }

    @Transactional
    public void removeProduct(Authentication authentication, Long productId) {
        User user = currentUserService.lockCurrentUser(authentication);
        wishlistRepository.findByUser_Id(user.getId()).ifPresent(wishlist ->
                wishlistItemRepository.findByWishlist_IdAndProduct_Id(wishlist.getId(), productId)
                        .ifPresent(item -> {
                            wishlistItemRepository.delete(item);
                            wishlistItemRepository.flush();
                        }));
    }

    @Transactional
    public void clear(Authentication authentication) {
        User user = currentUserService.lockCurrentUser(authentication);
        wishlistRepository.findByUser_Id(user.getId()).ifPresent(wishlist -> {
            List<WishlistItem> items =
                    wishlistItemRepository.findByWishlist_IdOrderByCreatedAtDescIdDesc(wishlist.getId());
            if (!items.isEmpty()) {
                wishlistItemRepository.deleteAll(items);
                wishlistItemRepository.flush();
            }
        });
    }

    private Wishlist persistWishlist(User user) {
        try {
            Wishlist wishlist = new Wishlist();
            wishlist.setUser(user);
            return wishlistRepository.saveAndFlush(wishlist);
        } catch (DataIntegrityViolationException ex) {
            return wishlistRepository.findByUser_Id(user.getId()).orElseThrow(() -> ex);
        }
    }

    private Product requireWishlistableProduct(Long productId) {
        Product product = productRepository.findWithCatalogById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!CatalogAvailability.isPubliclyVisible(product)) {
            throw new BusinessRuleException(UNAVAILABLE);
        }
        return product;
    }

    private WishlistResponse toResponse(Wishlist wishlist) {
        List<WishlistItem> items =
                wishlistItemRepository.findByWishlist_IdOrderByCreatedAtDescIdDesc(wishlist.getId());
        if (items.isEmpty()) {
            return WishlistMapper.empty(wishlist.getId());
        }
        List<Long> productIds = items.stream().map(item -> item.getProduct().getId()).toList();
        Map<Long, String> images = primaryImageUrls(productIds);
        Set<Long> inStockIds = inStockIds(productIds);
        List<WishlistItemResponse> responses = items.stream()
                .map(item -> WishlistMapper.toItemResponse(
                        item,
                        images.get(item.getProduct().getId()),
                        CatalogAvailability.isPubliclyVisible(item.getProduct()),
                        inStockIds.contains(item.getProduct().getId())))
                .toList();
        return WishlistMapper.toResponse(wishlist, responses);
    }

    private Map<Long, String> primaryImageUrls(List<Long> productIds) {
        return PrimaryImages.urlsByProductId(productImageRepository, productIds);
    }

    private Set<Long> inStockIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(productVariantRepository.findInStockProductIds(productIds));
    }
}
