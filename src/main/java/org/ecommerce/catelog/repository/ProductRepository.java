package org.ecommerce.catelog.repository;

import org.ecommerce.admin.dashboard.projection.ProductCategoryStatisticsProjection;
import org.ecommerce.catelog.dtos.admin.response.ProductOptionResponse;
import org.ecommerce.catelog.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    @Query("""
            SELECT new org.ecommerce.catelog.dtos.admin.response.ProductOptionResponse(p.id, p.name)
            FROM Product p
            """)
    Page<ProductOptionResponse> findProductOptions(Pageable pageable);

    @Query(value = """
            SELECT c.name AS category, COUNT(p.id) AS count FROM products p
            JOIN categories c ON p.category_id = c.id GROUP BY c.name ORDER BY c.name
            """, nativeQuery = true)
    List<ProductCategoryStatisticsProjection> getProductCategoryStatistics();
}
