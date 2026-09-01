package org.ecommerce.user.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.ecommerce.user.dtos.user.request.ContactRequest;
import org.ecommerce.user.service.user.ContactService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
@Tag(name = "Contact", description = "Public APIs for submitting contact messages")
public class ContactController {
    private final ContactService contactService;

    @Operation(summary = "Submit contact message", description = "Submit a message through the public contact form")
    @PostMapping
    public ResponseEntity<ApiSuccessResponse<Void>> createContactMessage(
            @Valid @RequestBody ContactRequest request, HttpServletRequest HttpRequest
    ) {
        contactService.createContactMessage(request);

        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder()
                .success(true)
                .message("Contact message submitted successfully")
                .data(null)
                .path(HttpRequest.getRequestURI())
                .build());
    }
}
