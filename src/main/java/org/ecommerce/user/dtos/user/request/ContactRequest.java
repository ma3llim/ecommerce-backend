package org.ecommerce.user.dtos.user.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @NotBlank(message = "Subject is required")
        @Size(max = 255, message = "Subject cannot exceed 255 characters")
        String subject,

        @NotBlank(message = "Message is required")
        @Size(max = 5000, message = "Message cannot exceed 5000 characters")
        String message
) {
}