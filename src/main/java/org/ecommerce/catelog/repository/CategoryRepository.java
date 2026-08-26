package org.ecommerce.catelog.repository;

import org.ecommerce.auth.Dtos.request.CategorySummary;
import org.ecommerce.catelog.entities.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsBySlug(String categorySlug);

    boolean existsBySlugAndIdNot(String categorySlug, UUID categoryId);

    Optional<Category> findBySlugAndActiveTrue(String categorySlug);

    Page<Category> findAllByActiveTrue(Pageable pageable);

    @Query("""
            SELECT new org.ecommerce.auth.Dtos.request.CategorySummary(c.id, c.name)
            FROM Category c WHERE c.id IN :ids
            """)
    List<CategorySummary> findAllByIdIn(@Param("ids") Collection<UUID> ids);
}
