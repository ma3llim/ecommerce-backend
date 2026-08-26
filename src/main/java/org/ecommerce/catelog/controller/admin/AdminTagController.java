package org.ecommerce.catelog.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.catelog.dtos.admin.request.TagRequest;
import org.ecommerce.catelog.dtos.admin.response.TagOptionResponse;
import org.ecommerce.catelog.dtos.admin.response.TagResponse;
import org.ecommerce.catelog.service.admin.AdminTagService;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Tag Management", description = "APIs for administrators to create, retrieve, update, and delete product tags")
public class AdminTagController {
    private final AdminTagService tagService;

    @Operation(summary = "Create a product tag", description = "Creates a new tag that can be assigned to products.")
    @PostMapping
    public ResponseEntity<ApiSuccessResponse<TagResponse>> createTag(
            @Valid @RequestBody TagRequest requestData, HttpServletRequest request) {
        TagResponse data = tagService.create(requestData);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiSuccessResponse.<TagResponse>builder()
                        .success(true)
                        .message("Tag created successfully.")
                        .data(data)
                        .path(request.getRequestURI()).build()
        );
    }

    @Operation(summary = "Get all product tags", description = "Retrieves a paginated list of product tags with optional search and sorting.")
    @GetMapping
    public ResponseEntity<ApiSuccessResponse<PageResponse<TagResponse>>> getTags(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest request
    ) {
        PageResponse<TagResponse> data = tagService.getAll(search, pageable);

        return ResponseEntity.ok(
                ApiSuccessResponse.<PageResponse<TagResponse>>builder()
                        .success(true)
                        .message("Tags fetched successfully.")
                        .data(data)
                        .path(request.getRequestURI()).build()
        );
    }

    @Operation(summary = "Get product tag by ID", description = "Retrieves a product tag by its unique identifier.")
    @GetMapping("/{tagId}")
    public ResponseEntity<ApiSuccessResponse<TagResponse>> getTagById(
            @PathVariable UUID tagId, HttpServletRequest request
    ) {
        TagResponse data = tagService.getById(tagId);

        return ResponseEntity.ok(
                ApiSuccessResponse.<TagResponse>builder()
                        .success(true)
                        .message("Tag fetched successfully.")
                        .data(data)
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @Operation(summary = "Update a product tag", description = "Updates the details of an existing product tag.")
    @PutMapping("/{tagId}")
    public ResponseEntity<ApiSuccessResponse<TagResponse>> updateTag(
            @PathVariable UUID tagId, @Valid @RequestBody TagRequest requestData, HttpServletRequest request) {

        TagResponse data = tagService.update(tagId, requestData);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiSuccessResponse.<TagResponse>builder()
                        .success(true)
                        .message("Tag updated successfully.")
                        .data(data)
                        .path(request.getRequestURI()).build()
        );
    }

    @Operation(summary = "Delete a product tag", description = "Deletes an existing product tag.")
    @DeleteMapping("/{tagId}")
    public ResponseEntity<ApiSuccessResponse<Void>> deleteTag(
            @PathVariable UUID tagId, HttpServletRequest request) {

        tagService.delete(tagId);

        return ResponseEntity.ok(
                ApiSuccessResponse.<Void>builder()
                        .success(true)
                        .message("Tag deleted successfully.")
                        .data(null).path(request.getRequestURI()).build()
        );
    }

    @Operation(summary = "Get tag options", description = "Retrieves a paginated list containing only tag IDs and names.")
    @GetMapping("/options")
    public ResponseEntity<ApiSuccessResponse<PageResponse<TagOptionResponse>>> getTagOptions(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            HttpServletRequest request
    ) {
        PageResponse<TagOptionResponse> data = tagService.getTagOptions(search, pageable);

        return ResponseEntity.ok(
                ApiSuccessResponse.<PageResponse<TagOptionResponse>>builder()
                        .success(true)
                        .message("Tag options fetched successfully.")
                        .data(data)
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
