package com.agentdesk.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ragUploadRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.md",
                "text/markdown",
                "# Notes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/threads/{threadId}/rag/uploads", UUID.randomUUID())
                        .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id", not(blankOrNullString())));
    }

    @Test
    void fileUploadCreatesDocumentAndIndexJob() throws Exception {
        AuthSession session = register("b11-" + UUID.randomUUID() + "@example.com");
        UUID threadId = firstThreadId(session);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "login-notes.md",
                "text/markdown",
                "# Login Notes\n用户登录流程说明".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/threads/{threadId}/rag/uploads", threadId)
                        .file(file)
                        .param("scope", "thread")
                        .param("source_path", "docs/login-notes.md")
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.document.source_name").value("login-notes.md"))
                .andExpect(jsonPath("$.data.document.source_path").value("docs/login-notes.md"))
                .andExpect(jsonPath("$.data.document.status").value("uploaded"))
                .andExpect(jsonPath("$.data.document.storage_uri").value(org.hamcrest.Matchers.matchesPattern("^(memory|s3)://.+")))
                .andExpect(jsonPath("$.data.document.sha256", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.document.metadata.upload_entry").value("composer_file_button"))
                .andExpect(jsonPath("$.data.job.status").value("queued"))
                .andExpect(jsonPath("$.data.stream").value("rag.index.jobs"));

        mockMvc.perform(get("/api/v1/threads/{threadId}/rag/documents", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].source_name").value("login-notes.md"))
                .andExpect(jsonPath("$.data.items[0].status").value("uploaded"));
    }

    @Test
    void crossUserRagUploadIsRejected() throws Exception {
        AuthSession owner = register("b11-owner-" + UUID.randomUUID() + "@example.com");
        AuthSession other = register("b11-other-" + UUID.randomUUID() + "@example.com");
        UUID threadId = firstThreadId(owner);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "private.md",
                "text/markdown",
                "private".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/threads/{threadId}/rag/uploads", threadId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private UUID firstThreadId(AuthSession session) throws Exception {
        JsonNode folders = read(mockMvc.perform(get("/api/v1/projects/{projectId}/folders?include_threads=true", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        return UUID.fromString(folders.path("data").path("items").get(0).path("threads").get(0).path("id").asText());
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"B11 User","password":"password-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = read(result).path("data");
        return new AuthSession(data.path("access_token").asText(), UUID.fromString(data.path("default_project_id").asText()));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(String accessToken, UUID projectId) {
    }
}
