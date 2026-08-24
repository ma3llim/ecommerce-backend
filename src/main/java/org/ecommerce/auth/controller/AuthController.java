package org.ecommerce.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.auth.Dtos.request.*;
import org.ecommerce.auth.Dtos.response.UserAndAccessToken;
import org.ecommerce.auth.Dtos.response.UserAndTokenResponseDto;
import org.ecommerce.auth.Dtos.response.UserResponseDto;
import org.ecommerce.auth.service.AuthService;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.ecommerce.common.utils.CookieUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(
        name = "Authentication",
        description = "APIs for user registration, authentication, email verification, password management, and logout"
)
public class AuthController {
    private final AuthService authService;
    private final CookieUtils cookieUtils;

    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account and sends an OTP to the user's email for verification"
    )
    @PostMapping("/register")
    public ResponseEntity<ApiSuccessResponse<UserResponseDto>> registerUser(@Valid @RequestBody RegisterUserRequestDto requestDto, HttpServletRequest request) {
        UserResponseDto userResponseDto = authService.registerUser(requestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.<UserResponseDto>builder().success(true)
                .message("User registered successfully. We have sent an OTP to your email for verification.").data(userResponseDto).path(request.getRequestURI()).build()
        );
    }

    @Operation(
            summary = "Verify user email",
            description = "Verifies the user's email using the OTP and establishes authentication cookies"
    )
    @PostMapping("/verify-email")
    public ResponseEntity<ApiSuccessResponse<UserAndAccessToken>> verifyEmail(@Valid @RequestBody VerifyEmailRequestDto verifyEmailRequest, HttpServletResponse response, HttpServletRequest request) {
        UserAndTokenResponseDto userAndTokens = authService.verifyEmail(verifyEmailRequest);
        cookieUtils.setAuthCookies(response, userAndTokens.accessToken(), userAndTokens.refreshToken());

        UserAndAccessToken userAndAccessToken = UserAndAccessToken.builder().accessToken(userAndTokens.accessToken())
                .user(userAndTokens.userResponseDto()).build();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.<UserAndAccessToken>builder()
                .success(true)
                .message("Email verified successfully. You are now logged in.")
                .data(userAndAccessToken)
                .path(request.getRequestURI()).build()
        );
    }

    @Operation(
            summary = "Resend verification OTP",
            description = "Sends a new email verification OTP to the user"
    )
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiSuccessResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequestDto requestDto, HttpServletRequest request) {
        authService.resendVerification(requestDto.userId());
        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder().success(true).message("Verification OTP resent successfully").data(null).path(request.getRequestURI()).build());
    }

    @Operation(
            summary = "Authenticate user",
            description = "Authenticates the user using email and password and establishes authentication cookies"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/login")
    public ResponseEntity<ApiSuccessResponse<UserAndAccessToken>> login(@Valid @RequestBody LoginRequestDto loginData, HttpServletResponse response, HttpServletRequest request) {
        UserAndTokenResponseDto userAndToken = authService.login(loginData);

        cookieUtils.setAuthCookies(response, userAndToken.accessToken(), userAndToken.refreshToken());

        UserAndAccessToken userAndAccessToken = UserAndAccessToken.builder()
                .accessToken(userAndToken.accessToken())
                .user(userAndToken.userResponseDto())
                .build();


        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.<UserAndAccessToken>builder()
                .success(true)
                .message("Login successfully.")
                .data(userAndAccessToken)
                .path(request.getRequestURI()).build()
        );
    }

    @Operation(
            summary = "Refresh authentication tokens",
            description = "Generates new access and refresh tokens using the refresh token stored in the authentication cookie"
    )
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiSuccessResponse<UserAndAccessToken>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtils.getRefreshToken(request);
        UserAndTokenResponseDto userAndTokenResponseDto = authService.refreshToken(refreshToken);
        cookieUtils.setAuthCookies(response, userAndTokenResponseDto.accessToken(), userAndTokenResponseDto.refreshToken());

        UserAndAccessToken userAndAccessToken = UserAndAccessToken.builder().accessToken(userAndTokenResponseDto.accessToken())
                .user(userAndTokenResponseDto.userResponseDto()).build();

        return ResponseEntity.ok(ApiSuccessResponse.<UserAndAccessToken>builder()
                .success(true)
                .message("Refresh Token Successfully")
                .data(userAndAccessToken)
                .path(request.getRequestURI()).build()
        );
    }

    @Operation(
            summary = "Logout user",
            description = "Invalidates the refresh token and clears authentication cookies"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<ApiSuccessResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtils.getRefreshToken(request);
        authService.logout(refreshToken);

        cookieUtils.clearAuthCookies(response);

        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder().success(true).message("Logout successful").path(request.getRequestURI()).build());
    }

    @Operation(
            summary = "Request password reset",
            description = "Sends a password reset OTP to the user's registered email address"
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiSuccessResponse<UUID>> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto requestDto, HttpServletRequest request, HttpServletResponse response) {
        UUID userId = authService.forgotPassword(requestDto.email());
        return ResponseEntity.ok(ApiSuccessResponse.<UUID>builder().success(true).message("Password reset OTP sent successfully").data(userId).path(request.getRequestURI()).build());
    }

    @Operation(
            summary = "Reset user password",
            description = "Resets the user's password using the password reset OTP and clears existing authentication cookies"
    )
    @PostMapping("/reset-password")
    public ResponseEntity<ApiSuccessResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequestDto resetPasswordDto, HttpServletRequest request, HttpServletResponse response) {
        authService.resetPassword(resetPasswordDto);
        cookieUtils.clearAuthCookies(response);

        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder().success(true).message("Password reset successfully").path(request.getRequestURI()).build());
    }
}
