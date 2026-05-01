package com.agentdesk.backend.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successBuildsSuccessfulEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.code()).isEqualTo("0");
        assertThat(response.message()).isEqualTo("ok");
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.details()).isNull();
    }

    @Test
    void failureBuildsErrorEnvelope() {
        ApiResponse<Void> response = ApiResponse.failure("BAD_REQUEST", "Invalid request.");

        assertThat(response.code()).isEqualTo("BAD_REQUEST");
        assertThat(response.message()).isEqualTo("Invalid request.");
        assertThat(response.data()).isNull();
        assertThat(response.details()).isNull();
    }
}
