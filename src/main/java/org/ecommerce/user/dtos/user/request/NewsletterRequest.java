package org.ecommerce.user.dtos.user.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewsletterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email
) {
}