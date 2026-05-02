package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapResponse;
import com.agentdesk.backend.bootstrap.BootstrapSeedData;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!dev")
public class InMemoryWorkspaceRepository implements WorkspaceRepository {

    private static final Instant BASE_TIME = Instant.parse("2026-05-02T02:00:00Z");

    private final Map<UUID, ProjectState> projects = new LinkedHashMap<>();
    private final Map<UUID, UUID> folderProjects = new LinkedHashMap<>();
    private final Map<UUID, UUID> threadProjects = new LinkedHashMap<>();

    @Override
    public synchronized void ensureSeeded(UUID projectId) {
        projects.computeIfAbsent(projectId, this::seedProject);
    }

    @Override
    public synchronized List<WorkspaceDtos.FolderItem> listFolders(UUID projectId, boolean includeThreads) {
        ensureSeeded(projectId);
        ProjectState state = projects.get(projectId);
        return state.folders.values().stream()
                .sorted(Comparator.comparingInt(FolderState::sortOrder).thenComparing(FolderState::createdAt))
                .map(folder -> folderItem(state, folder, includeThreads))
                .toList();
    }

    @Override
    public synchronized WorkspaceDtos.CreateFolderResponse createFolder(
            UUID projectId,
            WorkspaceDtos.CreateFolderRequest request
    ) {
        ensureSeeded(projectId);
        ProjectState state = projects.get(projectId);
        String name = normalizeName(request.name());
        if (state.folders.values().stream().anyMatch(folder -> folder.name().equals(name))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Folder already exists.");
        }

        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        FolderState folder = new FolderState(UUID.randomUUID(), name, StringUtils.hasText(request.path()) ? request.path().trim() : name, sortOrder, Instant.now());
        state.folders.put(folder.id(), folder);
        folderProjects.put(folder.id(), projectId);

        ThreadState thread = createThreadState(
                state,
                folder,
                "primary-agent",
                "新关联目录已加入，等待补充任务目标",
                List.of("primary", "review"),
                "primary",
                "未编排"
        );
        List<MessageState> messages = List.of(
                createMessage(state, thread.id(), "seed-folder-welcome", "agent", "主助手",
                        "已关联 " + name + " 目录。你可以直接在输入框里描述要分析、修改或验证的目标。", "completed"),
                createMessage(state, thread.id(), "seed-folder-review", "agent", "review-agent",
                        "我会先把这个目录作为当前上下文，不会影响其他目录下已有会话的消息记录。", "completed")
        );
        state.messagesByThread.put(thread.id(), new ArrayList<>(messages));

        return new WorkspaceDtos.CreateFolderResponse(
                folderItem(state, folder, true),
                threadView(state, thread),
                messages.stream().map(this::messageView).toList()
        );
    }

    @Override
    public synchronized Optional<UUID> findProjectIdByFolder(UUID folderId) {
        return Optional.ofNullable(folderProjects.get(folderId));
    }

    @Override
    public synchronized Optional<UUID> findProjectIdByThread(UUID threadId) {
        return Optional.ofNullable(threadProjects.get(threadId));
    }

    @Override
    public synchronized List<BootstrapResponse.ThreadView> listThreads(UUID folderId) {
        UUID projectId = folderProjects.get(folderId);
        if (projectId == null) {
            return List.of();
        }
        ProjectState state = projects.get(projectId);
        return state.threads.values().stream()
                .filter(thread -> thread.folderId().equals(folderId))
                .sorted(Comparator.comparingInt(ThreadState::sortOrder).thenComparing(ThreadState::createdAt))
                .map(thread -> threadView(state, thread))
                .toList();
    }

