package org.ecommerce.catelog.repository;

import org.ecommerce.catelog.entities.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    boolean existsBySku(String sku);

    List<ProductVariant> findAllByProductId(UUID productId);

    Optional<ProductVariant> findByIdAndActiveTrue(UUID productId);

    List<ProductVariant> findAllByProductIdAndActiveTrue(UUID productId);

    List<ProductVariant> findAllByIdInAndActiveTrue(Collection<UUID> ids);

    @Query("""
            SELECT COUNT(oi) FROM OrderItem oi JOIN ProductVariant pv ON pv.id = oi.productVariantId
                WHERE oi.orderId = :orderId AND pv.stockQuantity < oi.quantity
            """)
    long countInsufficientStock(@Param("orderId") UUID orderId);

    @Modifying
    @Query(value = """
            UPDATE product_variants pv
                    SET stock_quantity = stock_quantity - oi.quantity
                    FROM order_items oi
                    WHERE oi.order_id = :orderId
                      AND pv.id = oi.product_variant_id
            """, nativeQuery = true)
    int reduceStock(@Param("orderId") UUID orderId);

    @Modifying
    @Query(value = """
            UPDATE product_variants pv
                    SET stock_quantity = pv.stock_quantity + oi.quantity
                    FROM order_items oi
                    WHERE oi.order_id = :orderId
                      AND pv.id = oi.product_variant_id
            """, nativeQuery = true)
    int restoreStock(@Param("orderId") UUID orderId);

    Optional<ProductVariant> findByIdAndProductId(UUID variantId, UUID productId);
}
