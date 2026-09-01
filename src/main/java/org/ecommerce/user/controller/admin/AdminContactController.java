package org.ecommerce.user.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.ecommerce.user.dtos.admin.response.ContactDetailsResponse;
import org.ecommerce.user.dtos.admin.response.ContactListResponse;
import org.ecommerce.user.service.admin.AdminContactService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Contact", description = "Admin APIs for managing contact messages")
public class AdminContactController {
    private final AdminContactService adminContactService;

    @Operation(summary = "Get all contact messages", description = "Retrieve contact messages with pagination")
    @GetMapping
    public ResponseEntity<ApiSuccessResponse<PageResponse<ContactListResponse>>> getAllContacts(Pageable pageable, HttpServletRequest request) {

        PageResponse<ContactListResponse> allContacts = adminContactService.getAllContacts(pageable);

        return ResponseEntity.ok(ApiSuccessResponse.<PageResponse<ContactListResponse>>builder()
                .success(true)
                .message("Contact messages retrieved successfully")
                .data(allContacts)
                .path(request.getRequestURI())
                .build());
    }

    @Operation(summary = "Get contact message details", description = "Retrieve complete details of a contact message by ID")
    @GetMapping("/{contactId}")
    public ResponseEntity<ApiSuccessResponse<ContactDetailsResponse>> getContactById(@PathVariable UUID contactId, HttpServletRequest request) {

        ContactDetailsResponse contactDetailsResponse = adminContactService.getContactById(contactId);

        return ResponseEntity.ok(ApiSuccessResponse.<ContactDetailsResponse>builder()
                .success(true)
                .message("Contact message retrieved successfully")
                .data(contactDetailsResponse)
                .path(request.getRequestURI())
                .build());
    }
}
