package org.ecommerce.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.auth.Dtos.request.LoginRequestDto;
import org.ecommerce.auth.Dtos.request.RegisterUserRequestDto;
import org.ecommerce.auth.Dtos.request.ResetPasswordRequestDto;
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
import org.ecommerce.auth.utils.PasswordUtils;
import org.ecommerce.auth.utils.TokenUtils;
import org.ecommerce.common.config.properties.JwtProperties;
import org.ecommerce.common.constants.AppConstants;
import org.ecommerce.common.exception.BadRequestException;
import org.ecommerce.common.exception.ResourceAlreadyExistsException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.common.exception.UnauthorizedException;
import org.ecommerce.common.notification.dtos.NotificationRequest;
import org.ecommerce.common.notification.enums.channel.NotificationChannel;
import org.ecommerce.common.notification.enums.channel.NotificationEvent;
import org.ecommerce.common.notification.service.NotificationService;
import org.ecommerce.common.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordUtils passwordUtils;
    private final NotificationService notificationService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;

    public UserResponseDto registerUser(RegisterUserRequestDto requestDto) {
        // check user is existed or not
        if (userRepository.findByEmail(requestDto.email()).isPresent()) {
            log.warn("User registration rejected: email already exists");
            throw new ResourceAlreadyExistsException("Email already exists");
        }

        // encode the password
        String hashPassword = passwordUtils.encode(requestDto.password());
        // save user
        User user = User.builder()
                .firstName(requestDto.firstName())
                .lastName(requestDto.lastName())
                .email(requestDto.email())
                .password(hashPassword)
                .build();

        userRepository.save(user);
        log.info("User created successfully: userId={}", user.getId());

        // Generate Otp and Verification Code
        String otp = TokenUtils.generateOtp();

        // save otp
        OtpVerification otpVerification = OtpVerification.builder()
                .purpose(OtpPurpose.EMAIL_VERIFICATION)
                .userId(user.getId())
                .otpCode(otp)
                .expiresAt(Instant.now().plus(AppConstants.OTP_TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .status(OtpStatus.PENDING)
                .build();

        otpVerificationRepository.save(otpVerification);
        log.info(
                "Email verification initialized successfully: userId={}, purpose={}",
                user.getId(),
                OtpPurpose.EMAIL_VERIFICATION
        );

        // send mail
        sendOtpMail(user.getFullName(), otp, String.valueOf(AppConstants.OTP_TOKEN_EXPIRY_MINUTES), user.getEmail());

        // Last Log
        log.info("User registration completed successfully: userId={}", user.getId());
        return objectMapper.convertValue(user, UserResponseDto.class);
    }

    public UserAndTokenResponseDto verifyEmail(VerifyEmailRequestDto verifyEmailRequest) {
        // check user is existed or not
        User user = userRepository.findById(verifyEmailRequest.userId()).orElseThrow(() -> {
            log.warn("User not found for email verification, userId={}", verifyEmailRequest.userId());
            return new ResourceNotFoundException("User not found");
        });

        OtpVerification otpVerification = otpVerificationRepository.findByUserIdAndPurposeAndStatus(
                user.getId(),
                OtpPurpose.EMAIL_VERIFICATION,
                OtpStatus.PENDING).orElseThrow(() -> {
            log.warn("Pending OTP not found for userId={}", user.getId());
            return new BadCredentialsException("Invalid OTP");
        });

        if (otpVerification.getAttemptCount() >= AppConstants.MAX_OTP_ATTEMPTS) {
            throw new BadCredentialsException("Maximum OTP attempts exceeded");
        }

        if (Instant.now().isAfter(otpVerification.getExpiresAt())) {
            otpVerification.setStatus(OtpStatus.EXPIRED);

            otpVerificationRepository.save(otpVerification);
            throw new BadCredentialsException("OTP has expired");
        }

        if (!verifyEmailRequest.otp().equals(otpVerification.getOtpCode())) {
            otpVerification.setAttemptCount(otpVerification.getAttemptCount() + 1);
            otpVerificationRepository.save(otpVerification);

            log.warn("Invalid OTP attempt for userId={}, attemptCount={}", user.getId(),
                    otpVerification.getAttemptCount());

            throw new BadCredentialsException("Invalid OTP");
        }
        // update the OTP
        otpVerification.setStatus(OtpStatus.VERIFIED);
        otpVerification.setVerifiedAt(Instant.now());
        // Set User as Active
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);

        // Generate Token
        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user, tokenId.toString());
        log.info("verify email: Access and refresh tokens generated successfully for userId={}", user.getId());

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .id(tokenId)
                .userId(user.getId())
                .refreshToken(refreshToken)
                .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpiration()))
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        UserResponseDto userResponseDto = objectMapper.convertValue(user, UserResponseDto.class);

        WelcomeUserMail(user.getFullName(), user.getEmail());

        return new UserAndTokenResponseDto(accessToken, refreshToken, userResponseDto);
    }

    public void resendVerification(UUID userId) {
        // check user is existed or not
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("User not found for resend email verification, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        if (user.isEmailVerified()) {
            log.warn("Email already verified, userId={}", userId);
            throw new BadRequestException("Email is already verified");
        }

        otpVerificationRepository.findByUserIdAndPurposeAndStatus(userId, OtpPurpose.EMAIL_VERIFICATION,
                OtpStatus.PENDING).ifPresent(currentOpt -> {
            currentOpt.setStatus(OtpStatus.EXPIRED);
            otpVerificationRepository.save(currentOpt);
        });

        // generate new otp and send mail
        // Generate Otp and Verification Code
        String otp = TokenUtils.generateOtp();

        // save otp
        OtpVerification otpVerification = OtpVerification.builder()
                .purpose(OtpPurpose.EMAIL_VERIFICATION)
                .userId(user.getId())
                .otpCode(otp)
                .expiresAt(Instant.now().plus(AppConstants.OTP_TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .status(OtpStatus.PENDING)
                .build();

        otpVerificationRepository.save(otpVerification);

        log.info("Email verification OTP generated and saved successfully, userId={}, purpose={}", user.getId(),
                OtpPurpose.EMAIL_VERIFICATION);

        // send mail
        sendOtpMail(user.getFullName(), otp, String.valueOf(AppConstants.OTP_TOKEN_EXPIRY_MINUTES), user.getEmail());
        log.info("Email verification OTP resent successfully, userId={}", user.getId());
    }

    public UserAndTokenResponseDto login(@Valid LoginRequestDto loginData) {
        // check user is existed or not
        User user = userRepository.findByEmail(loginData.email()).orElseThrow(() -> {
            log.warn("User not found for login, email={}", loginData.email());
            return new ResourceNotFoundException("User not found");
        });
        if (!user.isEmailVerified()) {
            log.warn("Login attempt with unverified email={}", loginData.email());
            throw new BadCredentialsException("Email is not verified");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            log.warn("Login attempt with inactive account, email={}", loginData.email());
            throw new BadCredentialsException("Account is not active");
        }

        if (!passwordUtils.passwordMatches(loginData.password(), user.getPassword())) {
            log.warn("Login attempt with incorrect password, email={}", loginData.email());
            throw new BadCredentialsException("Invalid password");
        }
        // updating user last time
        user.setLastLoginAt(Instant.now());

        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user, tokenId.toString());
        log.info("login: Access and refresh tokens generated successfully for userId={}", user.getId());

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .id(tokenId)
                .userId(user.getId())
                .refreshToken(refreshToken)
                .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpiration()))
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        UserResponseDto userResponseDto = objectMapper.convertValue(user, UserResponseDto.class);

        return new UserAndTokenResponseDto(accessToken, refreshToken, userResponseDto);
    }

    @Transactional
    public UserAndTokenResponseDto refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Authentication is required");
        }

        if (!jwtService.validateRefreshToken(refreshToken)) {
            log.warn("refresh token request failed: Invalid refresh token received");
            throw new UnauthorizedException("Invalid refresh token");
        }
        Claims refreshTokenClaims = jwtService.extractClaims(refreshToken);

        // Token ID
        UUID tokenId = UUID.fromString(jwtService.getJwtId(refreshTokenClaims));
        UUID userId = jwtService.getUserId(refreshTokenClaims);

        RefreshToken tokenEntity = refreshTokenRepository.findById(tokenId).orElseThrow(() -> {
            log.warn("refresh token request failed: Refresh token not found, tokenId={}, userId={}", tokenId, userId);
            return new UnauthorizedException("Invalid refresh token");
        });

        if (Instant.now().isAfter(tokenEntity.getExpiresAt())) {
            log.warn("Refresh token expired in database, userId={}", userId);
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (!tokenEntity.getUserId().equals(userId)) {
            log.warn("Refresh token user mismatch, tokenId={}, userId={}", tokenId, userId);
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (tokenEntity.isRevoked()) {
            log.warn("Attempt to reuse revoked refresh token, userId={}", userId);
            throw new UnauthorizedException("Invalid refresh token");
        }

        // check user is existed or not
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("User not found for revoked token, UserId={}", userId);
            return new ResourceNotFoundException("User not found");
        });
        // create new refresh and access token
        UUID newTokenId = UUID.randomUUID();
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user, newTokenId.toString());
        log.info("Access and refresh tokens generated successfully for userId={}", user.getId());

        tokenEntity.setRevokedByTokenId(newTokenId);
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        //  saving new token
        RefreshToken newRefreshTokenEntity = RefreshToken.builder()
                .id(newTokenId)
                .userId(userId)
                .refreshToken(newRefreshToken)
                .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpiration()))
                .build();

        refreshTokenRepository.save(newRefreshTokenEntity);
        UserResponseDto userResponseDto = objectMapper.convertValue(user, UserResponseDto.class);

        return new UserAndTokenResponseDto(newAccessToken, newRefreshToken, userResponseDto);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is required");
        }

        if (!jwtService.validateRefreshToken(refreshToken)) {
            log.warn("Invalid refresh token received");
            throw new UnauthorizedException("Invalid refresh token");
        }
        Claims refreshTokenClaims = jwtService.extractClaims(refreshToken);

        // Token ID
        UUID tokenId = UUID.fromString(jwtService.getJwtId(refreshTokenClaims));
        UUID userId = jwtService.getUserId(refreshTokenClaims);

        RefreshToken tokenEntity = refreshTokenRepository.findById(tokenId).orElseThrow(() -> {
            log.warn("Refresh token not found, tokenId={}, userId={}", tokenId, userId);
            return new UnauthorizedException("Invalid refresh token");
        });
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);
    }

    public UUID forgotPassword(String email) {
        // check user is existed or not
        User user = userRepository.findByEmail(email).orElseThrow(() -> {
            log.warn("User not found for password reset, email={}", email);
            return new ResourceNotFoundException("User not found");
        });
        // Generate reset OTP
        String forgotOtp = TokenUtils.generateOtp();


        OtpVerification forgotOtpVerification = OtpVerification.builder()
                .userId(user.getId())
                .purpose(OtpPurpose.PASSWORD_RESET)
                .otpCode(forgotOtp)
                .expiresAt(Instant.now().plus(AppConstants.OTP_TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .status(OtpStatus.PENDING)
                .build();
        otpVerificationRepository.save(forgotOtpVerification);

        log.info("Password reset OTP generated and saved successfully, userId={}, purpose={}", user.getId(), OtpPurpose.PASSWORD_RESET);

        sendForgotOtpMail(user.getFullName(), forgotOtp, String.valueOf(AppConstants.OTP_TOKEN_EXPIRY_MINUTES), user.getEmail());
        log.info("Password reset OTP email sent successfully, userId={}", user.getId());

        return user.getId();
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public void resetPassword(ResetPasswordRequestDto resetPasswordDto) {
        // check user is existed or not
        User user = userRepository.findById(resetPasswordDto.userId()).orElseThrow(() -> {
            log.warn("User not found during password reset OTP verification, userId={}", resetPasswordDto.userId());
            return new BadCredentialsException("Invalid OTP");
        });

        OtpVerification otpVerification = otpVerificationRepository.findByUserIdAndPurposeAndStatus(
                user.getId(),
                OtpPurpose.PASSWORD_RESET,
                OtpStatus.PENDING
        ).orElseThrow(() -> {
            log.warn("Pending password reset OTP not found, userId={}", user.getId());
            return new BadCredentialsException("Invalid OTP");
        });

        if (otpVerification.getAttemptCount() >= AppConstants.MAX_OTP_ATTEMPTS) {
            log.warn("Maximum password reset OTP attempts exceeded, userId={}", user.getId());
            throw new BadCredentialsException("Maximum OTP attempts exceeded");
        }
        if (Instant.now().isAfter(otpVerification.getExpiresAt())) {
            otpVerification.setStatus(OtpStatus.EXPIRED);

            otpVerificationRepository.save(otpVerification);
            throw new BadCredentialsException("OTP has expired");
        }

        if (!otpVerification.getOtpCode().equals(resetPasswordDto.otp())) {
            otpVerification.setAttemptCount(otpVerification.getAttemptCount() + 1);
            log.warn("Invalid OTP attempt for reset password userId={}, attemptCount={}", user.getId(), otpVerification.getAttemptCount());
            throw new BadCredentialsException("Invalid OTP");
        }

        // hash password and update in user table
        String hashPassword = passwordUtils.encode(resetPasswordDto.password());
        user.setPassword(hashPassword);
        user.setPasswordChangedAt(Instant.now());
        // update token table
        otpVerification.setStatus(OtpStatus.VERIFIED);

        refreshTokenRepository.findAllByUserId(user.getId())
                .forEach(refreshToken -> refreshToken.setRevoked(true));
    }

    // Notifications Functions
    // Send OTP to user
    void sendOtpMail(String fullName, String otp, String expiryMinutes, String recipientEmail) {
        Map<String, Object> data = new HashMap<>();
        data.put("fullName", fullName);
        data.put("otp", otp);
        data.put("expiryMinutes", expiryMinutes);

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.OTP_VERIFICATION)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }

    // forgot OTP
    void sendForgotOtpMail(String fullName, String otp, String expiryMinutes, String recipientEmail) {
        Map<String, Object> data = new HashMap<>();
        data.put("fullName", fullName);
        data.put("otp", otp);
        data.put("expiryMinutes", expiryMinutes);

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.FORGET_PASSWORD_VERIFICATION)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }

    // Welcome Mail
    void WelcomeUserMail(String fullName, String recipientEmail) {
        Map<String, Object> data = new HashMap<>();
        data.put("fullName", fullName);

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.USER_REGISTERED)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }
}
