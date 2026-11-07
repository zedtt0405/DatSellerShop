package com.DatShop.service;

import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.cart.CartItemResponse;
import com.DatShop.dto.cart.CartResponse;
import com.DatShop.dto.cart.UpdateCartItemRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.CartMapper;
import com.DatShop.model.Cart;
import com.DatShop.model.CartItem;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.User;
import com.DatShop.repository.CartItemRepository;
import com.DatShop.repository.CartRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductVariantRepository;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartService {

    private static final String UNAVAILABLE = CatalogAvailability.UNAVAILABLE;
    private static final String OVER_STOCK = CatalogAvailability.OVER_STOCK;
    private static final String ITEM_NOT_FOUND = "Cart item not found";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final CurrentUserService currentUserService;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductVariantRepository productVariantRepository,
            ProductImageRepository productImageRepository,
            CurrentUserService currentUserService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.currentUserService = currentUserService;
    }

    public CartResponse getCart(Authentication authentication) {
        User user = currentUserService.requireUser(authentication);
        return cartRepository.findByUser_Id(user.getId())
                .map(this::toResponse)
                .orElseGet(CartMapper::unpersistedEmpty);
    }

    @Transactional
    public CartResponse addItem(Authentication authentication, AddCartItemRequest request) {
        User user = currentUserService.lockCurrentUser(authentication);
        ProductVariant variant = requirePurchasableVariant(request.variantId());
        Cart cart = cartRepository.findByUser_Id(user.getId()).orElse(null);
        CartItem item = cart == null
                ? null
                : cartItemRepository.findByCart_IdAndProductVariant_Id(cart.getId(), variant.getId()).orElse(null);
        int current = item == null ? 0 : item.getQuantity();
        int next = addQuantities(current, request.quantity());
        assertWithinStock(variant, next);
        if (cart == null) {
            cart = persistCart(user);
        }
        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProductVariant(variant);
            item.setQuantity(next);
            cartItemRepository.save(item);
        } else {
            item.setQuantity(next);
        }
        cartItemRepository.flush();
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Authentication authentication, Long itemId, UpdateCartItemRequest request) {
        User user = currentUserService.lockCurrentUser(authentication);
        Cart cart = requireCart(user.getId());
        CartItem item = requireOwnedItem(cart.getId(), itemId);
        ProductVariant variant = requirePurchasableVariant(item.getProductVariant().getId());
        assertWithinStock(variant, request.quantity());
        item.setQuantity(request.quantity());
        cartItemRepository.flush();
        return toResponse(cart);
    }

    @Transactional
    public void removeItem(Authentication authentication, Long itemId) {
        User user = currentUserService.lockCurrentUser(authentication);
        Cart cart = requireCart(user.getId());
        CartItem item = requireOwnedItem(cart.getId(), itemId);
        cartItemRepository.delete(item);
        cartItemRepository.flush();
    }

    @Transactional
    public void clear(Authentication authentication) {
        User user = currentUserService.lockCurrentUser(authentication);
        cartRepository.findByUser_Id(user.getId()).ifPresent(cart -> {
            List<CartItem> items = cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cart.getId());
            if (!items.isEmpty()) {
                cartItemRepository.deleteAll(items);
                cartItemRepository.flush();
            }
        });
    }

    private Cart requireCart(Long userId) {
        return cartRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ITEM_NOT_FOUND));
    }

    private Cart persistCart(User user) {
        try {
            Cart cart = new Cart();
            cart.setUser(user);
            return cartRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException ex) {
            return cartRepository.findByUser_Id(user.getId()).orElseThrow(() -> ex);
        }
    }

    private CartItem requireOwnedItem(Long cartId, Long itemId) {
        return cartItemRepository.findByIdAndCart_Id(itemId, cartId)
                .orElseThrow(() -> new ResourceNotFoundException(ITEM_NOT_FOUND));
    }

    private ProductVariant requirePurchasableVariant(Long variantId) {
        ProductVariant variant = productVariantRepository.findWithCatalogById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        if (!CatalogAvailability.isPurchasable(variant)) {
            throw new BusinessRuleException(UNAVAILABLE);
        }
        return variant;
    }

    private static void assertWithinStock(ProductVariant variant, int quantity) {
        Integer stock = variant.getStockQuantity();
        if (stock == null || quantity > stock) {
            throw new BusinessRuleException(OVER_STOCK);
        }
    }

    private static int addQuantities(int current, int incoming) {
        long next = (long) current + incoming;
        if (next > Integer.MAX_VALUE) {
            throw new BusinessRuleException(OVER_STOCK);
        }
        return (int) next;
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cart.getId());
        if (items.isEmpty()) {
            return CartMapper.empty(cart.getId());
        }
        Map<Long, String> images = primaryImageUrls(items);
        List<CartItemResponse> responses = items.stream()
                .map(item -> CartMapper.toItemResponse(
                        item,
                        images.get(item.getProductVariant().getProduct().getId()),
                        CatalogAvailability.isLineAvailable(item.getProductVariant(), item.getQuantity())))
                .toList();
        return CartMapper.toResponse(cart, responses);
    }

    private Map<Long, String> primaryImageUrls(List<CartItem> items) {
        List<Long> productIds = items.stream()
                .map(item -> item.getProductVariant().getProduct().getId())
                .distinct()
                .toList();
        return PrimaryImages.urlsByProductId(productImageRepository, productIds);
    }
}
