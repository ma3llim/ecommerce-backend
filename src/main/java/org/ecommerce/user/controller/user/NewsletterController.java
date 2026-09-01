package org.ecommerce.user.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.ecommerce.user.dtos.user.request.NewsletterRequest;
import org.ecommerce.user.service.user.NewsletterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/newsletter")
@Tag(name = "Newsletter", description = "Public APIs for newsletter subscription")
public class NewsletterController {
    private final NewsletterService newsletterService;

    @Operation(summary = "Subscribe to newsletter", description = "Subscribe an email address to the newsletter")
    @PostMapping
    public ResponseEntity<ApiSuccessResponse<Void>> subscribe(
            @Valid @RequestBody NewsletterRequest request,
            HttpServletRequest httpRequest
    ) {
        newsletterService.subscribe(request);
        
        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder()
                .success(true)
                .message("Email subscribed successfully")
                .data(null)
                .path(httpRequest.getRequestURI())
                .build()
        );
    }
}
