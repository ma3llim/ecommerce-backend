package org.ecommerce.user.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.ecommerce.user.dtos.admin.response.NewsletterResponse;
import org.ecommerce.user.service.admin.AdminNewsletterService;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/newsletter")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Newsletter", description = "Admin APIs for newsletter subscribers")
public class AdminNewsletterController {
    private final AdminNewsletterService adminNewsletterService;

    @Operation(summary = "Get newsletter subscribers", description = "Retrieve newsletter subscribers with pagination")
    @GetMapping("/newsletter")
    public ApiSuccessResponse<PageResponse<NewsletterResponse>> getAllSubscribers(
            Pageable pageable, HttpServletRequest httpRequest
    ) {
        PageResponse<NewsletterResponse> subscribers = adminNewsletterService.getAllSubscribers(pageable);
        return ApiSuccessResponse.<PageResponse<NewsletterResponse>>builder()
                .success(true)
                .message("Newsletter subscribers retrieved successfully")
                .data(subscribers)
                .path(httpRequest.getRequestURI())
                .build();
    }
}
