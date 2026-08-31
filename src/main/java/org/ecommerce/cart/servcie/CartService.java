package org.ecommerce.cart.servcie;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.auth.entities.User;
import org.ecommerce.auth.repository.UserRepository;
import org.ecommerce.cart.dtos.request.AddCartItemRequest;
import org.ecommerce.cart.dtos.request.UpdateCartItemRequest;
import org.ecommerce.cart.dtos.response.CartItemResponse;
import org.ecommerce.cart.dtos.response.CartResponse;
import org.ecommerce.cart.entities.Cart;
import org.ecommerce.cart.entities.CartItem;
import org.ecommerce.cart.repository.CartItemRepository;
import org.ecommerce.cart.repository.CartRepository;
import org.ecommerce.catelog.entities.Product;
import org.ecommerce.catelog.entities.ProductVariant;
import org.ecommerce.catelog.entities.ProductVariantImage;
import org.ecommerce.catelog.repository.ProductRepository;
import org.ecommerce.catelog.repository.ProductVariantImageRepository;
import org.ecommerce.catelog.repository.ProductVariantRepository;
import org.ecommerce.common.exception.BadRequestException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantImageRepository productVariantImageRepository;

    public CartResponse getCart(Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("get cart request failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart newFirstCart = Cart.builder().userId(userId).totalAmount(BigDecimal.ZERO).build();
            return cartRepository.save(newFirstCart);
        });

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());

        if (items.isEmpty()) {
            return new CartResponse(cart.getId(), cart.getTotalAmount(), List.of()
            );
        }

        // all variants IDs
        List<UUID> variantIds = items.stream().map(CartItem::getProductVariantId).distinct().toList();

        // all variants
        List<ProductVariant> variants = productVariantRepository.findAllById(variantIds);

        Map<UUID, ProductVariant> variantMap = variants.stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<UUID> productIds = variants.stream().map(ProductVariant::getProductId).distinct().toList();

        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<UUID, ProductVariantImage> imageMap = productVariantImageRepository.findAllByProductVariantIdInAndPrimaryTrue(variantIds)
                .stream().collect(Collectors.toMap(ProductVariantImage::getProductVariantId, Function.identity()));

        List<CartItemResponse> cartItemResponses = items.stream().map(cartItem -> {
                    ProductVariant productVariant = variantMap.get(cartItem.getProductVariantId());
                    if (productVariant == null) {
                        throw new ResourceNotFoundException("Product Variant is not found");
                    }

                    Product product = productMap.get(productVariant.getProductId());
                    if (product == null) {
                        throw new ResourceNotFoundException("Product is not found");
                    }

                    ProductVariantImage image = imageMap.get(cartItem.getProductVariantId());

                    return new CartItemResponse(
                            cartItem.getId(),
                            product.getName(),
                            product.getSlug(),
                            image != null ? image.getImageUrl() : null,
                            cartItem.getProductVariantId(),
                            cartItem.getQuantity(),
                            cartItem.getUnitPrice(),
                            cartItem.getTotalPrice()
                    );
                })
                .toList();

        return buildCartResponseGet(cart, cartItemResponses);
    }

    public void addItem(AddCartItemRequest request, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();

        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("Add item in cart request failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        ProductVariant variant = productVariantRepository.findByIdAndActiveTrue(request.productVariantId()).orElseThrow(() -> {
            log.warn("Add cart item failed: active product variant not found. variantId={}, userId={}",
                    request.productVariantId(), user.getId());
            return new ResourceNotFoundException("Product variant not found");
        });

        if (request.quantity() == null || request.quantity() <= 0) {
            log.warn("Add cart item rejected: invalid quantity. variantId={}, quantity={}, userId={}",
                    request.productVariantId(), request.quantity(), userId);
            throw new BadRequestException("Quantity must be greater than zero");
        }

        if (variant.getStockQuantity() < request.quantity()) {
            log.warn("Add cart item rejected: insufficient stock. variantId={}, requestedQuantity={}, availableStock={}, userId={}",
                    variant.getId(), request.quantity(), variant.getStockQuantity(), userId
            );
            throw new BadRequestException("Insufficient stock");
        }

        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart newFirstCart = Cart.builder().userId(userId).totalAmount(BigDecimal.ZERO).build();
            return cartRepository.save(newFirstCart);
        });

        CartItem cartItem = cartItemRepository.findByCartIdAndProductVariantId(cart.getId(), variant.getId())
                .orElse(null);

        if (cartItem == null) {
            BigDecimal totalPrice = variant.getPrice().multiply(BigDecimal.valueOf(request.quantity()));

            cartItem = CartItem.builder()
                    .cartId(cart.getId())
                    .productVariantId(variant.getId())
                    .quantity(request.quantity())
                    .unitPrice(variant.getPrice())
                    .totalPrice(totalPrice)
                    .build();
        } else {
            int newQuantity = cartItem.getQuantity() + request.quantity();

            if (variant.getStockQuantity() < newQuantity) {
                throw new BadRequestException("Insufficient stock");
            }

            cartItem.setQuantity(newQuantity);

            cartItem.setTotalPrice(cartItem.getUnitPrice().multiply(BigDecimal.valueOf(newQuantity)));
        }

        cartItemRepository.save(cartItem);

        recalculateCartTotal(cart);
    }

    @Transactional
    public void updateItem(UUID itemId, UpdateCartItemRequest request, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("Update item in cart request failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        if (request.quantity() == null || request.quantity() <= 0) {
            log.warn("Update cart item rejected: invalid quantity. quantity={}, userId={}", request.quantity(), userId);
            throw new BadRequestException("Quantity must be greater than zero");
        }

        Cart cart = cartRepository.findByUserId(userId).orElseThrow(() -> {
            log.warn("Update cart item failed: cart not found. userId={}", user.getId());
            return new ResourceNotFoundException("Cart not found");
        });

        CartItem cartItem = cartItemRepository.findByIdAndCartId(itemId, cart.getId()).orElseThrow(() -> {
            log.warn("Update cart item failed: cart item not found. itemId={}, cartId={}, userId={}",
                    itemId, cart.getId(), userId);
            return new ResourceNotFoundException("Cart item not found");
        });

        ProductVariant variant = productVariantRepository.findByIdAndActiveTrue(cartItem.getProductVariantId()).orElseThrow(
                () -> {
                    log.warn("Update cart item failed: active product variant not found. variantId={}, itemId={}, userId={}",
                            cartItem.getProductVariantId(), itemId, userId);
                    return new ResourceNotFoundException("Product variant not found");
                }
        );

        if (variant.getStockQuantity() < request.quantity()) {
            log.warn("Update cart item rejected: insufficient stock. itemId={}, variantId={}, requestedQuantity={}, availableStock={}, userId={}",
                    itemId, variant.getId(), request.quantity(), variant.getStockQuantity(), userId);
            throw new BadRequestException("Insufficient stock");
        }

        cartItem.setQuantity(request.quantity());

        cartItem.setTotalPrice(cartItem.getUnitPrice().multiply(BigDecimal.valueOf(request.quantity())));

        cartItemRepository.save(cartItem);

        recalculateCartTotal(cart);
    }

    @Transactional
    public void deleteItem(UUID itemId, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("delete item in cart request failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Cart cart = cartRepository.findByUserId(userId).orElseThrow(() -> {
            log.warn("Delete cart item failed: cart not found. userId={}", user.getId());
            return new ResourceNotFoundException("Cart not found");
        });

        CartItem cartItem = cartItemRepository.findByIdAndCartId(itemId, cart.getId()).orElseThrow(() -> {
            log.warn("Delete cart item failed: cart item not found. itemId={}, cartId={}, userId={}",
                    itemId, cart.getId(), userId);
            return new ResourceNotFoundException("Cart item not found");
        });
        cartItemRepository.delete(cartItem);

        recalculateCartTotal(cart);
        log.info("Cart item deleted successfully. itemId={}, cartId={}, userId={}",
                itemId, cart.getId(), userId);
    }

    @Transactional
    public void clearCart(Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("clear cart in cart request failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Cart cart = cartRepository.findByUserId(userId).orElseThrow(() -> {
            log.warn("Clear cart failed: cart not found. userId={}", user.getId());
            return new ResourceNotFoundException("Cart not found");
        });

        cartItemRepository.deleteByCartId(cart.getId());

        cart.setTotalAmount(BigDecimal.ZERO);

        cartRepository.save(cart);

        log.info("Cart cleared successfully. cartId={}, userId={}", cart.getId(), userId);
    }

    private void recalculateCartTotal(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());

        BigDecimal total = items.stream().map(CartItem::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalAmount(total);

        cartRepository.save(cart);
    }

    private CartResponse buildCartResponseGet(Cart cart, List<CartItemResponse> itemResponses) {
        return new CartResponse(cart.getId(), cart.getTotalAmount(), itemResponses);
    }
}
