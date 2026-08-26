package org.ecommerce.catelog.repository;

import org.ecommerce.catelog.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsBySlug(String productSlug);

    boolean existsBySlugAndIdNot(String productSlug, UUID productID);

    Page<Product> findByCategoryIdAndPublishedTrue(UUID categoryId, Pageable pageable);

    Page<Product> findByPublishedTrue(Pageable pageable);

    Optional<Product> findBySlugAndPublishedTrue(String productSlug);

    Optional<Product> findByIdAndPublishedTrue(UUID productId);

    List<Product> findAllByIdInAndPublishedTrue(List<UUID> productIds);

    List<Product> findAllByCategoryId(UUID categoryId);
}
