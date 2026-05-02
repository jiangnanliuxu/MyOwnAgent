package com.agentdesk.backend.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public final class BootstrapSeedData {

    private BootstrapSeedData() {
    }

    public static List<SeedRole> roles(ObjectMapper objectMapper) {
        return List.of(
                role(objectMapper, "primary", "主助手", "Primary Agent", "orchestrator",
                        "负责拆解任务、调度子 agent、把最后结果整理给用户。",
                        "总控调度、拆解问题、汇总对外输出。",
                        "GPT-5.4", "OpenAI", "中压缩",
                        "先拆解任务，再调度子 agent，最后只输出用户真正需要的结论。",
                        List.of("读代码", "执行测试", "整理结论"), List.of("协调汇总"),
                        List.of("系统层", "角色层", "任务层")),
                role(objectMapper, "review", "review-agent", "Review Agent", "review-guard",
                        "负责变更审查、影响面排查和回归建议。",
                        "变更审查、影响面排查、回归建议。",
                        "GPT-5.4-mini", "OpenAI", "轻压缩",
                        "先定位风险，再给出最短可执行建议；证据不足时明确标出不确定点。",
                        List.of("读代码", "对比变更", "列回归点"), List.of("分析排查", "验证回归"),
                        List.of("系统层", "角色层")),
                role(objectMapper, "route", "route-agent", "Route Agent", "route-guard",
                        "专注登录守卫、白名单路径和重定向流向。",
                        "登录守卫、白名单路径、重定向流向。",
                        "GPT-5.4-mini", "OpenAI", "轻压缩",
                        "只追登录守卫、白名单和重定向链路，不扩散到 UI 细节。",
                        List.of("读代码", "执行测试", "梳理跳转"), List.of("分析排查", "验证回归"),
                        List.of("系统层", "任务层")),
                role(objectMapper, "test", "test-agent", "Test Agent", "smoke-check",
                        "负责最小验证顺序、用例优先级和断言缺口。",
                        "用例生成、smoke 顺序、断言补齐。",
                        "GPT-5.4-mini", "Anthropic", "轻压缩",
                        "优先给最小验证顺序，再补断言缺口和失败回放线索。",
                        List.of("执行测试", "列断言", "整理 smoke"), List.of("验证回归"),
                        List.of("角色层", "任务层")),
                role(objectMapper, "snapshot", "snapshot-agent", "Snapshot Agent", "visual-check",
                        "负责界面快照、差异记录和视觉确认。",
                        "界面快照、差异记录、视觉确认。",
                        "GPT-5.4-mini", "OpenAI", "轻压缩",
                        "只描述可见状态、差异和截图证据，避免替代业务判断。",
                        List.of("截图比对", "视觉核验", "整理差异"), List.of("视觉确认"),
                        List.of("系统层", "角色层")),
                role(objectMapper, "auth", "auth-agent", "Auth Agent", "token-watch",
                        "负责 token 刷新、超时回收和认证链路状态判断。",
                        "token 刷新、超时回收、认证状态。",
                        "GPT-5.4-mini", "OpenAI", "轻压缩",
                        "只围绕认证状态、token 生命周期和 session 回收判断边界。",
                        List.of("读代码", "排查状态", "列认证边界"), List.of("分析排查", "验证回归"),
                        List.of("角色层", "任务层"))
        );
    }

    public static List<SeedThread> threads() {
        return List.of(
                new SeedThread("session-review", "review-agent", "src/auth/useSession.ts", "src/auth",
                        "正在梳理 session 状态变更点", List.of("primary", "review", "auth"), "review", "已编排"),
                new SeedThread("session-auth", "auth-agent", "src/auth/useSession.ts", "src/auth",
                        "已标记 token 刷新与超时回收逻辑", List.of("primary", "auth", "review"), "auth", "已编排"),
                new SeedThread("route-primary", "route-agent", "src/router/guard.ts", "src/router",
                        "关注未登录跳转与白名单路径", List.of("primary", "route", "review", "test"), "route", "已编排"),
                new SeedThread("route-review", "review-agent", "src/router/guard.ts", "src/router",
                        "正在检查守卫依赖的 session 来源", List.of("primary", "review", "route", "test"), "review", "已编排"),
                new SeedThread("route-test", "test-agent", "src/router/guard.ts", "src/router",
                        "准备生成重定向回归用例", List.of("primary", "test", "route", "review"), "test", "已编排"),
                new SeedThread("login-test", "test-agent", "tests/auth-login.spec.ts", "tests",
                        "待补登录失败与重定向断言", List.of("primary", "test", "snapshot"), "test", "已编排"),
                new SeedThread("login-snapshot", "snapshot-agent", "tests/auth-login.spec.ts", "tests",
                        "正在核对认证流程截图变化", List.of("primary", "snapshot", "test"), "snapshot", "已编排")
        );
    }

    public static List<SeedMessage> messages(SeedThread thread) {
        if ("session-review".equals(thread.clientKey())) {
            return List.of(
                    new SeedMessage("user", "你", "我想尽快知道这次重构会影响哪些页面和测试。", 0),
                    new SeedMessage("agent", "主助手", "我已经锁定认证模块、路由守卫和两组回归测试。建议先跑关键路径，再把差异归类。", 1),
                    new SeedMessage("user", "你", "那先告诉我最可能改动的文件，再给一个最小验证顺序。", 2),
                    new SeedMessage("agent", "主助手", "优先看 `src/auth`、`src/router` 和 `tests` 这三个目录，然后先验证登录主路径。", 3)
            );
        }

        return List.of(
                new SeedMessage("user", "你", "切到 " + thread.folder() + "，先看 " + thread.label() + " 这条会话。", 0),
                new SeedMessage("agent", "主助手", thread.summary() + "。当前会话会优先调度 "
                        + String.join("、", thread.roles()) + " 这些角色，并保留独立的消息上下文。", 1),
                new SeedMessage("agent", thread.label(), "这个会话绑定到 " + thread.folder()
                        + " 目录，切换到其他会话时这里会展示另一组对话内容。", 2)
        );
    }

    public static List<SeedSkill> skills(ObjectMapper objectMapper) {
        return List.of(
                new SeedSkill("frontend-design", "frontend-design", "local skill", "启用",
                        "页面设计、交互细节、前端视觉 polish", List.of("系统层", "任务层"), "04:02 / 当前原型",
                        objectMapper.createObjectNode(), objectMapper.createObjectNode()),
                new SeedSkill("browser-use", "browser-use", "plugin skill", "启用",
                        "本地页面打开、点击、截图和可视验证", List.of("任务层"), "03:42 / 弹窗验证",
                        objectMapper.createObjectNode(), objectMapper.createObjectNode()),
                new SeedSkill("openai-docs", "openai-docs", "system skill", "待启用",
                        "OpenAI API 文档、模型升级和 Prompt 迁移", List.of("任务层"), "未触发",
                        objectMapper.createObjectNode(), objectMapper.createObjectNode())
        );
    }

    public static List<SeedMcpEndpoint> mcpEndpoints() {
        return List.of(
                new SeedMcpEndpoint("figma", "Figma MCP", "plugin api", "已连接", "OAuth",
                        "mcp://codex-apps/figma", List.of("design_context", "use_figma", "generate_diagram"), 128),
                new SeedMcpEndpoint("node-repl", "Node REPL", "stdio", "已连接", "local",
                        "mcp://node-repl/js", List.of("js", "js_reset"), 22),
                new SeedMcpEndpoint("browser", "Browser Use", "iab", "已连接", "local session",
                        "file:// / localhost", List.of("goto", "click", "screenshot", "domSnapshot"), 76)
        );
    }

    public static List<BootstrapResponse.HealthItemView> healthItems() {
        return List.of(
                new BootstrapResponse.HealthItemView("Skill 装载", "正常", "3 个 skill 已进入候选池，按页面场景自动启用。"),
                new BootstrapResponse.HealthItemView("MCP 连接", "正常", "当前接口可被页面配置引用，失败时会回到本地摘要。"),
                new BootstrapResponse.HealthItemView("Prompt 挂载", "待确认", "新增 skill 默认不直接进入系统层，需要人工确认范围。"),
                new BootstrapResponse.HealthItemView("密钥状态", "演示", "当前 API Key 为示例值，真实接入前需要替换。")
        );
    }

    public static String sessionRoleKey(String threadKey) {
        return "thread-role-" + threadKey;
    }

    private static SeedRole role(
            ObjectMapper objectMapper,
            String clientKey,
            String name,
            String tag,
            String alias,
            String description,
            String shortDescription,
            String model,
            String provider,
            String compression,
            String promptPrefix,
            List<String> tools,
            List<String> duties,
            List<String> promptLayers
    ) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("model", model);
        config.put("provider", provider);
        config.put("official_url", provider.equals("Anthropic") ? "https://docs.anthropic.com" : "https://platform.openai.com/docs");
        config.put("secret_ref", "demo/" + clientKey);
        config.put("endpoint", provider.equals("Anthropic") ? "https://api.anthropic.com/v1/messages" : "https://api.openai.com/v1/responses");
        config.put("api_format", provider.equals("Anthropic") ? "Anthropic Messages" : "OpenAI Responses");
        config.put("model_mapping", name + " -> " + model.toLowerCase());
        config.put("config_json", provider.equals("Anthropic")
                ? "{\n  \"max_tokens\": 4096,\n  \"temperature\": 0.1\n}"
                : "{\n  \"reasoning_effort\": \"low\",\n  \"temperature\": 0.2\n}");
        config.put("compression", compression);
        config.put("prompt_prefix", promptPrefix);
        config.put("routing", "涉及 " + shortDescription + " 时优先交给 " + name + "。");
        config.set("routing_chips", array(objectMapper, List.of(name + " / " + model, tag)));
        config.set("tools", array(objectMapper, tools));
        config.put("handoff", "将结构化结论交回主助手，由主助手统一对外输出。");
        config.put("matrix_copy", shortDescription);
        config.set("duties", array(objectMapper, duties));
        config.put("custom_duty", description);
        config.set("prompt_layers", array(objectMapper, promptLayers));
        config.put("icon", "");
        return new SeedRole(clientKey, name, alias, tag, description, shortDescription, true, config);
    }

    private static ArrayNode array(ObjectMapper objectMapper, List<String> values) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        values.forEach(arrayNode::add);
        return arrayNode;
    }

    public record SeedRole(
            String clientKey,
            String name,
            String alias,
            String tag,
            String description,
            String shortDescription,
            boolean builtin,
            JsonNode config
    ) {
    }

    public record SeedThread(
            String clientKey,
            String label,
            String file,
            String folder,
            String summary,
            List<String> roles,
            String focusRole,
            String roleStatus
    ) {
    }

    public record SeedMessage(
            String role,
            String title,
            String content,
            int sortOrder
    ) {
    }

    public record SeedSkill(
            String clientKey,
            String name,
            String source,
            String status,
            String scope,
            List<String> mounts,
            String lastRun,
            JsonNode manifest,
            JsonNode config
    ) {
    }

    public record SeedMcpEndpoint(
            String clientKey,
            String name,
            String transport,
            String status,
            String authType,
            String url,
            List<String> tools,
            int latencyMs
    ) {
    }
}
