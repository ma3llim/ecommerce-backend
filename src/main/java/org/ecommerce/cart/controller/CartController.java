package org.ecommerce.cart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ecommerce.cart.dtos.request.AddCartItemRequest;
import org.ecommerce.cart.dtos.request.UpdateCartItemRequest;
import org.ecommerce.cart.dtos.response.CartResponse;
import org.ecommerce.cart.servcie.CartService;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cart Management", description = "APIs for authenticated users to view and manage their shopping cart")
public class CartController {
    private final CartService cartService;

    @Operation(summary = "Get current user's cart", description = "Retrieves the authenticated user's shopping cart and its items.")
    @GetMapping
    public ResponseEntity<ApiSuccessResponse<CartResponse>> getCart(
            Authentication authentication, HttpServletRequest request
    ) {
        CartResponse response = cartService.getCart(authentication);

        return ResponseEntity.ok(ApiSuccessResponse.<CartResponse>builder()
                .success(true).message("Cart fetched successfully")
                .data(response)
                .path(request.getRequestURI())
                .build()
        );
    }

    @Operation(summary = "Add product to cart",
            description = "Adds a product variant to the authenticated user's cart. The requested quantity must be available in stock.")
    @PostMapping("/items")
    public ResponseEntity<ApiSuccessResponse<Void>> addItem(
            @Valid @RequestBody AddCartItemRequest request, Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        cartService.addItem(request, authentication);

        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder()
                .success(true).message("Product added to cart successfully")
                .data(null)
                .path(httpRequest.getRequestURI())
                .build()
        );
    }

    @Operation(summary = "Update cart item quantity", description = "Updates the quantity of an existing cart item for the authenticated user.")
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<ApiSuccessResponse<Void>> updateItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request, Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        cartService.updateItem(itemId, request, authentication);

        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder()
                .success(true).message("Cart item updated successfully")
                .data(null)
                .path(httpRequest.getRequestURI())
                .build()
        );
    }

    @Operation(summary = "Remove item from cart", description = "Removes a specific item from the authenticated user's cart.")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiSuccessResponse<Void>> deleteItem(
            @PathVariable UUID id, Authentication authentication, HttpServletRequest httpRequest
    ) {
        cartService.deleteItem(id, authentication);

        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder()
                .success(true).message("Cart item removed successfully")
                .data(null)
                .path(httpRequest.getRequestURI())
                .build()
        );
    }

    @Operation(summary = "Clear cart", description = "Removes all items from the authenticated user's shopping cart.")
    @DeleteMapping
    public ResponseEntity<ApiSuccessResponse<Void>> clearCart(
            Authentication authentication, HttpServletRequest httpRequest
    ) {
        cartService.clearCart(authentication);

        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder()
                .success(true).message("Cart cleared successfully")
                .data(null)
                .path(httpRequest.getRequestURI())
                .build()
        );
    }
}
