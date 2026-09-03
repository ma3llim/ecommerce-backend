package org.ecommerce.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ecommerce.auth.Dtos.request.*;
import org.ecommerce.auth.Dtos.response.UserAndTokenResponseDto;
import org.ecommerce.auth.Dtos.response.UserResponseDto;
import org.ecommerce.auth.enums.AccountStatus;
import org.ecommerce.auth.enums.Role;
import org.ecommerce.auth.repository.UserRepository;
import org.ecommerce.auth.service.AuthService;
import org.ecommerce.common.config.rate.RateLimitFilter;
import org.ecommerce.common.security.JwtService;
import org.ecommerce.common.utils.CookieUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CookieUtils cookieUtils;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("Should register user successfully")
    void shouldRegisterUserSuccessfully() throws Exception {

        RegisterUserRequestDto request = new RegisterUserRequestDto(
                "John",
                "Doe",
                "john@example.com",
                "Password@123"
        );

        UUID userId = UUID.randomUUID();

        UserResponseDto responseDto = new UserResponseDto(
                userId,
                "John",
                "Doe",
                "john@example.com",
                false,
                AccountStatus.PENDING,
                Role.USER
        );

        when(authService.registerUser(any(RegisterUserRequestDto.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("User registered successfully. We have sent an OTP to your email for verification."))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        verify(authService)
                .registerUser(any(RegisterUserRequestDto.class));
    }

    @Test
    @DisplayName("Should verify email successfully and set authentication cookies")
    void shouldVerifyEmailSuccessfullyAndSetAuthenticationCookies() throws Exception {
        UUID userId = UUID.randomUUID();
        VerifyEmailRequestDto request = new VerifyEmailRequestDto(userId, "456456");

        UserResponseDto userResponseDto = new UserResponseDto(userId, "John", "Doe", "john@example.com", true, null, null);

        UserAndTokenResponseDto tokenResponse = new UserAndTokenResponseDto("access-token", "refresh-token", userResponseDto);

        when(authService.verifyEmail(any(VerifyEmailRequestDto.class))).thenReturn(tokenResponse);

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully. You are now logged in."))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/verify-email"));

        verify(authService).verifyEmail(any(VerifyEmailRequestDto.class));

        verify(cookieUtils).setAuthCookies(any(), eq("access-token"), eq("refresh-token"));
    }


    @Test
    @DisplayName("Should resend verification OTP successfully")
    void shouldResendVerificationOtpSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        ResendVerificationRequestDto request = new ResendVerificationRequestDto(userId);

        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Verification OTP resent successfully"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/resend-verification"));

        verify(authService).resendVerification(userId);
    }


    @Test
    @DisplayName("Should login successfully and set authentication cookies")
    void shouldLoginSuccessfullyAndSetAuthenticationCookies() throws Exception {
        LoginRequestDto request = new LoginRequestDto("john@example.com", "Password@123");
        UUID userId = UUID.randomUUID();

        UserResponseDto userResponseDto = new UserResponseDto(userId, "John", "Doe", "john@example.com", true, null, null);

        UserAndTokenResponseDto tokenResponse = new UserAndTokenResponseDto("access-token", "refresh-token", userResponseDto);

        when(authService.login(any(LoginRequestDto.class))).thenReturn(tokenResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Login successful"))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/auth/login"));

        verify(authService)
                .login(any(LoginRequestDto.class));

        verify(cookieUtils)
                .setAuthCookies(
                        any(),
                        eq("access-token"),
                        eq("refresh-token")
                );
    }


    @Test
    @DisplayName("Should refresh tokens successfully and update authentication cookies")
    void shouldRefreshTokensSuccessfullyAndUpdateAuthenticationCookies()
            throws Exception {

        UUID userId = UUID.randomUUID();

        UserResponseDto userResponseDto = new UserResponseDto(
                userId,
                "John",
                "Doe",
                "john@example.com",
                true,
                null,
                null
        );

        UserAndTokenResponseDto tokenResponse =
                new UserAndTokenResponseDto(
                        "new-access-token",
                        "new-refresh-token",
                        userResponseDto
                );

        when(cookieUtils.getRefreshToken(any()))
                .thenReturn("old-refresh-token");

        when(authService.refreshToken("old-refresh-token"))
                .thenReturn(tokenResponse);

        mockMvc.perform(post("/api/v1/auth/refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Refresh Token Successfully"))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/auth/refresh-token"));

        verify(cookieUtils)
                .getRefreshToken(any());

        verify(authService)
                .refreshToken("old-refresh-token");

        verify(cookieUtils)
                .setAuthCookies(
                        any(),
                        eq("new-access-token"),
                        eq("new-refresh-token")
                );
    }


    @Test
    @DisplayName("Should logout successfully and clear authentication cookies")
    void shouldLogoutSuccessfullyAndClearAuthenticationCookies()
            throws Exception {

        when(cookieUtils.getRefreshToken(any()))
                .thenReturn("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Logout successful"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/auth/logout"));

        verify(cookieUtils)
                .getRefreshToken(any());

        verify(authService)
                .logout("refresh-token");

        verify(cookieUtils)
                .clearAuthCookies(any());
    }


    @Test
    @DisplayName("Should send password reset OTP successfully")
    void shouldSendPasswordResetOtpSuccessfully() throws Exception {

        String email = "john@example.com";
        UUID userId = UUID.randomUUID();

        ForgotPasswordRequestDto request =
                new ForgotPasswordRequestDto(email);

        when(authService.forgotPassword(email))
                .thenReturn(userId);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Password reset OTP sent successfully"))
                .andExpect(jsonPath("$.data").value(userId.toString()))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/auth/forgot-password"));

        verify(authService)
                .forgotPassword(email);
    }


    @Test
    @DisplayName("Should reset password successfully and clear authentication cookies")
    void shouldResetPasswordSuccessfullyAndClearAuthenticationCookies() throws Exception {
        UUID userId = UUID.randomUUID();
        ResetPasswordRequestDto request = new ResetPasswordRequestDto(userId, "456456", "newPassword@123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Password reset successfully"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/auth/reset-password"));

        verify(authService)
                .resetPassword(any(ResetPasswordRequestDto.class));

        verify(cookieUtils)
                .clearAuthCookies(any());
    }
}
