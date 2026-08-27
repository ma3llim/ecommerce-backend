package org.ecommerce.catelog.repository;

import org.ecommerce.catelog.dtos.admin.response.ProductTagOptionResponse;
import org.ecommerce.catelog.entities.ProductTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ProductTagRepository extends JpaRepository<ProductTag, UUID> {

    void deleteAllByTagId(UUID tagId);

    boolean existsByProductIdAndTagId(UUID productId, UUID tagId);

    Optional<ProductTag> findByProductIdAndTagId(UUID productId, UUID tagId);

    void deleteAllByProductId(UUID productId);

    @Query("""
            SELECT new org.ecommerce.catelog.dtos.admin.response.ProductTagOptionResponse(
                p.id, p.name, t.id, t.name
            )
            FROM ProductTag pt
            INNER JOIN Product p ON p.id = pt.productId
            INNER JOIN Tag t ON t.id = pt.tagId
            ORDER BY p.name ASC
            """)
    Page<ProductTagOptionResponse> findProductTagOptions(Pageable pageable);
}
