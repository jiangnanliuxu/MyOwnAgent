package com.agentdesk.backend.tools;

import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DeveloperToolService {

    private static final int MAX_OUTPUT_CHARS = 12_000;
    private static final int MAX_READ_CHARS = 16_000;

    private final Path projectRoot;

    public DeveloperToolService(@Value("${agent-desk.developer-tools.project-root:..}") String projectRoot) {
        this.projectRoot = Path.of(projectRoot).toAbsolutePath().normalize();
    }

    public List<Map<String, Object>> toolDefinitions() {
        return List.of(
                functionTool(
                        "project_list_files",
                        "List project files under the repository. Use this before reading files when paths are unknown.",
                        Map.of(
                                "filter", Map.of("type", "string", "description", "Optional substring filter for returned paths."),
                                "limit", Map.of("type", "integer", "description", "Maximum file count, default 80, max 200.")
                        ),
                        List.of()
                ),
                functionTool(
                        "project_read_file",
                        "Read a text file inside the repository. Paths must be relative to project root.",
                        Map.of(
                                "path", Map.of("type", "string", "description", "Relative file path."),
                                "max_chars", Map.of("type", "integer", "description", "Maximum characters to return, max 16000.")
                        ),
                        List.of("path")
                ),
                functionTool(
                        "project_search",
                        "Search project text with ripgrep. Use for code or documentation lookup.",
                        Map.of(
                                "query", Map.of("type", "string", "description", "Search query."),
                                "path", Map.of("type", "string", "description", "Optional relative path to restrict search.")
                        ),
                        List.of("query")
                )
        );
    }

    public ToolResult invoke(String name, JsonNode arguments) {
        return switch (name) {
            case "project_list_files" -> listFiles(arguments);
            case "project_read_file" -> readFile(arguments);
            case "project_search" -> search(arguments);
            default -> new ToolResult(name, false, "Tool is not allowed: " + name);
        };
    }

    private ToolResult listFiles(JsonNode arguments) {
        int limit = clamp(arguments.path("limit").asInt(80), 1, 200);
        String filter = arguments.path("filter").asText("");
        List<String> files = new ArrayList<>();
        try (var stream = Files.walk(projectRoot)) {
            stream.filter(Files::isRegularFile)
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(path -> !path.contains("/.git/") && !path.startsWith(".git/"))
                    .filter(path -> !StringUtils.hasText(filter) || path.contains(filter))
                    .sorted()
                    .limit(limit)
                    .forEach(files::add);
        } catch (IOException exception) {
            return new ToolResult("project_list_files", false, "Failed to list files: " + exception.getMessage());
        }
        return new ToolResult("project_list_files", true, String.join("\n", files));
    }

    private ToolResult readFile(JsonNode arguments) {
        String relativePath = arguments.path("path").asText("");
        if (!StringUtils.hasText(relativePath)) {
            return new ToolResult("project_read_file", false, "path is required.");
        }
        Path target;
        try {
            target = resolveInsideProject(relativePath);
        } catch (BusinessException exception) {
            return new ToolResult("project_read_file", false, exception.getMessage());
        }
        if (!Files.isRegularFile(target)) {
            return new ToolResult("project_read_file", false, "File is not available: " + relativePath);
        }
        int maxChars = clamp(arguments.path("max_chars").asInt(MAX_READ_CHARS), 1, MAX_READ_CHARS);
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return new ToolResult("project_read_file", true, truncate(content, maxChars));
        } catch (IOException exception) {
            return new ToolResult("project_read_file", false, "Failed to read file: " + exception.getMessage());
        }
    }

    private ToolResult search(JsonNode arguments) {
        String query = arguments.path("query").asText("");
        if (!StringUtils.hasText(query)) {
            return new ToolResult("project_search", false, "query is required.");
        }
        List<String> command = new ArrayList<>();
        command.add("rg");
        command.add("--line-number");
        command.add("--no-heading");
        command.add("--color");
        command.add("never");
        command.add(query);
        String relativePath = arguments.path("path").asText("");
        if (StringUtils.hasText(relativePath)) {
            try {
                command.add(resolveInsideProject(relativePath).toString());
            } catch (BusinessException exception) {
                return new ToolResult("project_search", false, exception.getMessage());
            }
        } else {
            command.add(projectRoot.toString());
        }
        return runCommand("project_search", command, Duration.ofSeconds(5));
    }

    private ToolResult runCommand(String toolName, List<String> command, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(projectRoot.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResult(toolName, false, "Command timed out.");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() > 1) {
                return new ToolResult(toolName, false, truncate(output, MAX_OUTPUT_CHARS));
            }
            if (!StringUtils.hasText(output)) {
                output = "No matches.";
            }
            return new ToolResult(toolName, true, truncate(output, MAX_OUTPUT_CHARS));
        } catch (IOException exception) {
            return new ToolResult(toolName, false, "Failed to run rg: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ToolResult(toolName, false, "Tool execution was interrupted.");
        }
    }

    private Path resolveInsideProject(String relativePath) {
        Path target = projectRoot.resolve(relativePath).normalize();
        if (!target.startsWith(projectRoot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Path must stay inside project root.");
        }
        return target;
    }

    private Map<String, Object> functionTool(
            String name,
            String description,
            Map<String, Object> properties,
            List<String> required
    ) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);
        parameters.put("additionalProperties", false);
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars) + "\n...<truncated>";
    }

    public record ToolResult(String name, boolean success, String content) {
    }
}
