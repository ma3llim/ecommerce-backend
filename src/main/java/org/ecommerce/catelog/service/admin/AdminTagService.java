package org.ecommerce.catelog.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.catelog.dtos.admin.request.TagRequest;
import org.ecommerce.catelog.dtos.admin.response.TagOptionResponse;
import org.ecommerce.catelog.dtos.admin.response.TagResponse;
import org.ecommerce.catelog.entities.Tag;
import org.ecommerce.catelog.repository.ProductTagRepository;
import org.ecommerce.catelog.repository.TagRepository;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.exception.ResourceAlreadyExistsException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.common.utils.SlugUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTagService {
    private final TagRepository tagRepository;
    private final ProductTagRepository productTagRepository;
    private final ObjectMapper objectMapper;

    public TagResponse create(TagRequest request) {
        String tagName = request.name().trim();

        if (tagRepository.existsByNameIgnoreCase(tagName)) {
            log.warn("Cannot create tag. Tag name already exists. name={}", tagName);
            throw new ResourceAlreadyExistsException("A tag with name " + tagName + " already exists.");
        }

        String slug = SlugUtils.generateSlug(tagName);

        Tag tag = Tag.builder().name(tagName).slug(slug).build();
        Tag savedTag = tagRepository.save(tag);

        log.info("Tag created successfully. tagId={}, name={}, slug={}",
                savedTag.getId(), savedTag.getName(), savedTag.getSlug());
        return objectMapper.convertValue(savedTag, TagResponse.class);
    }

    public PageResponse<TagResponse> getAll(String search, Pageable pageable) {
        Page<Tag> tags;

        if (search == null || search.isBlank()) {
            tags = tagRepository.findAll(pageable);
        } else {
            tags = tagRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        }
        Page<TagResponse> tagResponsesPage = tags.map(tag -> objectMapper.convertValue(tag, TagResponse.class));

        return PageResponse.<TagResponse>builder()
                .content(tagResponsesPage.getContent()).
                page(tagResponsesPage.getNumber())
                .size(tagResponsesPage.getSize())
                .totalElements(tagResponsesPage.getTotalElements())
                .totalPages(tagResponsesPage.getTotalPages())
                .first(tagResponsesPage.isFirst())
                .last(tagResponsesPage.isLast())
                .build();
    }

    public TagResponse update(UUID tagId, TagRequest request) {
        String tagName = request.name().trim();

        Tag tag = tagRepository.findById(tagId).orElseThrow(() -> {
            log.warn("Cannot update tag. Tag not found. tagId={}", tagId);
            return new ResourceNotFoundException("Unable to update tag because tag with id " + tagId + " was not found.");
        });

        if (tagRepository.existsByNameIgnoreCaseAndIdNot(tagName, tagId)) {
            log.warn("Cannot update tag. Tag name already exists. tagId={}, name={}", tagId, tagName);
            throw new ResourceAlreadyExistsException("A tag with name " + tagName + " already exists.");
        }

        String newSlug = SlugUtils.generateSlug(tagName);

        tag.setName(tagName);
        tag.setSlug(newSlug);

        Tag updatedTag = tagRepository.save(tag);

        log.info("Tag updated successfully. tagId={}, name={}, slug={}",
                updatedTag.getId(), updatedTag.getName(), updatedTag.getSlug());

        return objectMapper.convertValue(updatedTag, TagResponse.class);
    }

    public void delete(UUID tagId) {
        Tag tag = tagRepository.findById(tagId).orElseThrow(() -> {
            log.warn("Cannot delete tag. Tag not found. tagId={}", tagId);
            return new ResourceNotFoundException("Unable to delete tag because tag with id " + tagId + " was not found.");
        });

        productTagRepository.deleteAllByTagId(tagId);

        log.debug("Product-tag mappings removed successfully. tagId={}", tagId);

        tagRepository.delete(tag);

        log.info("Tag deleted successfully. tagId={}, name={}", tagId, tag.getName());
    }

    public TagResponse getById(UUID tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tag not found with id: " + tagId
                ));

        return objectMapper.convertValue(tag, TagResponse.class);
    }

    public PageResponse<TagOptionResponse> getTagOptions(Pageable pageable) {
        Page<TagOptionResponse> tags = tagRepository.findTagOptions(pageable);

        return PageResponse.<TagOptionResponse>builder()
                .content(tags.getContent())
                .page(tags.getNumber())
                .size(tags.getSize())
                .totalElements(tags.getTotalElements())
                .totalPages(tags.getTotalPages())
                .last(tags.isLast())
                .build();
    }
}
