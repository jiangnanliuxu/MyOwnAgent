package com.agentdesk.backend.security;

import com.agentdesk.backend.auth.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtTokenService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JwtTokenService(AuthProperties authProperties, ObjectMapper objectMapper) {
        this(authProperties, objectMapper, Clock.systemUTC());
    }

    JwtTokenService(AuthProperties authProperties, ObjectMapper objectMapper, Clock clock) {
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String issueAccessToken(UserAccount user) {
        return issueAccessToken(user, authProperties.accessTokenTtl());
    }

    public String issueAccessToken(UserAccount user, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        return encode(Map.of(
                "typ", ACCESS_TOKEN_TYPE,
                "sub", user.id().toString(),
                "email", user.email(),
                "name", user.name(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()
        ));
    }

    public JwtAccessTokenClaims verifyAccessToken(String token) {
        JsonNode claims = decodeAndVerify(token);
        String tokenType = claims.path("typ").asText();
        if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new org.springframework.security.core.AuthenticationException("Invalid token type.") {
            };
        }

        long expiresAtEpochSecond = claims.path("exp").asLong(0);
        if (expiresAtEpochSecond <= clock.instant().getEpochSecond()) {
            throw new org.springframework.security.core.AuthenticationException("Access token has expired.") {
            };
        }

        try {
            return new JwtAccessTokenClaims(
                    UUID.fromString(claims.path("sub").asText()),
                    claims.path("email").asText(),
                    claims.path("name").asText(),
                    Instant.ofEpochSecond(expiresAtEpochSecond)
            );
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.security.core.AuthenticationException("Invalid token subject.", exception) {
            };
        }
    }

    private String encode(Map<String, Object> claims) {
        try {
            String header = BASE64_URL_ENCODER.encodeToString("""
                    {"alg":"HS256","typ":"JWT"}""".trim().getBytes(StandardCharsets.UTF_8));
            String payload = BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
            String signedContent = header + "." + payload;
            String signature = BASE64_URL_ENCODER.encodeToString(sign(signedContent));
            return signedContent + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue access token.", exception);
        }
    }

    private JsonNode decodeAndVerify(String token) {
        String[] segments = token.split("\\.");
        if (segments.length != 3) {
            throw new org.springframework.security.core.AuthenticationException("Invalid bearer token.") {
            };
        }

        String signedContent = segments[0] + "." + segments[1];
        byte[] expectedSignature = sign(signedContent);
        byte[] actualSignature;
        try {
            actualSignature = BASE64_URL_DECODER.decode(segments[2]);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.security.core.AuthenticationException("Invalid bearer token signature.", exception) {
            };
        }

        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new org.springframework.security.core.AuthenticationException("Invalid bearer token signature.") {
            };
        }

        try {
            return objectMapper.readTree(BASE64_URL_DECODER.decode(segments[1]));
        } catch (Exception exception) {
            throw new org.springframework.security.core.AuthenticationException("Invalid bearer token payload.", exception) {
            };
        }
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(authProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign access token.", exception);
        }
    }
}
