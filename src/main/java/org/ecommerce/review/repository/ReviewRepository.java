package org.ecommerce.review.repository;

import org.ecommerce.review.entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByProductIdAndProductVariantIdAndUserId(UUID productId, UUID productVariantId, UUID userId);

    Page<Review> findByProductIdOrderByCreatedAtDesc(UUID id, Pageable pageable);

    Optional<Review> findByUserIdAndProductIdAndProductVariantId(UUID userId, UUID productId, UUID productVariantId);
}
