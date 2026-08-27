package org.ecommerce.catelog.repository;

import org.ecommerce.catelog.dtos.admin.response.TagOptionResponse;
import org.ecommerce.catelog.entities.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    Page<Tag> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
            SELECT new org.ecommerce.catelog.dtos.admin.response.TagOptionResponse(t.id, t.name)
            FROM Tag t
            """)
    Page<TagOptionResponse> findTagOptions(Pageable pageable);
}
