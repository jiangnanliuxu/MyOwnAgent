<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import TopNav from '../components/layout/TopNav.vue';
import { useThreadStore } from '../stores/thread';
import { useRoleStore } from '../stores/role';
import { useToast } from '../composables/useToast';

const route = useRoute();
const threadStore = useThreadStore();
const roleStore = useRoleStore();
const { show: showToast } = useToast();

const openFolders = ref(new Set(['src/auth']));
const composerText = ref(null);
const folderInput = ref(null);
const ragFileInput = ref(null);
const contextChip = ref('已附带当前目录与最近建议');
const workflowAgents = [
  {
    id: 'planning',
    name: 'Planning Agent',
    role: '规划协调',
    status: '拆分阶段范围、验收标准和交付顺序'
  },
  {
    id: 'development',
    name: 'Development Agent',
    role: '实现项目需求代码',
    status: '实现当前阶段代码并修复测试反馈'
  },
  {
    id: 'testing',
    name: 'Testing Agent',
    role: '测试验证',
    status: '验证当前阶段构建、接口和回归用例'
  }
];
const activeWorkflowAgentId = ref('development');

const activeContext = computed(() => threadStore.currentContext);
const bubbles = computed(() => threadStore.currentConversation);
const agentIcon = computed(() => roleStore.getRole('primary').icon);
const activeWorkflowAgent = computed(() =>
  workflowAgents.find((agent) => agent.id === activeWorkflowAgentId.value) || workflowAgents[0]
);

function isOpen(folder) {
  return openFolders.value.has(folder);
}

function groupCountLabel(group) {
  return `${group.threads.length} 个 Agent 对话`;
}

function setOpenFolders(folders) {
  openFolders.value = new Set(folders);
}

function selectThread(threadId) {
  const context = threadStore.setActiveThread(threadId, true);
  setOpenFolders([context.folder]);
  contextChip.value = `已切换到 ${context.label}`;
}

function setActiveWorkflowAgent(agentId) {
  activeWorkflowAgentId.value = agentId;
  contextChip.value = `当前工作：${activeWorkflowAgent.value.name}`;
}

function onSummaryClick(event, group) {
  if (!isOpen(group.folder)) {
    event.preventDefault();
    selectThread(group.threads[0]);
  }
}

function onSummaryKeydown(event, group) {
  if ((event.key === 'Enter' || event.key === ' ') && !isOpen(group.folder)) {
    event.preventDefault();
    selectThread(group.threads[0]);
  }
}

function onAccordionToggle(event, group) {
  const next = new Set(openFolders.value);
  if (event.target.open) {
    next.add(group.folder);
  } else {
    next.delete(group.folder);
  }
  openFolders.value = next;
}

function openFolderPicker() {
  folderInput.value?.click();
}

function addRelatedFolder(event) {
  const files = Array.from(event.target.files || []);
  if (!files.length) return;
  const context = threadStore.addRelatedFolder(files);
  setOpenFolders([context.folder]);
  contextChip.value = `已关联 ${context.folder}`;
  showToast(`已新增关联目录：${context.folder}`);
  event.target.value = '';
}

async function addThread(group) {
  try {
    const context = await threadStore.addThreadForFolder(group.folder);
    setOpenFolders([context.folder]);
    contextChip.value = `已新建 ${context.label}`;
    showToast(`已为 ${group.folder} 新增会话`);
  } catch (error) {
    contextChip.value = '新增会话失败';
    showToast(error.message || '新增会话失败，请检查后端连接');
  }
}

function appendText(fragment) {
  const node = composerText.value;
  if (!node) return;
  const current = node.textContent.trim();
  node.textContent = current ? `${current}\n${fragment}` : fragment;
}

function scrollConversationToBottom() {
  nextTick(() => {
    const stack = document.querySelector('.conversation-stack');
    stack?.scrollTo({ top: stack.scrollHeight });
  });
}

