package org.ecommerce.catelog.service.publics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.catelog.dtos.admin.response.CategoryResponse;
import org.ecommerce.catelog.entities.Category;
import org.ecommerce.catelog.repository.CategoryRepository;
import org.ecommerce.common.constants.RedisKeyConstants;
import org.ecommerce.common.dtos.PageResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(
            value = RedisKeyConstants.CATEGORIES,
            key = "'page=' + #pageable.pageNumber + ':size=' + #pageable.pageSize + ':sort=' + #pageable.sort"
    )
    public PageResponse<CategoryResponse> getAllCategories(Pageable pageable) {
        Page<Category> categories = categoryRepository.findAllByActiveTrue(pageable);

        Page<CategoryResponse> categoriesInfoResponse = categories.map(
                category -> objectMapper.convertValue(category, CategoryResponse.class));

        return new PageResponse<>(
                categoriesInfoResponse.getContent(),
                categoriesInfoResponse.getNumber(),
                categoriesInfoResponse.getSize(),
                categoriesInfoResponse.getTotalElements(),
                categoriesInfoResponse.getTotalPages(),
                categoriesInfoResponse.isFirst(),
                categoriesInfoResponse.isLast()
        );
    }
}
