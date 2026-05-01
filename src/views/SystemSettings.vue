<script setup>
import { reactive } from 'vue';
import TopNav from '../components/layout/TopNav.vue';
import { useModal } from '../composables/useModal';
import { useSectionDirectory } from '../composables/useSectionDirectory';
import { useToast } from '../composables/useToast';
import { COMPRESSION_OPTIONS } from '../data';

const { open } = useModal();
const { show: showToast } = useToast();

const directoryItems = [
  { hash: '#workspace-defaults', title: '工作区偏好', copy: '布局密度、输入节奏、默认上下文呈现。' },
  { hash: '#execution-guard', title: '执行边界', copy: '工具调用、外部动作、提交前确认规则。' },
  { hash: '#context-ops', title: '上下文管理', copy: '压缩策略、内容备份、恢复边界。' },
  { hash: '#platform-entry', title: '平台逻辑入口', copy: '任务队列、运行日志、工具授权、记忆入口。' }
];

const { activeHash, jumpTo } = useSectionDirectory(directoryItems);
const segmentedState = reactive({
  density: '紧凑',
  guard: '开启'
});

function selectSegment(group, value) {
  segmentedState[group] = value;
  showToast(`${value} 已选中`);
}

function openContextModal(type) {
  if (type === 'compression-settings') {
    const options = COMPRESSION_OPTIONS.map(
      (option, index) => `
        <label class="choice-row">
          <input type="radio" name="workspace-compression" value="${option}" ${index === 2 ? 'checked' : ''} />
          <span>${option}</span>
        </label>
      `
    ).join('');

    open({
      modalTitle: '上下文压缩',
      modalSubtitle: '触发压缩时可以先选强度，再决定保留哪些内容。',
      saveLabel: '执行压缩',
      content: `
        <div class="modal-form-grid">
          <div class="modal-field modal-field-full">
            <span>压缩强度</span>
            <div class="choice-stack">${options}</div>
          </div>
          <label class="modal-field modal-field-full">
            <span>保留内容</span>
            <textarea class="text-input modal-textarea">当前目录、最近操作链、角色分工、关键结论</textarea>
          </label>
        </div>
      `,
      onSave() {
        showToast('已触发上下文压缩');
      }
    });
  }

  if (type === 'backup-settings') {
    open({
      modalTitle: '上下文内容备份',
      modalSubtitle: '压缩前先生成快照，决定备份哪些内容以及落在哪里。',
      saveLabel: '生成备份',
      content: `
        <div class="modal-form-grid">
          <label class="modal-field modal-field-full">
            <span>备份内容</span>
            <textarea class="text-input modal-textarea">重要对话、阶段结论、角色 Prompt、交付摘要</textarea>
          </label>
          <label class="modal-field">
            <span>备份位置</span>
            <input class="text-input" type="text" value="workspace-memory/context-snapshots/" />
          </label>
          <label class="modal-field">
            <span>命名规则</span>
            <input class="text-input" type="text" value="thread-id + stage + timestamp" />
          </label>
        </div>
      `,
      onSave() {
        showToast('备份快照已生成');
      }
    });
  }
}

