package org.ecommerce.catelog.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.catelog.dtos.admin.request.*;
import org.ecommerce.catelog.dtos.admin.response.*;
import org.ecommerce.catelog.entities.*;
import org.ecommerce.catelog.repository.*;
import org.ecommerce.common.constants.AppConstants;
import org.ecommerce.common.dtos.CloudinaryUploadResult;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.enums.CloudinaryFolder;
import org.ecommerce.common.enums.VisibleStatus;
import org.ecommerce.common.exception.BadRequestException;
import org.ecommerce.common.exception.ResourceAlreadyExistsException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.common.service.CloudinaryService;
import org.ecommerce.common.utils.SkuUtils;
import org.ecommerce.common.utils.SlugUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantImageRepository productVariantImageRepository;
    private final CloudinaryService cloudinaryService;
    private final TagRepository tagRepository;
    private final ProductTagRepository productTagRepository;
    private final ProductFaqRepository productFaqRepository;
    private final ObjectMapper objectMapper;

    public PageResponse<ProductResponse> getProducts(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);

        Set<UUID> categoryIds = products.getContent().stream().map(Product::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> categoryMap = categoryRepository.findAllByIdIn(categoryIds).stream()
                .collect(Collectors.toMap(CategorySummaryResponse::id, CategorySummaryResponse::name));

        Page<ProductResponse> productResponses = products.map(product -> new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                categoryMap.get(product.getCategoryId()),
                product.getName(),
                product.getDescription(),
                product.getSpecifications(),
                product.isPublished(),
                product.getCreatedAt()
        ));


        return new PageResponse<>(
                productResponses.getContent(),
                productResponses.getNumber(),
                productResponses.getSize(),
                productResponses.getTotalElements(),
                productResponses.getTotalPages(),
                productResponses.isFirst(),
                productResponses.isLast()
        );
    }

    public ProductResponse createProduct(AddProductRequest productRequest) {
        Category category = categoryRepository.findById(productRequest.categoryId()).orElseThrow(() -> {
            log.warn("Create product request failed: category not found, categoryId={}", productRequest.categoryId());
            return new ResourceNotFoundException("Category not found");
        });

        String productSlug = SlugUtils.generateSlug(productRequest.name());
        if (productRepository.existsBySlug(productSlug)) {
            log.warn("Create product request rejected: product slug already exists, slug={}", productSlug);
            throw new ResourceAlreadyExistsException("Product slug already exists");
        }

        Product newProduct = Product.builder()
                .categoryId(category.getId())
                .name(productRequest.name())
                .slug(productSlug)
                .description(productRequest.description())
                .specifications(productRequest.specifications())
                .published(false)
                .build();

        Product savedProduct = productRepository.save(newProduct);

        log.info("Product created successfully, productId={}, categoryId={}, slug={}",
                savedProduct.getId(), savedProduct.getCategoryId(), savedProduct.getSlug());

        return objectMapper.convertValue(savedProduct, ProductResponse.class);
    }

    public ProductDetailsResponse getProduct(UUID productId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Get product details request failed: product not found, productId={}", productId);
            return new ResourceNotFoundException("Product not found");
        });

        Category category = categoryRepository.findById(product.getCategoryId()).orElseThrow(() -> {
            log.warn("Get product details request failed: category not found, productId={}, categoryId={}",
                    productId, product.getCategoryId());
            return new ResourceNotFoundException("Category not found");
        });

        List<ProductVariant> variants = productVariantRepository.findAllByProductId(productId);

        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();

        List<ProductVariantImage> variantImages = variantIds.isEmpty() ? List.of() :
                productVariantImageRepository.findAllByProductVariantIdIn(variantIds);

        Map<UUID, List<ProductVariantImage>> imagesByVariantId = variantImages.stream()
                .collect(Collectors.groupingBy(ProductVariantImage::getProductVariantId));

        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(productVariant -> {
                    List<ProductVariantImageResponse> imageResponses = imagesByVariantId
                            .getOrDefault(productVariant.getId(), List.of())
                            .stream()
                            .map(image -> new ProductVariantImageResponse(
                                    image.getId(),
                                    image.getImageUrl(),
                                    image.getDisplayOrder(),
                                    image.isPrimary())
                            ).toList();

                    return new ProductVariantResponse(
                            productVariant.getId(),
                            productVariant.getSku(),
                            productVariant.getPrice(),
                            productVariant.getStockQuantity(),
                            productVariant.getAttributes(),
                            productVariant.isActive(),
                            imageResponses
                    );
                }).toList();

        return new ProductDetailsResponse(
                product.getId(),
                new CategorySummaryResponse(
                        category.getId(),
                        category.getName()
                ),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getSpecifications(),
                product.getDefaultVariantId(),
                product.isPublished(),
                variantResponses,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public ProductResponse updateProduct(UUID productId, UpdateProduct productRequest) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Update product request failed: product not found, productId={}", productId);
            return new ResourceNotFoundException("Product not found");
        });

        if (productRequest.categoryId() != null && !productRequest.categoryId().equals(product.getCategoryId())) {
            categoryRepository.findById(productRequest.categoryId()).orElseThrow(() -> {
                log.warn("Update product request failed: category not found, productId={}, categoryId={}",
                        productId, productRequest.categoryId());
                return new ResourceNotFoundException("Category not found");
            });

            product.setCategoryId(productRequest.categoryId());
        }

        if (productRequest.name() != null) {
            String productSlug = SlugUtils.generateSlug(productRequest.name());

            if (productRepository.existsBySlugAndIdNot(productSlug, productId)) {
                log.warn("Update product request rejected: product slug already exists, productId={}, slug={}",
                        productId, productSlug);
                throw new ResourceAlreadyExistsException("Product slug already exists");
            }

            product.setName(productRequest.name());
            product.setSlug(productSlug);
        }

        if (productRequest.description() != null) {
            product.setDescription(productRequest.description());
        }

        if (productRequest.specifications() != null) {
            product.setSpecifications(productRequest.specifications());
        }

        Product savedProduct = productRepository.save(product);

        log.info("Product updated successfully, productId={}, categoryId={}, slug={}",
                savedProduct.getId(), savedProduct.getCategoryId(), savedProduct.getSlug());
        return objectMapper.convertValue(savedProduct, ProductResponse.class);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Delete product request failed: product not found, productId={}", productId);
            return new ResourceNotFoundException("Product not found");
        });

        List<ProductVariant> productVariants = productVariantRepository.findAllByProductId(product.getId());

        productVariants.forEach(this::deleteVariantResources);

        productTagRepository.deleteAllByProductId(product.getId());

        productFaqRepository.deleteAllByProductId(product.getId());

        productRepository.delete(product);
        log.info("Product deleted successfully, productId={}, variantCount={}", productId, productVariants.size());
    }

    @Transactional
    public ProductVariantResponse addProductVariant(UUID productId, AddProductVariants addProductVariants) {
        Map<String, Object> attributes;
        try {
            attributes = objectMapper.readValue(
                    addProductVariants.attributes(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            log.warn("Add product variant request rejected: invalid attributes JSON, productId={}", productId);
            throw new BadRequestException("Invalid variant attributes");
        }

        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Add product variant request failed: product not found, productId={}", productId);
            return new ResourceNotFoundException("Product not found");
        });

        String variantValue = attributes.values().stream().map(Objects::toString)
                .collect(Collectors.joining("-"));
        String variantSku = SkuUtils.generateSku(product.getName(), variantValue);

        if (productVariantRepository.existsBySku(variantSku)) {
            log.warn("Add product variant request rejected: SKU already exists, productId={}, sku={}",
                    productId, variantSku);
            throw new ResourceAlreadyExistsException("SKU already exists");
        }

        ProductVariant variant = ProductVariant.builder()
                .productId(product.getId())
                .sku(variantSku)
                .price(addProductVariants.price())
                .stockQuantity(addProductVariants.stockQuantity())
                .attributes(attributes)
                .build();

        ProductVariant savedVariant = productVariantRepository.save(variant);

        List<ProductVariantImage> variantImages = List.of();

        if (addProductVariants.images() != null && !addProductVariants.images().isEmpty()) {
            variantImages = uploadAndCreateImageRecords(savedVariant.getId(), addProductVariants.images());

            productVariantImageRepository.saveAll(variantImages);

            if (product.getDefaultVariantId() == null) {
                product.setDefaultVariantId(savedVariant.getId());
                productRepository.save(product);
                log.info("Default variant assigned to product, productId={}, variantId={}",
                        productId, savedVariant.getId());
            }
        }

        List<ProductVariantImageResponse> imageResponses = variantImages.stream()
                .map(image -> objectMapper.convertValue(image, ProductVariantImageResponse.class))
                .toList();

        log.info("Product variant created successfully, productId={}, variantId={}, sku={}, imageCount={}",
                productId, savedVariant.getId(), savedVariant.getSku(), variantImages.size());

        return new ProductVariantResponse(
                savedVariant.getId(),
                savedVariant.getSku(),
                savedVariant.getPrice(),
                savedVariant.getStockQuantity(),
                savedVariant.getAttributes(),
                savedVariant.isActive(),
                imageResponses
        );
    }

    public ProductResponse updateProductStatus(UUID productId, VisibleStatus status) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Update product status request failed: product not found, productId={}, status={}",
                    productId, status);
            return new ResourceNotFoundException("Product not found");
        });

        boolean published = status == VisibleStatus.ACTIVE;

        if (product.isPublished() == published) {
            log.info("Update product status skipped: status is already {}, productId={}", status, productId);

            return objectMapper.convertValue(product, ProductResponse.class);
        }

        product.setPublished(published);
        Product savedProduct = productRepository.save(product);

        log.info("Product status updated successfully, productId={}, status={}", productId, status);
        return objectMapper.convertValue(savedProduct, ProductResponse.class);
    }

    private List<ProductVariantImage> uploadAndCreateImageRecords(UUID productVariantId, List<MultipartFile> images) {
        List<ProductVariantImage> imageRecords = new ArrayList<>();

        for (int index = 0; index < images.size(); index++) {
            MultipartFile image = images.get(index);

            CloudinaryUploadResult uploadResult = cloudinaryService.uploadImage(image, CloudinaryFolder.PRODUCT_IMAGES);
            ProductVariantImage imageRecord = ProductVariantImage.builder()
                    .productVariantId(productVariantId)
                    .imageUrl(uploadResult.secureUrl())
                    .imagePublicId(uploadResult.publicId())
                    .displayOrder(index + 1)
                    .primary(index == 0)
                    .build();

            imageRecords.add(imageRecord);
        }

        return imageRecords;
    }

    public ProductVariantResponse updateProductVariant(UUID productId, UUID variantId, UpdateProductVariant productVariant) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Update product variant failed: product not found, productId={}, variantId={}",
                    productId, variantId);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant existingVariant = productVariantRepository.findById(variantId).orElseThrow(() -> {
            log.warn("Update product variant failed: variant not found, productId={}, variantId={}",
                    productId, variantId);
            return new ResourceNotFoundException("Product variant not found");
        });

        if (!existingVariant.getProductId().equals(product.getId())) {
            log.warn("Update product variant rejected: variant does not belong to product, productId={}, variantId={}",
                    productId, variantId);
            throw new ResourceNotFoundException("Product variant not found");
        }

        if (productVariant.price() != null) {
            existingVariant.setPrice(productVariant.price());
        }

        if (productVariant.stockQuantity() != null) {
            existingVariant.setStockQuantity(productVariant.stockQuantity());
        }

        if (productVariant.attributes() != null) {
            existingVariant.setAttributes(productVariant.attributes());
        }

        ProductVariant savedVariant = productVariantRepository.save(existingVariant);

        log.info("Product variant updated successfully, productId={}, variantId={}, sku={}", productId, variantId, savedVariant.getSku());

        return objectMapper.convertValue(savedVariant, ProductVariantResponse.class);
    }

    public List<ProductVariantImageResponse> uploadsImage(UUID productId, UUID variantId, @Valid AddImages images) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Upload product variant images failed: product not found, productId={}, variantId={}", productId, variantId);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow(() -> {
            log.warn("Upload product variant images failed: variant not found, productId={}, variantId={}", productId, variantId);
            return new ResourceNotFoundException("Product variant not found");
        });

        if (!variant.getProductId().equals(product.getId())) {
            log.warn("Upload product variant images rejected: variant does not belong to product, productId={}, " +
                    "variantId={}", productId, variantId);
            throw new ResourceNotFoundException("Product variant not found");
        }

        int existingImageCount = productVariantImageRepository.countByProductVariantId(variantId);

        log.debug("Existing product variant image count, productId={}, variantId={}, existingImageCount={}",
                productId, variantId, existingImageCount);

        if (existingImageCount >= AppConstants.MAX_FILE_UPLOAD) {
            log.warn("Image upload rejected: maximum image limit reached, productId={}, " +
                            "variantId={}, existingImageCount={}, maxAllowed={}",
                    productId, variantId, existingImageCount, AppConstants.MAX_FILE_UPLOAD);
            throw new BadRequestException("A product variant can have a maximum of " + AppConstants.MAX_FILE_UPLOAD + " images");
        }

        int requestedImageCount = images.images().size();
        int totalImageCount = existingImageCount + requestedImageCount;

        if (totalImageCount > AppConstants.MAX_FILE_UPLOAD) {
            log.warn("Image upload rejected: maximum image limit exceeded, productId={}, variantId={}, " +
                            "existingImageCount={}, requestedImageCount={}, totalImageCount={}, maxAllowed={}",
                    productId, variantId, existingImageCount, requestedImageCount, totalImageCount,
                    AppConstants.MAX_FILE_UPLOAD);
            throw new BadRequestException("You can upload a maximum of " + AppConstants.MAX_FILE_UPLOAD + " images per product variant");
        }

        List<ProductVariantImage> imageRecords = new ArrayList<>();

        for (int i = 0; i < images.images().size(); i++) {
            MultipartFile image = images.images().get(i);
            CloudinaryUploadResult uploadResult = cloudinaryService.uploadImage(image, CloudinaryFolder.PRODUCT_IMAGES);

            int displayOrder = existingImageCount + i + 1;
            boolean primary = existingImageCount == 0 && i == 0;

            ProductVariantImage imageRecord = ProductVariantImage.builder()
                    .productVariantId(variantId)
                    .imageUrl(uploadResult.secureUrl())
                    .imagePublicId(uploadResult.publicId())
                    .displayOrder(displayOrder)
                    .primary(primary)
                    .build();

            imageRecords.add(imageRecord);
        }

        productVariantImageRepository.saveAll(imageRecords);
        log.info("Product variant images uploaded successfully, productId={}, variantId={}, uploadedCount={}, " +
                        "previousImageCount={}, totalImageCount={}", productId, variantId, requestedImageCount,
                existingImageCount, totalImageCount);

        return objectMapper.convertValue(imageRecords, new TypeReference<List<ProductVariantImageResponse>>() {
        });
    }

    public ProductVariantImageResponse replaceImage(UUID productId, UUID variantId, UUID variantImageId, ReplaceImage image) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Replace product variant image failed: product not found, productId={}, variantId={}," +
                    " variantImageId={}", productId, variantId, variantImageId);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow(() -> {
            log.warn("Replace product variant image failed: variant not found, productId={}, variantId={}," +
                    " variantImageId={}", productId, variantId, variantImageId);
            return new ResourceNotFoundException("Product variant not found");
        });

        if (!variant.getProductId().equals(product.getId())) {
            log.warn("Replace product variant image rejected: variant does not belong to product, productId={}, " +
                    "variantId={}, variantImageId={}", productId, variantId, variantImageId);
            throw new ResourceNotFoundException("Product variant not found");
        }

        ProductVariantImage existingImage = productVariantImageRepository.findById(variantImageId).orElseThrow(() -> {
            log.warn("Replace product variant image failed: image not found, productId={}, variantId={}," +
                    " variantImageId={}", productId, variantId, variantImageId);
            return new ResourceNotFoundException("Product variant image not found");
        });

        if (!existingImage.getProductVariantId().equals(variant.getId())) {
            log.warn("Replace product variant image rejected: image does not belong to variant, productId={}, " +
                    "variantId={}, variantImageId={}", productId, variantId, variantImageId);
            throw new ResourceNotFoundException("Product variant image not found");
        }

        String oldPublicId = existingImage.getImagePublicId();

        CloudinaryUploadResult uploadResult = cloudinaryService.uploadImage(image.image(), CloudinaryFolder.PRODUCT_IMAGES);

        existingImage.setImageUrl(uploadResult.secureUrl());
        existingImage.setImagePublicId(uploadResult.publicId());

        ProductVariantImage savedImage = productVariantImageRepository.save(existingImage);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryService.removeImage(oldPublicId);
        }

        log.info("Product variant image replaced successfully, productId={}, variantId={}, variantImageId={}",
                productId, variantId, variantImageId);
        return objectMapper.convertValue(savedImage, ProductVariantImageResponse.class);
    }

    @Transactional
    public ProductVariantImageResponse setVariantImagePrimary(UUID productId, UUID variantId, UUID variantImageId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Set variant image primary failed: product not found, productId={}, variantId={}, " +
                    "variantImageId={}", productId, variantId, variantImageId);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow(() -> {
            log.warn("Set variant image primary failed: variant not found, productId={}, variantId={}, " +
                    "variantImageId={}", productId, variantId, variantImageId);
            return new ResourceNotFoundException("Product variant not found");
        });

        if (!variant.getProductId().equals(product.getId())) {
            log.warn("Set variant image primary rejected: variant does not belong to product, productId={}, " +
                    "variantId={}, variantImageId={}", productId, variantId, variantImageId);
            throw new ResourceNotFoundException("Product variant not found");
        }

        ProductVariantImage image = productVariantImageRepository.findById(variantImageId).orElseThrow(() -> {
            log.warn("Set variant image primary failed: image not found, productId={}, variantId={}, " +
                    "variantImageId={}", productId, variantId, variantImageId);
            return new ResourceNotFoundException("Product variant image not found");
        });

        if (!image.getProductVariantId().equals(variant.getId())) {
            log.warn("Set variant image primary rejected: image does not belong to variant, productId={}, " +
                    "variantId={}, variantImageId={}", productId, variantId, variantImageId);
            throw new ResourceNotFoundException("Product variant image not found");
        }

        if (image.isPrimary()) {
            log.debug("Set variant image primary skipped: image is already primary, productId={}, " +
                    "variantId={}, variantImageId={}", productId, variantId, variantImageId);
            return objectMapper.convertValue(image, ProductVariantImageResponse.class);
        }

        productVariantImageRepository.findByProductVariantIdAndPrimaryTrue(variantId)
                .ifPresent(currentPrimary ->
                        currentPrimary.setPrimary(false)
                );

        image.setPrimary(true);

        ProductVariantImage savedImage = productVariantImageRepository.save(image);
        log.info("Product variant image set as primary successfully, productId={}, " +
                "variantId={}, variantImageId={}", productId, variantId, variantImageId);
        return objectMapper.convertValue(savedImage, ProductVariantImageResponse.class);
    }

    @Transactional
    public List<ProductVariantImageResponse> reorderImages(UUID productId, UUID variantId, ReorderImages reorderImages) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Reorder variant images failed: product not found, productId={}, variantId={}",
                    productId, variantId);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant variant =
                productVariantRepository.findById(variantId).orElseThrow(() -> {
                    log.warn("Reorder variant images failed: variant not found, productId={}, variantId={}",
                            productId, variantId);
                    return new ResourceNotFoundException("Product variant not found");
                });

        if (!variant.getProductId().equals(product.getId())) {
            log.warn("Reorder variant images rejected: variant does not belong to product, productId={}, variantId={}",
                    productId, variantId);
            throw new ResourceNotFoundException("Product variant not found");
        }

        List<ProductVariantImage> existingImages = productVariantImageRepository.findAllByProductVariantId(variantId);

        List<UUID> requestedIds = reorderImages.imageIds();
        Set<UUID> uniqueIds = new HashSet<>(requestedIds);

        if (uniqueIds.size() != requestedIds.size()) {
            log.warn("Reorder variant images rejected: duplicate image IDs, productId={}, variantId={}", productId, variantId);
            throw new BadRequestException("Duplicate image IDs are not allowed");
        }

        Set<UUID> existingIds = existingImages.stream().map(ProductVariantImage::getId).collect(Collectors.toSet());
        if (!existingIds.containsAll(requestedIds)) {
            log.warn("Reorder variant images rejected: image does not belong to variant, productId={}, " +
                    "variantId={}", productId, variantId);
            throw new BadRequestException("One or more image IDs do not belong to this product variant");
        }

        if (requestedIds.size() != existingIds.size()) {
            log.warn("Reorder variant images rejected: incomplete image ID list, productId={}," +
                            " variantId={}, requestedCount={}, existingCount={}",
                    productId, variantId, requestedIds.size(), existingIds.size());
            throw new BadRequestException("All product variant images must be included when reordering");
        }

        Map<UUID, ProductVariantImage> imageMap = existingImages.stream().collect(Collectors.toMap(
                ProductVariantImage::getId,
                image -> image
        ));

        for (int i = 0; i < requestedIds.size(); i++) {
            ProductVariantImage image = imageMap.get(requestedIds.get(i));
            image.setDisplayOrder(i + 1);
        }

        List<ProductVariantImage> savedImages = productVariantImageRepository.saveAll(existingImages);

        log.info("Product variant images reordered successfully, productId={}, variantId={}, imageCount={}",
                productId, variantId, savedImages.size());

        return objectMapper.convertValue(savedImages, new TypeReference<List<ProductVariantImageResponse>>() {
                }
        );
    }

    @Transactional
    public void deleteVariantImage(UUID productId, UUID variantId, UUID imageVariantId) {

        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Delete variant image failed: product not found, productId={}, variantId={}, imageId={}",
                    productId, variantId, imageVariantId);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow(() -> {
            log.warn("Delete variant image failed: variant not found, productId={}, variantId={}, imageId={}",
                    productId, variantId, imageVariantId);
            return new ResourceNotFoundException("Product variant not found");
        });

        if (!variant.getProductId().equals(product.getId())) {
            log.warn("Delete variant image rejected: variant does not belong to product, productId={}, " +
                    "variantId={}, imageId={}", productId, variantId, imageVariantId);
            throw new ResourceNotFoundException("Product variant not found");
        }

        ProductVariantImage image = productVariantImageRepository.findById(imageVariantId).orElseThrow(() -> {
            log.warn("Delete variant image failed: image not found, productId={}, variantId={}, imageId={}", productId, variantId, imageVariantId);
            return new ResourceNotFoundException("Product variant image not found");
        });

        if (!image.getProductVariantId().equals(variant.getId())) {
            log.warn("Delete variant image rejected: image does not belong to variant, productId={}, " +
                    "variantId={}, imageId={}", productId, variantId, imageVariantId);
            throw new ResourceNotFoundException("Product variant image not found");
        }

        boolean deletedImageWasPrimary = image.isPrimary();

        cloudinaryService.removeImage(image.getImagePublicId());

        productVariantImageRepository.delete(image);

        List<ProductVariantImage> remainingImages = productVariantImageRepository
                .findAllByProductVariantIdOrderByDisplayOrderAsc(variantId);

        for (int i = 0; i < remainingImages.size(); i++) {
            ProductVariantImage remainingImage = remainingImages.get(i);

            remainingImage.setDisplayOrder(i + 1);

            if (deletedImageWasPrimary) {
                remainingImage.setPrimary(i == 0);
            }
        }
        productVariantImageRepository.saveAll(remainingImages);

        log.info("Product variant image deleted successfully, productId={}, variantId={}, " +
                        "imageId={}, wasPrimary={}, remainingImageCount={}", productId, variantId, imageVariantId,
                deletedImageWasPrimary, remainingImages.size());
    }

    @Transactional
    public void deleteVariant(UUID productId, UUID variantId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Delete product variant failed: product not found, productId={}, variantId={}", productId, variantId);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow(() -> {
            log.warn("Delete product variant failed: variant not found, productId={}, variantId={}", productId, variantId);
            return new ResourceNotFoundException("Product variant not found");
        });

        if (!variant.getProductId().equals(product.getId())) {
            log.warn("Delete product variant rejected: variant does not belong to product, productId={}, variantId={}",
                    productId, variantId);
            throw new ResourceNotFoundException("Product variant not found");
        }

        List<ProductVariantImage> variantImages = productVariantImageRepository.findAllByProductVariantId(variantId);

        for (ProductVariantImage image : variantImages) {
            cloudinaryService.removeImage(image.getImagePublicId());
        }

        productVariantImageRepository.deleteAll(variantImages);

        productVariantRepository.delete(variant);

        if (product.getDefaultVariantId() != null && product.getDefaultVariantId().equals(variantId)) {
            product.setDefaultVariantId(null);
            productRepository.save(product);

            log.info("Default variant cleared after variant deletion, productId={}, variantId={}", productId, variantId);
        }
        log.info("Product variant deleted successfully, productId={}, variantId={}, imageCount={}",
                productId, variantId, variantImages.size());
    }

    public ProductVariantResponse updateVariantStatus(UUID productId, UUID variantId, VisibleStatus status) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Update variant status failed: product not found, productId={}, variantId={}, status={}",
                    productId, variantId, status);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow(() -> {
            log.warn("Update variant status failed: variant not found, productId={}, variantId={}, status={}",
                    productId, variantId, status);
            return new ResourceNotFoundException("Product variant not found");
        });

        if (!variant.getProductId().equals(product.getId())) {
            log.warn("Update variant status rejected: variant does not belong to product, productId={}, " +
                    "variantId={}, status={}", productId, variantId, status);
            throw new ResourceNotFoundException("Product variant not found");
        }

        boolean active = status == VisibleStatus.ACTIVE;

        if (variant.isActive() == active) {
            log.debug("Update variant status skipped: status is already {}, productId={}, variantId={}",
                    status, productId, variantId);
            return objectMapper.convertValue(variant, ProductVariantResponse.class);
        }

        variant.setActive(active);
        ProductVariant savedVariant = productVariantRepository.save(variant);

        log.info("Product variant status updated successfully, productId={}, variantId={}, status={}",
                productId, variantId, status);

        return objectMapper.convertValue(savedVariant, ProductVariantResponse.class);
    }

    public ProductTagMappingResponse addTagProduct(UUID productId, UUID tagId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Add product tag failed: product not found. productId={}, tagId={}", productId, tagId);
            return new ResourceNotFoundException("Product not found");
        });

        Tag tag = tagRepository.findById(tagId).orElseThrow(() -> {
            log.warn("Add product tag failed: tag not found. productId={}, tagId={}", productId, tagId);
            return new ResourceNotFoundException("Tag not found");
        });

        if (productTagRepository.existsByProductIdAndTagId(product.getId(), tag.getId())) {
            log.warn("Add product tag rejected: tag is already assigned to product. productId={}, tagId={}",
                    productId, tagId
            );
            throw new ResourceAlreadyExistsException("Tag is already assigned to this product");
        }

        ProductTag productTag = ProductTag.builder().productId(product.getId()).tagId(tag.getId()).build();

        ProductTag savedProductTag = productTagRepository.save(productTag);
        log.info("Product tag assigned successfully. productId={}, tagId={}, productTagId={}",
                productId, tagId, savedProductTag.getId()
        );

        return objectMapper.convertValue(savedProductTag, ProductTagMappingResponse.class);
    }

    public void removeTagProduct(UUID productId, UUID tagId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> {
            log.warn("Remove product tag failed: product not found. productId={}, tagId={}", productId, tagId);
            return new ResourceNotFoundException("Product not found");
        });

        Tag tag = tagRepository.findById(tagId).orElseThrow(() -> {
            log.warn("Remove product tag failed: tag not found. productId={}, tagId={}", productId, tagId);
            return new ResourceNotFoundException("Tag not found");
        });

        ProductTag productTag = productTagRepository.findByProductIdAndTagId(product.getId(), tag.getId()).orElseThrow(() -> {
            log.warn("Remove product tag failed: tag assignment not found. productId={}, tagId={}", productId, tagId);
            return new ResourceNotFoundException("Tag is not assigned to this product");
        });

        productTagRepository.delete(productTag);
        log.info("Product tag removed successfully. productId={}, tagId={}, productTagId={}",
                productId, tagId, productTag.getId());
    }

    private void deleteVariantResources(ProductVariant variant) {
        List<ProductVariantImage> images = productVariantImageRepository.findAllByProductVariantId(variant.getId());

        for (ProductVariantImage image : images) {
            cloudinaryService.removeImage(image.getImagePublicId());
        }

        productVariantImageRepository.deleteAll(images);

        productVariantRepository.delete(variant);
    }
}