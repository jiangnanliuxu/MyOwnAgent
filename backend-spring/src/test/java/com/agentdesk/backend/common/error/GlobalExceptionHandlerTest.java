package com.agentdesk.backend.common.error;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.common.web.TraceId;
import com.agentdesk.backend.common.web.TraceIdFilter;
import com.agentdesk.backend.security.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.ProbeController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, GlobalExceptionHandlerTest.ProbeController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessExceptionUsesUnifiedEnvelope() throws Exception {
        mockMvc.perform(get("/probe/business").header(TraceId.HEADER_NAME, "trace-business"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceId.HEADER_NAME, "trace-business"))
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Probe business error."))
                .andExpect(jsonPath("$.request_id").value("trace-business"));
    }

    @Test
    void validationExceptionUsesUnifiedEnvelope() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceId.HEADER_NAME, not(blankOrNullString())))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void unexpectedExceptionUsesInternalErrorEnvelope() throws Exception {
        mockMvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected server error."));
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/business")
        ApiResponse<Void> business() {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Probe business error.");
        }

        @GetMapping("/unexpected")
        ApiResponse<Void> unexpected() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/validation")
        ApiResponse<Void> validation(@Valid @RequestBody ProbeRequest request) {
            return ApiResponse.success(null);
        }
    }

    record ProbeRequest(@NotBlank String name) {
    }
}