async function sendMessage() {
  const node = composerText.value;
  const text = node?.textContent.trim() || '';
  if (!text) {
    showToast('先输入一点内容再发送');
    return;
  }

  const context = activeContext.value;
  const roleNames = context.roles.map((roleId) => roleStore.getRole(roleId).name).join('、');
  threadStore.appendConversationBubble(context.id, { kind: 'user', title: '你', text });
  threadStore.appendConversationBubble(context.id, {
    kind: 'agent',
    title: '主助手',
    text: `已把这条请求绑定到 ${context.folder} / ${context.label}。下一步会优先让 ${roleNames} 接力处理。`
  });
  threadStore.sendMessageToBackend(context.id, text).catch(() => {
    contextChip.value = '后端消息发送失败，已保留本地会话';
  });
  node.textContent = '';
  showToast('已加入当前会话');
  scrollConversationToBottom();
}

function onComposerKeydown(event) {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
    event.preventDefault();
    sendMessage();
  }
}

function attachFolder() {
  if (threadStore.backendReady && activeContext.value.backendId) {
    ragFileInput.value?.click();
    return;
  }
  appendText(`附加目录：${activeContext.value.folder}`);
  contextChip.value = `已附加 ${activeContext.value.folder}`;
  showToast('已附加当前目录');
}

async function uploadRagFile(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  try {
    await threadStore.uploadRagFile(activeContext.value.id, file);
    contextChip.value = `已上传索引 ${file.name}`;
    showToast(`已上传索引：${file.name}`);
  } catch {
    contextChip.value = '上传索引失败';
    showToast('上传索引失败，已保留本地会话');
  } finally {
    event.target.value = '';
  }
}

function insertTerminal() {
  appendText('终端输出：npm test -- --runInBand 仍待执行');
  contextChip.value = '已插入终端输出占位';
  showToast('已插入终端输出占位');
}

onMounted(async () => {
  await threadStore.hydrateFromBackend();
  const context = threadStore.hydrateFromRoute(route.query.thread);
  setOpenFolders([context.folder]);
});

