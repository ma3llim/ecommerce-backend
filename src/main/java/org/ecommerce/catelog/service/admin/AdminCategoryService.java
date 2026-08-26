package org.ecommerce.catelog.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.catelog.dtos.admin.request.AddCategoryRequest;
import org.ecommerce.catelog.dtos.admin.request.UpdateCategoryRequest;
import org.ecommerce.catelog.dtos.admin.response.CategoryResponse;
import org.ecommerce.catelog.dtos.admin.response.CategorySummaryResponse;
import org.ecommerce.catelog.entities.Category;
import org.ecommerce.catelog.entities.Product;
import org.ecommerce.catelog.repository.CategoryRepository;
import org.ecommerce.catelog.repository.ProductRepository;
import org.ecommerce.common.dtos.CloudinaryUploadResult;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.enums.CloudinaryFolder;
import org.ecommerce.common.exception.ResourceAlreadyExistsException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.common.service.CloudinaryService;
import org.ecommerce.common.utils.SlugUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCategoryService {
    private final CategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final AdminProductService adminProductService;

    public CategoryResponse createCategory(AddCategoryRequest newCategory) {
        String categorySlug = SlugUtils.generateSlug(newCategory.name());

        boolean categoryExisted = categoryRepository.existsBySlug(categorySlug);
        if (categoryExisted) {
            log.warn("Create category request rejected: category slug already exists, slug={}", categorySlug);
            throw new ResourceAlreadyExistsException("Category Slug is already existed");
        }
        CloudinaryUploadResult uploadResult = cloudinaryService.uploadImage(newCategory.categoryImage(), CloudinaryFolder.CATEGORY_IMAGES);

        Category newCategoryEntity = Category.builder()
                .name(newCategory.name())
                .slug(categorySlug)
                .imageUrl(uploadResult.secureUrl())
                .imagePublicId(uploadResult.publicId())
                .active(newCategory.active())
                .build();

        Category savedCategory = categoryRepository.save(newCategoryEntity);

        log.info("Category created successfully, categoryId={}, slug={}", savedCategory.getId(), savedCategory.getSlug());
        return objectMapper.convertValue(savedCategory, CategoryResponse.class);
    }

    public PageResponse<CategoryResponse> getAllCategories(Pageable pageable) {
        Page<Category> categories = categoryRepository.findAll(pageable);

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

    public CategoryResponse getCategoryById(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(() -> {
            log.warn("Category not found, categoryId={}", categoryId);
            return new ResourceNotFoundException("Category not found");
        });

        return objectMapper.convertValue(category, CategoryResponse.class);
    }

    public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest categoryRequest) {
        String oldPublicId = null;
        Category categoryExisted = categoryRepository.findById(categoryId).orElseThrow(() -> {
            log.warn("Update category request failed: category not found, categoryId={}", categoryId);
            return new ResourceNotFoundException("Category is not found");
        });

        if (categoryRequest.name() != null) {
            String categorySlug = SlugUtils.generateSlug(categoryRequest.name());

            boolean newSlugExisted = categoryRepository.existsBySlugAndIdNot(categorySlug, categoryId);
            if (newSlugExisted) {
                log.warn("Update category request rejected: category slug already exists, categoryId={}, slug={}", categoryId, categorySlug);
                throw new ResourceAlreadyExistsException("Category Slug is already existed");
            }

            categoryExisted.setName(categoryRequest.name().trim());
            categoryExisted.setSlug(categorySlug);
        }
        if (categoryRequest.active() != null) categoryExisted.setActive(categoryRequest.active());
        if (categoryRequest.categoryImage() != null) {
            oldPublicId = categoryExisted.getImagePublicId();

            CloudinaryUploadResult cloudinaryUploadResult = cloudinaryService.uploadImage(categoryRequest.categoryImage(),
                    CloudinaryFolder.CATEGORY_IMAGES);

            categoryExisted.setImageUrl(cloudinaryUploadResult.secureUrl());
            categoryExisted.setImagePublicId(cloudinaryUploadResult.publicId());
        }

        categoryRepository.save(categoryExisted);
        log.info("Category updated successfully, categoryId={}, slug={}", categoryId, categoryExisted.getSlug());

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryService.removeImage(oldPublicId);
        }

        return objectMapper.convertValue(categoryExisted, CategoryResponse.class);
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        Category categoryExisted = categoryRepository.findById(categoryId).orElseThrow(() -> {
            log.warn("Delete category request failed: category not found, categoryId={}", categoryId);
            return new ResourceNotFoundException("Category is not found");
        });

        List<Product> products = productRepository.findAllByCategoryId(categoryId);

        if (!products.isEmpty()) {
            products.forEach(product -> {
                adminProductService.deleteProduct(product.getId());
            });
        }

        String categoryImagePublicId = categoryExisted.getImagePublicId();

        categoryRepository.deleteById(categoryId);
        cloudinaryService.removeImage(categoryImagePublicId);


        log.info("Category deleted successfully, categoryId={}", categoryId);
    }

    public PageResponse<CategorySummaryResponse> getActiveCategoryList(Pageable pageable) {
        Page<CategorySummaryResponse> categories = categoryRepository.findActiveCategorySummary(pageable);

        return new PageResponse<>(
                categories.getContent(),
                categories.getNumber(),
                categories.getSize(),
                categories.getTotalElements(),
                categories.getTotalPages(),
                categories.isFirst(),
                categories.isLast()
        );
    }
}
