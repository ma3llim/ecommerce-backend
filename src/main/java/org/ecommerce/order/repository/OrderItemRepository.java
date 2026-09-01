package org.ecommerce.order.repository;

import org.ecommerce.order.entities.OrderItem;
import org.ecommerce.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    @Query("""
            SELECT COUNT(oi) > 0 FROM OrderItem oi JOIN Order o ON o.id = oi.orderId WHERE o.userId = :userId
              AND o.orderStatus = :orderStatus AND oi.productId = :productId AND oi.productVariantId = :productVariantId
            """)
    boolean existsPurchasedProductVariant(
            @Param("userId") UUID userId,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("productId") UUID productId,
            @Param("productVariantId") UUID productVariantId
    );
}