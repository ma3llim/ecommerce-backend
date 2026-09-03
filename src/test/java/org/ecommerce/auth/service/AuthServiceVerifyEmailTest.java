package org.ecommerce.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ecommerce.auth.Dtos.request.RegisterUserRequestDto;
import org.ecommerce.auth.Dtos.request.VerifyEmailRequestDto;
import org.ecommerce.auth.Dtos.response.UserAndTokenResponseDto;
import org.ecommerce.auth.Dtos.response.UserResponseDto;
import org.ecommerce.auth.entities.OtpVerification;
import org.ecommerce.auth.entities.RefreshToken;
import org.ecommerce.auth.entities.User;
import org.ecommerce.auth.enums.AccountStatus;
import org.ecommerce.auth.enums.OtpPurpose;
import org.ecommerce.auth.enums.OtpStatus;
import org.ecommerce.auth.repository.OtpVerificationRepository;
import org.ecommerce.auth.repository.RefreshTokenRepository;
import org.ecommerce.auth.repository.UserRepository;
import org.ecommerce.common.config.properties.JwtProperties;
import org.ecommerce.common.constants.AppConstants;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.common.notification.service.NotificationService;
import org.ecommerce.common.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthServiceVerifyEmailTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthService authService;

    @Mock
    private NotificationService notificationService;

    private RegisterUserRequestDto registerRequest;
    private User user;
    private OtpVerification otpVerification;


    @Test
    @DisplayName("Should throw exception when user does not exist")
    void shouldThrowExceptionWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();

        VerifyEmailRequestDto request = new VerifyEmailRequestDto(userId, "456456");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.verifyEmail(request));
    }

    @Test
    @DisplayName("Should throw exception when pending verification OTP does not exist")
    void shouldThrowExceptionWhenPendingOtpDoesNotExist() {
        UUID userId = UUID.randomUUID();

        VerifyEmailRequestDto request = new VerifyEmailRequestDto(userId, "456456");

        User user = User.builder().id(userId).email("john@example.com").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(otpVerificationRepository.findByUserIdAndPurposeAndStatus(
                userId,
                OtpPurpose.EMAIL_VERIFICATION,
                OtpStatus.PENDING
        )).thenReturn(Optional.empty());

        assertThrows(
                BadCredentialsException.class,
                () -> authService.verifyEmail(request)
        );
    }

    @Test
    @DisplayName("Should throw exception when maximum OTP attempts are reached")
    void shouldThrowExceptionWhenMaximumOtpAttemptsReached() {
        UUID userId = UUID.randomUUID();

        VerifyEmailRequestDto request = new VerifyEmailRequestDto(userId, "456456");

        User user = User.builder().id(userId).email("john@example.com").build();

        OtpVerification otp = OtpVerification.builder()
                .userId(userId)
                .otpCode("456456")
                .purpose(OtpPurpose.EMAIL_VERIFICATION)
                .status(OtpStatus.PENDING)
                .attemptCount(AppConstants.MAX_OTP_ATTEMPTS)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(otpVerificationRepository.findByUserIdAndPurposeAndStatus(
                userId,
                OtpPurpose.EMAIL_VERIFICATION,
                OtpStatus.PENDING
        )).thenReturn(Optional.of(otp));

        assertThrows(BadCredentialsException.class, () -> authService.verifyEmail(request));
    }

    @Test
    @DisplayName("Should mark OTP as expired and throw exception when OTP has expired")
    void shouldMarkOtpExpiredAndThrowExceptionWhenOtpHasExpired() {
        UUID userId = UUID.randomUUID();

        VerifyEmailRequestDto request = new VerifyEmailRequestDto(userId, "456456");

        User user = User.builder().id(userId).email("john@example.com").build();

        OtpVerification otp = OtpVerification.builder()
                .userId(userId)
                .otpCode("456456")
                .purpose(OtpPurpose.EMAIL_VERIFICATION)
                .status(OtpStatus.PENDING)
                .attemptCount(0)
                .expiresAt(Instant.now().minusSeconds(10))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(otpVerificationRepository.findByUserIdAndPurposeAndStatus(
                userId,
                OtpPurpose.EMAIL_VERIFICATION,
                OtpStatus.PENDING
        )).thenReturn(Optional.of(otp));

        assertThrows(BadCredentialsException.class, () -> authService.verifyEmail(request));

        assertEquals(OtpStatus.EXPIRED, otp.getStatus());

        verify(otpVerificationRepository).save(otp);
    }

    @Test
    @DisplayName("Should increase attempt count and throw exception when OTP is incorrect")
    void shouldIncreaseAttemptCountAndThrowExceptionWhenOtpIsIncorrect() {
        UUID userId = UUID.randomUUID();

        VerifyEmailRequestDto request = new VerifyEmailRequestDto(userId, "111111");

        User user = User.builder().id(userId).email("john@example.com").build();

        OtpVerification otp = OtpVerification.builder()
                .userId(userId)
                .otpCode("456456")
                .purpose(OtpPurpose.EMAIL_VERIFICATION)
                .status(OtpStatus.PENDING)
                .attemptCount(1)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(otpVerificationRepository.findByUserIdAndPurposeAndStatus(
                userId,
                OtpPurpose.EMAIL_VERIFICATION,
                OtpStatus.PENDING
        )).thenReturn(Optional.of(otp));

        assertThrows(BadCredentialsException.class, () -> authService.verifyEmail(request));

        assertEquals(2, otp.getAttemptCount());

        verify(otpVerificationRepository).save(otp);
    }

    @Test
    @DisplayName("Should verify email and return tokens when OTP is correct")
    void shouldVerifyEmailAndReturnTokensWhenOtpIsCorrect() {
        UUID userId = UUID.randomUUID();

        VerifyEmailRequestDto request = new VerifyEmailRequestDto(userId, "456456");

        User user = User.builder().id(userId).email("john@example.com").build();

        OtpVerification otp = OtpVerification.builder()
                .userId(userId)
                .otpCode("456456")
                .purpose(OtpPurpose.EMAIL_VERIFICATION)
                .status(OtpStatus.PENDING)
                .attemptCount(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        UserResponseDto userResponseDto = new UserResponseDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getAccountStatus(),
                user.getRole()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(otpVerificationRepository.findByUserIdAndPurposeAndStatus(
                userId,
                OtpPurpose.EMAIL_VERIFICATION,
                OtpStatus.PENDING
        )).thenReturn(Optional.of(otp));

        when(jwtService.generateAccessToken(user)).thenReturn("access-token");

        when(jwtService.generateRefreshToken(eq(user), anyString())).thenReturn("refresh-token");

        when(jwtProperties.getRefreshTokenExpiration())
                .thenReturn(3600L);

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(objectMapper.convertValue(
                eq(user),
                eq(UserResponseDto.class)
        )).thenReturn(userResponseDto);

        UserAndTokenResponseDto result = authService.verifyEmail(request);

        assertNotNull(result);

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());

        assertEquals(OtpStatus.VERIFIED, otp.getStatus());

        assertNotNull(otp.getVerifiedAt());

        assertTrue(user.isEmailVerified());

        assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());

        verify(refreshTokenRepository).save(any(RefreshToken.class));

        verify(objectMapper).convertValue(user, UserResponseDto.class);
    }
}
