package com.agentdesk.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeveloperToolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsAndSearchesOnlyInsideProject(@TempDir Path projectRoot) throws Exception {
        Files.writeString(projectRoot.resolve("README.md"), "Agent Desk backend\n");
        Files.createDirectories(projectRoot.resolve("src"));
        Files.writeString(projectRoot.resolve("src/App.java"), "class App { String name = \"AgentJobService\"; }\n");
        DeveloperToolService tools = new DeveloperToolService(projectRoot.toString());

        DeveloperToolService.ToolResult read = tools.invoke(
                "project_read_file",
                objectMapper.readTree("{\"path\":\"README.md\"}")
        );
        DeveloperToolService.ToolResult search = tools.invoke(
                "project_search",
                objectMapper.readTree("{\"query\":\"AgentJobService\",\"path\":\"src\"}")
        );
        DeveloperToolService.ToolResult escaped = tools.invoke(
                "project_read_file",
                objectMapper.readTree("{\"path\":\"../outside.txt\"}")
        );

        assertThat(read.success()).isTrue();
        assertThat(read.content()).contains("Agent Desk backend");
        assertThat(search.success()).isTrue();
        assertThat(search.content()).contains("App.java");
        assertThat(escaped.success()).isFalse();
        assertThat(escaped.content()).contains("inside project root");
    }
}
