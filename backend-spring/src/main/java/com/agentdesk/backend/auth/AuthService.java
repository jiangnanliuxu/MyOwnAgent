package com.agentdesk.backend.auth;

import com.agentdesk.backend.common.error.AuthenticationException;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.security.AuthProperties;
import com.agentdesk.backend.security.AuthenticatedUser;
import com.agentdesk.backend.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String TOKEN_TYPE = "Bearer";

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuthProperties authProperties
    ) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authProperties = authProperties;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (authRepository.userEmailExists(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email is already registered.");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        RegisteredUser registeredUser = authRepository.createUserWithDefaults(email, request.name().trim(), passwordHash);
        TokenBundle tokenBundle = issueTokens(registeredUser.user());
        return new AuthResponse(
                UserResponse.from(registeredUser.user(), registeredUser.projectId()),
                registeredUser.projectId(),
                tokenBundle.accessToken(),
                tokenBundle.refreshToken(),
                tokenBundle.tokenType(),
                tokenBundle.expiresIn()
        );
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserAccountWithPassword account = authRepository.findUserByEmailWithPassword(email)
                .orElseThrow(() -> new AuthenticationException("Invalid email or password."));
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new AuthenticationException("Invalid email or password.");
        }

        TokenBundle tokenBundle = issueTokens(account.user());
        UUID defaultProjectId = authRepository.findDefaultProjectId(account.user().id())
                .orElseThrow(() -> new AuthenticationException("Default project is not available."));
        return new AuthResponse(
                UserResponse.from(account.user(), defaultProjectId),
                defaultProjectId,
                tokenBundle.accessToken(),
                tokenBundle.refreshToken(),
                tokenBundle.tokenType(),
                tokenBundle.expiresIn()
        );
    }

    public TokenResponse refresh(RefreshRequest request) {
        String tokenHash = hashToken(request.refreshToken());
        StoredRefreshToken storedRefreshToken = authRepository.findValidRefreshToken(tokenHash, Instant.now())
                .orElseThrow(() -> new AuthenticationException("Invalid refresh token."));
        UserAccount user = authRepository.findUserById(storedRefreshToken.userId())
                .orElseThrow(() -> new AuthenticationException("Invalid refresh token."));

        authRepository.revokeRefreshToken(storedRefreshToken.id(), Instant.now());
        TokenBundle tokenBundle = issueTokens(user);
        return new TokenResponse(
                tokenBundle.accessToken(),
                tokenBundle.refreshToken(),
                tokenBundle.tokenType(),
                tokenBundle.expiresIn()
        );
    }

    public UserResponse me(AuthenticatedUser authenticatedUser) {
        UserAccount user = authRepository.findUserById(authenticatedUser.id())
                .orElseThrow(() -> new AuthenticationException("Authentication is required."));
        UUID defaultProjectId = authRepository.findDefaultProjectId(user.id()).orElse(null);
        return UserResponse.from(user, defaultProjectId);
    }

    private TokenBundle issueTokens(UserAccount user) {
        String accessToken = jwtTokenService.issueAccessToken(user);
        String refreshToken = generateOpaqueRefreshToken();
        authRepository.saveRefreshToken(
                user.id(),
                hashToken(refreshToken),
                Instant.now().plus(authProperties.refreshTokenTtl())
        );
        return new TokenBundle(accessToken, refreshToken, TOKEN_TYPE, authProperties.accessTokenTtl().toSeconds());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return TOKEN_ENCODER.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token.", exception);
        }
    }
}