    @Override
    public synchronized WorkspaceDtos.CreateThreadResponse createThread(UUID folderId, WorkspaceDtos.CreateThreadRequest request) {
        UUID projectId = folderProjects.get(folderId);
        ProjectState state = projects.get(projectId);
        FolderState folder = state.folders.get(folderId);
        int next = (int) state.threads.values().stream().filter(thread -> thread.folderId().equals(folderId)).count() + 1;
        String label = StringUtils.hasText(request.label()) ? request.label().trim() : "会话 " + next;
        String summary = StringUtils.hasText(request.summary()) ? request.summary().trim() : "新的独立会话，等待输入任务";
        List<String> roleKeys = request.roleKeys() == null || request.roleKeys().isEmpty()
                ? List.of("primary", "review", "test")
                : request.roleKeys();
        String focusRole = StringUtils.hasText(request.focusRoleKey()) ? request.focusRoleKey().trim() : "primary";
        ThreadState thread = createThreadState(state, folder, label, summary, roleKeys, focusRole, "未编排");
        state.messagesByThread.put(thread.id(), new ArrayList<>());
        return new WorkspaceDtos.CreateThreadResponse(threadView(state, thread), List.of());
    }

    @Override
    public synchronized Optional<BootstrapResponse.ThreadView> findThread(UUID threadId) {
        UUID projectId = threadProjects.get(threadId);
        if (projectId == null) {
            return Optional.empty();
        }
        ProjectState state = projects.get(projectId);
        return Optional.ofNullable(state.threads.get(threadId)).map(thread -> threadView(state, thread));
    }

    @Override
    public synchronized BootstrapResponse.ThreadView patchThread(UUID threadId, WorkspaceDtos.PatchThreadRequest request) {
        UUID projectId = threadProjects.get(threadId);
        ProjectState state = projects.get(projectId);
        ThreadState current = state.threads.get(threadId);
        String status = StringUtils.hasText(request.status()) ? request.status().trim() : current.status();
        if (!List.of("active", "archived").contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status must be active or archived.");
        }
        ThreadState updated = current.withPatch(
                StringUtils.hasText(request.label()) ? request.label().trim() : current.label(),
                request.summary() == null ? current.summary() : request.summary().trim(),
                StringUtils.hasText(request.focusRoleKey()) ? request.focusRoleKey().trim() : current.focusRoleKey(),
                status
        );
        state.threads.put(threadId, updated);
        return threadView(state, updated);
    }

    @Override
    public synchronized List<BootstrapResponse.MessageView> listMessages(UUID threadId, int limit) {
        UUID projectId = threadProjects.get(threadId);
        ProjectState state = projects.get(projectId);
        return state.messagesByThread.getOrDefault(threadId, List.of()).stream()
                .sorted(Comparator.comparing(MessageState::createdAt))
                .limit(limit)
                .map(this::messageView)
                .toList();
    }

    @Override
    public synchronized WorkspaceDtos.SendMessageResponse sendMessage(
            UUID threadId,
            WorkspaceDtos.SendMessageRequest request,
            String idempotencyKey
    ) {
        UUID projectId = threadProjects.get(threadId);
        ProjectState state = projects.get(projectId);
        String clientMessageId = StringUtils.hasText(request.clientMessageId())
                ? request.clientMessageId().trim()
                : (StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : "msg-" + UUID.randomUUID());
        List<MessageState> messages = state.messagesByThread.computeIfAbsent(threadId, ignored -> new ArrayList<>());
        Optional<MessageState> existing = messages.stream()
                .filter(message -> clientMessageId.equals(message.clientMessageId()))
                .findFirst();
        if (existing.isPresent()) {
            MessageState placeholder = findPlaceholder(messages, clientMessageId)
                    .orElseGet(() -> createMessage(state, threadId, clientMessageId + ":agent", "agent", "主助手",
                            "", "pending"));
            return sendResponse(threadId, existing.get(), placeholder);
        }

        MessageState userMessage = createMessage(state, threadId, clientMessageId, "user", null, request.content().trim(), "completed");
        MessageState placeholder = createMessage(state, threadId, clientMessageId + ":agent", "agent", "主助手",
                "", "pending");
        messages.add(userMessage);
        messages.add(placeholder);
        return sendResponse(threadId, userMessage, placeholder);
    }

