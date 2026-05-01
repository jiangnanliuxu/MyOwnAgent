package com.agentdesk.backend.health;

import com.agentdesk.backend.common.web.TraceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsUnifiedSuccessResponse() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(TraceId.HEADER_NAME, "test-trace-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceId.HEADER_NAME, "test-trace-id"))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("agent-desk-backend"))
                .andExpect(jsonPath("$.request_id").value("test-trace-id"));
    }
}
