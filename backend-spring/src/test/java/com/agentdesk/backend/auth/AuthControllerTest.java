package com.agentdesk.backend.auth;

import com.agentdesk.backend.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void registerCreatesDefaultUserStateAndReturnsTokens() throws Exception {
        JsonNode response = register("register-" + UUID.randomUUID() + "@example.com", "Register User");

        assertThat(response.path("code").asText()).isEqualTo("0");
        assertThat(response.path("data").path("access_token").asText()).isNotBlank();
        assertThat(response.path("data").path("refresh_token").asText()).isNotBlank();
        assertThat(response.path("data").path("token_type").asText()).isEqualTo("Bearer");
        assertThat(response.path("data").path("default_project_id").asText()).isNotBlank();
        assertThat(response.path("data").path("user").path("default_project_id").asText()).isNotBlank();
        assertThat(response.path("data").path("expires_in").asLong()).isGreaterThan(0);
        assertThat(response.path("data").path("user").path("email").asText()).contains("@example.com");

        String accessToken = response.path("data").path("access_token").asText();
        mockMvc.perform(get("/api/v1/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_thread_key").value("session-review"))
                .andExpect(jsonPath("$.data.preferences").isMap());
    }

    @Test
    void loginAndMeUseBearerToken() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        register(email, "Login User");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token", not(blankOrNullString())))
                .andReturn();

        String accessToken = read(loginResult).path("data").path("access_token").asText();
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.name").value("Login User"))
                .andExpect(jsonPath("$.data.default_project_id", not(blankOrNullString())));
    }

    @Test
    void refreshRotatesRefreshToken() throws Exception {
        JsonNode registered = register("refresh-" + UUID.randomUUID() + "@example.com", "Refresh User");
        String refreshToken = registered.path("data").path("refresh_token").asText();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refresh_token", not(blankOrNullString())))
                .andReturn();

        String rotatedRefreshToken = read(refreshResult).path("data").path("refresh_token").asText();
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void preferencesPatchMergesPreferencesAndFallsBackUnknownThreadKey() throws Exception {
        JsonNode registered = register("prefs-" + UUID.randomUUID() + "@example.com", "Prefs User");
        String accessToken = registered.path("data").path("access_token").asText();

        mockMvc.perform(patch("/api/v1/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active_thread_key": "missing-thread",
                                  "preferences": {"theme": "dark"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_thread_key").value("session-review"))
                .andExpect(jsonPath("$.data.preferences.theme").value("dark"));

        mockMvc.perform(patch("/api/v1/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferences": {"sidebar_collapsed": true}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferences.theme").value("dark"))
                .andExpect(jsonPath("$.data.preferences.sidebar_collapsed").value(true));

        mockMvc.perform(get("/api/v1/me/preferences").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_thread_key").value("session-review"))
                .andExpect(jsonPath("$.data.preferences.theme").value("dark"))
                .andExpect(jsonPath("$.data.preferences.sidebar_collapsed").value(true));
    }

    @Test
    void protectedEndpointsReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id", not(blankOrNullString())));
    }

    @Test
    void invalidBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.com";
        RegisteredUser registeredUser = authRepository.createUserWithDefaults(
                email,
                "Expired User",
                passwordEncoder.encode("password-123")
        );
        String expiredToken = jwtTokenService.issueAccessToken(registeredUser.user(), Duration.ofSeconds(-1));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidRequestFormatsUseApiResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","name":"","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        JsonNode registered = register("bad-prefs-" + UUID.randomUUID() + "@example.com", "Bad Prefs User");
        String accessToken = registered.path("data").path("access_token").asText();
        mockMvc.perform(patch("/api/v1/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferences": "dark"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private JsonNode register(String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"%s","password":"password-123"}
                                """.formatted(email, name)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