    @Override
    public synchronized BootstrapResponse.MessageView completeAgentMessage(UUID threadId, String clientMessageId, String content) {
        UUID projectId = threadProjects.get(threadId);
        ProjectState state = projects.get(projectId);
        List<MessageState> messages = state.messagesByThread.computeIfAbsent(threadId, ignored -> new ArrayList<>());
        for (int index = 0; index < messages.size(); index++) {
            MessageState message = messages.get(index);
            if (clientMessageId.equals(message.clientMessageId()) && "agent".equals(message.role())) {
                MessageState completed = new MessageState(
                        message.id(),
                        message.clientMessageId(),
                        message.role(),
                        message.agentName(),
                        content,
                        message.kind(),
                        "completed",
                        message.createdAt()
                );
                messages.set(index, completed);
                return messageView(completed);
            }
        }
        throw new IllegalStateException("Agent placeholder message is not available.");
    }

    private ProjectState seedProject(UUID projectId) {
        ProjectState state = new ProjectState(projectId);
        int folderSort = 0;
        for (String folderName : List.of("src/auth", "src/router", "tests")) {
            FolderState folder = new FolderState(stableId(projectId, "folder:" + folderName), folderName, folderName, folderSort++, BASE_TIME);
            state.folders.put(folder.id(), folder);
            folderProjects.put(folder.id(), projectId);
        }
        Map<String, FolderState> foldersByName = new LinkedHashMap<>();
        state.folders.values().forEach(folder -> foldersByName.put(folder.name(), folder));
        int threadSort = 0;
        for (BootstrapSeedData.SeedThread seed : BootstrapSeedData.threads()) {
            ThreadState thread = new ThreadState(
                    stableId(projectId, "thread:" + seed.clientKey()),
                    foldersByName.get(seed.folder()).id(),
                    seed.clientKey(),
                    seed.label(),
                    seed.file(),
                    seed.summary(),
                    seed.roles(),
                    seed.focusRole(),
                    seed.roleStatus(),
                    "active",
                    threadSort++,
                    BASE_TIME.plusSeconds(threadSort)
            );
            state.threads.put(thread.id(), thread);
            threadProjects.put(thread.id(), projectId);
            List<MessageState> messages = BootstrapSeedData.messages(seed).stream()
                    .map(message -> new MessageState(
                            stableId(projectId, "message:" + seed.clientKey() + ":" + message.sortOrder()),
                            "seed-" + message.sortOrder(),
                            message.role(),
                            "agent".equals(message.role()) ? message.title() : null,
                            message.content(),
                            "text",
                            "completed",
                            BASE_TIME.plusSeconds(message.sortOrder())
                    ))
                    .toList();
            state.messagesByThread.put(thread.id(), new ArrayList<>(messages));
        }
        return state;
    }

    private ThreadState createThreadState(
            ProjectState state,
            FolderState folder,
            String label,
            String summary,
            List<String> roleKeys,
            String focusRoleKey,
            String roleStatus
    ) {
        String clientKey = "thread-" + UUID.randomUUID().toString().substring(0, 8);
        int sortOrder = (int) state.threads.values().stream().filter(thread -> thread.folderId().equals(folder.id())).count();
        ThreadState thread = new ThreadState(
                UUID.randomUUID(),
                folder.id(),
                clientKey,
                label,
                folder.path(),
                summary,
                roleKeys,
                focusRoleKey,
                roleStatus,
                "active",
                sortOrder,
                Instant.now()
        );
        state.threads.put(thread.id(), thread);
        threadProjects.put(thread.id(), state.projectId());
        return thread;
    }

    private MessageState createMessage(
            ProjectState state,
            UUID threadId,
            String clientMessageId,
            String role,
            String agentName,
            String content,
            String status
    ) {
        return new MessageState(UUID.randomUUID(), clientMessageId, role, agentName, content, "text", status, Instant.now());
    }