function openPlatformCard(id) {
  const cards = {
    'open-task-queue': ['任务队列', '排队中：截图核验、MCP 健康检查。阻塞：真实 API Key 待配置。运行中：当前页面状态验证。'],
    'open-run-log': ['运行日志', '最近动作：恢复 script.js、渲染 Skill/MCP 页面、同步当前会话到机器人设置。'],
    'open-tool-permission': ['工具授权', '默认允许本地读写当前项目；外部提交、权限变更和敏感数据传输仍需要用户确认。'],
    'open-memory-backup': ['记忆与备份', '当前备份策略：压缩前生成阶段快照，保留角色 Prompt、会话摘要和交付结论。']
  };
  const [title, copy] = cards[id];
  open({
    modalTitle: title,
    modalSubtitle: '这里先作为平台入口原型展示，后续可接真实列表。',
    saveLabel: '知道了',
    content: `<div class="value-box modal-field-full">${copy}</div>`
  });
}
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar settings-sidebar">
      <section class="recent-file-block">
        <div class="sidebar-section-head">
          <span>设置目录</span>
          <span class="light-meta">4 组</span>
        </div>
        <div class="settings-directory">
          <a
            v-for="item in directoryItems"
            :key="item.hash"
            class="directory-item"
            :class="{ 'is-active': activeHash === item.hash }"
            :href="item.hash"
            @click.prevent="jumpTo(item.hash)"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.copy }}</span>
          </a>
        </div>
      </section>
    </aside>

    <main class="main-stage">
      <section class="settings-surface">
        <div class="surface-top">
          <div class="stage-meta">
            <span class="stage-kicker">系统设置</span>
            <h2>Workspace Settings</h2>
            <p class="page-note">先把工作区的默认手感定下来，再决定 agent 如何接手、何时提醒、何时越界前停一下。</p>
          </div>
          <TopNav active="settings" include-back />
        </div>

        <div class="settings-content">
          <section class="settings-panel" id="workspace-defaults">
            <div class="panel-head">
              <strong>工作区偏好</strong>
              <span>Workspace Defaults</span>
            </div>
            <div class="field-list">
              <div class="field-row">
                <div class="field-copy">
                  <strong>默认布局密度</strong>
                  <span>让文件、会话和工具输出保持同一种阅读压缩比，减少频繁切换的视觉噪音。</span>
                </div>
                <div class="segmented">
                  <button class="seg-chip" :class="{ 'is-active': segmentedState.density === '紧凑' }" type="button" @click="selectSegment('density', '紧凑')">紧凑</button>
                  <button class="seg-chip" :class="{ 'is-active': segmentedState.density === '舒展' }" type="button" @click="selectSegment('density', '舒展')">舒展</button>
                </div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>输入框默认模式</strong>
                  <span>主输入框优先保留上下文式长指令，还是偏向快速命令式短输入。</span>
                </div>
                <div class="chip-row">
                  <span class="status-chip">上下文优先</span>
                  <span class="toggle-chip">快捷命令</span>
                </div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>侧栏宽度基线</strong>
                  <span>保留足够的文件与角色信息，同时不压缩主会话区。</span>
                </div>
                <div class="range-readout">
                  <div class="range-track"></div>
                  <div class="range-caption">300px / 中等宽度</div>
                </div>
              </div>
            </div>
          </section>

          <section class="settings-panel" id="execution-guard">
            <div class="panel-head">
              <strong>执行边界</strong>
              <span>Safety Rules</span>
            </div>
            <div class="field-list">
              <div class="field-row">
                <div class="field-copy">
                  <strong>提交外部动作前停一下</strong>
                  <span>发送消息、提交表单、改权限这类动作先停在最后一步，等用户明确确认。</span>
                </div>
                <div class="segmented">
                  <button class="seg-chip" :class="{ 'is-active': segmentedState.guard === '开启' }" type="button" @click="selectSegment('guard', '开启')">开启</button>
                  <button class="seg-chip" :class="{ 'is-active': segmentedState.guard === '关闭' }" type="button" @click="selectSegment('guard', '关闭')">关闭</button>
                </div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>本地写入范围</strong>
                  <span>优先限制在当前项目和桌面草稿目录，避免大范围修改造成打扰。</span>
                </div>
                <div class="value-box">当前项目 + 桌面候选目录</div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>浏览器接管阈值</strong>
                  <span>遇到登录、隐私字段或代表用户提交时自动切换到人工确认节点。</span>
                </div>
                <div class="chip-row">
                  <span class="status-chip">严格</span>
                  <span class="toggle-chip">宽松</span>
                </div>
              </div>
            </div>
          </section>

          <section class="settings-panel" id="context-ops">
            <div class="panel-head">
              <strong>上下文管理</strong>
              <span>Context Control</span>
            </div>
            <div class="prompt-stack">
              <div class="prompt-block">
                <strong>上下文压缩</strong>
                <p>当会话过长时，优先保留当前目录、最近操作链和角色分工，把较早对话压成结构化摘要，不让旧内容持续占满窗口。</p>
                <div class="prompt-action-row">
                  <button class="tiny-action" type="button" data-modal="compression-settings" @click="openContextModal('compression-settings')">立即压缩</button>
                </div>
              </div>
              <div class="prompt-block">
                <strong>上下文内容备份</strong>
                <p>重要对话、阶段结论、角色 Prompt 和交付摘要在压缩前先生成备份快照，方便回放、恢复和对照不同 agent 的判断路径。</p>
                <div class="prompt-action-row">
                  <button class="tiny-action" type="button" data-modal="backup-settings" @click="openContextModal('backup-settings')">生成备份</button>
                </div>
              </div>
            </div>
            <div class="settings-note">压缩和备份最好是并行的：一个负责让窗口变轻，一个负责让历史不丢。</div>
          </section>

          <section class="settings-panel" id="platform-entry">
            <div class="panel-head">
              <strong>平台逻辑入口</strong>
              <span>Platform Entry Points</span>
            </div>
            <div class="dual-grid">
              <button class="micro-card action-card" id="open-task-queue" type="button" @click="openPlatformCard('open-task-queue')">
                <strong>任务队列</strong>
                <p>看见当前有哪些任务在排队、阻塞、运行中，以及谁在等待人工确认。</p>
              </button>
              <button class="micro-card action-card" id="open-run-log" type="button" @click="openPlatformCard('open-run-log')">
                <strong>运行日志</strong>
                <p>把 agent 的工具调用、关键决策和失败原因串成时间线，而不是散落在对话里。</p>
              </button>
              <button class="micro-card action-card" id="open-tool-permission" type="button" @click="openPlatformCard('open-tool-permission')">
                <strong>工具授权</strong>
                <p>统一管理文件写入、浏览器接管、外部提交这类敏感动作的权限边界。</p>
              </button>
              <button class="micro-card action-card" id="open-memory-backup" type="button" @click="openPlatformCard('open-memory-backup')">
                <strong>记忆与备份</strong>
                <p>把项目记忆、角色 Prompt、阶段快照和可恢复上下文放到一处集中查看。</p>
              </button>
              <a class="micro-card action-card" href="/integration">
                <strong>Skill 与 MCP 管理</strong>
                <p>统一查看 skill 装载、Prompt 注入点、MCP 接口接入状态和连通性。</p>
              </a>
            </div>
            <div class="settings-note">如果这是一个真正面向 agent 开发的平台，这五个入口通常会比“通知中心”更常用。</div>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>