watch(
  () => route.query.thread,
  (threadId) => {
    const context = threadStore.hydrateFromRoute(threadId);
    setOpenFolders([context.folder]);
  }
);
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar">
      <section class="recent-file-block">
        <div class="sidebar-section-head">
          <span>最近关联目录</span>
          <span class="light-meta">{{ threadStore.fileGroups.length }} 个</span>
        </div>
        <button class="add-related-file" type="button" @click="openFolderPicker">
          <span>新增关联目录</span>
          <strong>调用系统文件夹选择</strong>
        </button>
        <input
          id="related-folder-input"
          ref="folderInput"
          class="visually-hidden-file"
          type="file"
          webkitdirectory
          directory
          multiple
          @change="addRelatedFolder"
        />
        <div class="file-mini-list">
          <details
            v-for="group in threadStore.fileGroups"
            :key="group.folder"
            class="file-accordion"
            :class="{ active: group.folder === activeContext.folder, 'has-active-thread': group.folder === activeContext.folder }"
            :open="isOpen(group.folder)"
            @toggle="onAccordionToggle($event, group)"
          >
            <summary
              class="file-mini-item"
              @click="onSummaryClick($event, group)"
              @keydown="onSummaryKeydown($event, group)"
            >
              <div>
                <strong>{{ group.folder }}</strong>
                <span>{{ groupCountLabel(group) }}</span>
              </div>
              <span class="accordion-arrow" aria-hidden="true"></span>
            </summary>
            <div class="agent-thread-list">
              <button
                v-for="threadId in group.threads"
                :key="threadId"
                class="agent-thread-card"
                :class="{ 'is-active': activeContext.id === threadId }"
                type="button"
                :data-thread-id="threadId"
                @click="selectThread(threadId)"
              >
                <strong>{{ threadStore.getContext(threadId).label }}</strong>
                <p>{{ threadStore.getContext(threadId).summary }}</p>
              </button>
              <button class="new-thread-card" type="button" @click="addThread(group)">
                <strong>新增会话</strong>
                <p>为这个目录开一条独立上下文</p>
              </button>
            </div>
          </details>
        </div>
      </section>
    </aside>

    <main class="main-stage">
      <section class="conversation-surface">
        <div class="surface-top">
          <div class="stage-meta">
            <span class="stage-kicker">当前工作区</span>
            <h2 id="active-file-title">{{ activeContext.folder }}</h2>
          </div>
          <TopNav active="workspace" :robot-thread-id="activeContext.id" />
        </div>

        <div class="conversation-context-card">
          <span>当前会话</span>
          <strong>{{ activeContext.label }}</strong>
          <p>{{ activeContext.summary }}</p>
        </div>

        <div class="workflow-agent-strip" id="workflow-agent-strip" aria-live="polite">
          <div class="workflow-agent-current">
            <span class="stage-kicker">当前工作 Agent</span>
            <strong id="current-workflow-agent">{{ activeWorkflowAgent.name }}</strong>
            <p>{{ activeWorkflowAgent.role }} / {{ activeWorkflowAgent.status }}</p>
          </div>
          <div class="workflow-agent-steps" aria-label="后端开发子 Agent">
            <button
              v-for="agent in workflowAgents"
              :key="agent.id"
              class="workflow-agent-chip"
              :class="{ 'is-active': activeWorkflowAgent.id === agent.id }"
              type="button"
              :aria-pressed="activeWorkflowAgent.id === agent.id"
              @click="setActiveWorkflowAgent(agent.id)"
            >
              <span>{{ agent.name }}</span>
              <strong>{{ agent.role }}</strong>
            </button>
          </div>
        </div>

        <div class="conversation-stack">
          <div v-for="(bubble, index) in bubbles" :key="`${activeContext.id}-${bubble.kind}-${index}`" class="bubble" :class="bubble.kind" :id="bubble.id">
            <strong v-if="bubble.kind === 'agent'" class="agent-title">
              <span class="agent-avatar" aria-hidden="true" v-html="agentIcon"></span>
              <span>{{ bubble.title }}</span>
            </strong>
            <strong v-else>{{ bubble.title }}</strong>
            <p>{{ bubble.text }}</p>
          </div>
        </div>

        <div class="composer-card">
          <div class="composer-box" id="composer-box">
            <div
              class="composer-text"
              id="composer-text"
              ref="composerText"
              contenteditable="true"
              role="textbox"
              aria-label="输入请求"
              data-placeholder="继续补充你的任务、限制条件或想优先看的目录。"
              @keydown="onComposerKeydown"
            >帮我列一下这三个文件各自可能受影响的逻辑点，并按验证优先级排序。</div>
            <div class="composer-line">
              <span class="stage-kicker" id="composer-thread-chip">当前线程 / {{ activeContext.label }}</span>
              <span class="light-meta" id="composer-context-chip">{{ contextChip }}</span>
            </div>
            <div class="composer-tools" aria-label="输入操作">
              <button class="icon-button primary" id="composer-send" type="button" data-tooltip="发送" @click="sendMessage">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M3 11.5 20.5 4l-4.6 16-4.1-6.2L3 11.5Z"></path>
                  <path d="m20.5 4-8.7 9.8"></path>
                </svg>
              </button>
              <button class="icon-button" id="composer-attach-file" type="button" data-tooltip="附加当前目录" @click="attachFolder">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 7v10"></path>
                  <path d="M7 12h10"></path>
                  <path d="M6 4h8l4 4v12H6z"></path>
                </svg>
              </button>
              <input
                id="composer-rag-file-input"
                ref="ragFileInput"
                class="visually-hidden-file"
                type="file"
                @change="uploadRagFile"
              />
              <button class="icon-button" id="composer-insert-terminal" type="button" data-tooltip="插入终端输出" @click="insertTerminal">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m5 7 4 5-4 5"></path>
                  <path d="M12 17h7"></path>
                  <path d="M4 4h16v16H4z"></path>
                </svg>
              </button>
              <button class="icon-button send-key" id="composer-send-hotkey" type="button" data-tooltip="回车发送" @click="sendMessage">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M19 7v6H8"></path>
                  <path d="m12 9-4 4 4 4"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>