    private WorkspaceDtos.FolderItem folderItem(ProjectState state, FolderState folder, boolean includeThreads) {
        List<BootstrapResponse.ThreadView> threads = state.threads.values().stream()
                .filter(thread -> thread.folderId().equals(folder.id()))
                .sorted(Comparator.comparingInt(ThreadState::sortOrder).thenComparing(ThreadState::createdAt))
                .map(thread -> threadView(state, thread))
                .toList();
        return new WorkspaceDtos.FolderItem(
                folder.id(),
                folder.name(),
                folder.path(),
                folder.sortOrder(),
                threads.size(),
                threads.stream().map(BootstrapResponse.ThreadView::clientKey).toList(),
                includeThreads ? threads : null
        );
    }

    private BootstrapResponse.ThreadView threadView(ProjectState state, ThreadState thread) {
        List<MessageState> messages = state.messagesByThread.getOrDefault(thread.id(), List.of());
        MessageState lastMessage = messages.stream().max(Comparator.comparing(MessageState::createdAt)).orElse(null);
        FolderState folder = state.folders.get(thread.folderId());
        return new BootstrapResponse.ThreadView(
                thread.id(),
                thread.clientKey(),
                thread.folderId(),
                folder.name(),
                thread.file(),
                thread.label(),
                thread.summary(),
                stableId(state.projectId(), "role:" + thread.focusRoleKey()),
                thread.focusRoleKey(),
                thread.roleKeys(),
                thread.roleKeys(),
                thread.roleStatus(),
                stableId(state.projectId(), "role:" + BootstrapSeedData.sessionRoleKey(thread.clientKey())),
                BootstrapSeedData.sessionRoleKey(thread.clientKey()),
                thread.status(),
                lastMessage == null ? null : lastMessage.createdAt(),
                lastMessage == null ? null : lastMessage.content(),
                new BootstrapResponse.RagSummary(0, 0, null)
        );
    }

    private BootstrapResponse.MessageView messageView(MessageState message) {
        return new BootstrapResponse.MessageView(
                message.id(),
                message.clientMessageId(),
                message.role(),
                message.agentName(),
                message.content(),
                message.kind(),
                message.status(),
                message.createdAt()
        );
    }

    private Optional<MessageState> findPlaceholder(List<MessageState> messages, String clientMessageId) {
        return messages.stream().filter(message -> (clientMessageId + ":agent").equals(message.clientMessageId())).findFirst();
    }

    private WorkspaceDtos.SendMessageResponse sendResponse(UUID threadId, MessageState userMessage, MessageState placeholder) {
        return new WorkspaceDtos.SendMessageResponse(
                messageView(userMessage),
                messageView(placeholder),
                stableId(threadId, "job:" + userMessage.clientMessageId()),
                "processing",
                "/api/v1/threads/" + threadId + "/stream"
        );
    }

    private String normalizeName(String name) {
        return name.trim().replaceAll("/+$", "");
    }

    private UUID stableId(UUID ownerId, String key) {
        return UUID.nameUUIDFromBytes((ownerId + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private record ProjectState(
            UUID projectId,
            Map<UUID, FolderState> folders,
            Map<UUID, ThreadState> threads,
            Map<UUID, List<MessageState>> messagesByThread
    ) {
        ProjectState(UUID projectId) {
            this(projectId, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    private record FolderState(UUID id, String name, String path, int sortOrder, Instant createdAt) {
    }

    private record ThreadState(
            UUID id,
            UUID folderId,
            String clientKey,
            String label,
            String file,
            String summary,
            List<String> roleKeys,
            String focusRoleKey,
            String roleStatus,
            String status,
            int sortOrder,
            Instant createdAt
    ) {
        ThreadState withPatch(String label, String summary, String focusRoleKey, String status) {
            return new ThreadState(id, folderId, clientKey, label, file, summary, roleKeys, focusRoleKey, roleStatus, status, sortOrder, createdAt);
        }
    }

    private record MessageState(
            UUID id,
            String clientMessageId,
            String role,
            String agentName,
            String content,
            String kind,
            String status,
            Instant createdAt
    ) {
    }
}
