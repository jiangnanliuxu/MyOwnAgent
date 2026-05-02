<script setup>
import { computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import TopNav from '../components/layout/TopNav.vue';
import { useModal } from '../composables/useModal';
import { useToast } from '../composables/useToast';
import { useRoleStore } from '../stores/role';
import { useThreadStore } from '../stores/thread';

const route = useRoute();
const threadStore = useThreadStore();
const roleStore = useRoleStore();
const { open } = useModal();
const { show: showToast } = useToast();

const context = computed(() => threadStore.currentContext);
const activeRole = computed(() => roleStore.activeRole);
const sessionRoleIds = computed(() =>
  threadStore.getThreadIdsForFolder(context.value.folder).map((threadId) => {
    const threadContext = threadStore.getContext(threadId);
    return roleStore.ensureThreadRole(threadContext).id;
  })
);

function escapeHTML(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

async function syncThread(threadId) {
  const nextContext = threadStore.hydrateFromRoute(threadId);
  threadStore.getThreadIdsForFolder(nextContext.folder).forEach((fileThreadId) => {
    roleStore.ensureThreadRole(threadStore.getContext(fileThreadId));
  });
  roleStore.setActiveRole(nextContext.sessionRoleId);
}

function setActiveRole(roleId) {
  roleStore.setActiveRole(roleId);
}

function renderList(values, className = 'status-chip') {
  return values.map((value) => `<span class="${className}">${escapeHTML(value)}</span>`).join('');
}

function openModelConfig() {
  const role = activeRole.value;
  open({
    modalTitle: `${role.name} / 模型接入`,
    modalSubtitle: '补全供应商、官网、密钥、请求地址、格式映射和原始 JSON。',
    saveLabel: '保存模型配置',
    content: `
      <div class="modal-form-grid">
        <label class="modal-field">
          <span>供应商名称</span>
          <input class="text-input" id="provider-name-input" type="text" value="${escapeHTML(role.provider || '')}" />
        </label>
        <label class="modal-field">
          <span>官网链接</span>
          <input class="text-input" id="provider-url-input" type="text" value="${escapeHTML(role.officialUrl || '')}" />
        </label>
        <label class="modal-field modal-field-full">
          <span>API Key</span>
          <input class="text-input" id="provider-key-input" type="text" value="${escapeHTML(role.apiKey || '')}" />
        </label>
        <label class="modal-field modal-field-full">
          <span>请求地址</span>
          <input class="text-input" id="provider-endpoint-input" type="text" value="${escapeHTML(role.endpoint || '')}" />
        </label>
        <label class="modal-field">
          <span>API 格式</span>
          <select class="text-input" id="provider-format-input">
            ${['OpenAI Responses', 'OpenAI Chat Completions', 'Anthropic Messages', 'Gemini GenerateContent']
              .map((format) => `<option ${role.apiFormat === format ? 'selected' : ''}>${format}</option>`)
              .join('')}
          </select>
        </label>
        <label class="modal-field">
          <span>模型名</span>
          <input class="text-input" id="provider-model-input" type="text" value="${escapeHTML(role.model || '')}" />
        </label>
        <label class="modal-field modal-field-full">
          <span>模型映射</span>
          <textarea class="text-input modal-textarea" id="provider-mapping-input">${escapeHTML(role.modelMapping || '')}</textarea>
        </label>
        <label class="modal-field modal-field-full">
          <span>配置 JSON</span>
          <textarea class="text-input modal-textarea modal-codearea" id="provider-json-input">${escapeHTML(role.configJson || '')}</textarea>
        </label>
      </div>
    `,
    onSave(modalBody) {
      roleStore.updateRole(roleStore.activeRoleId, {
        provider: modalBody.querySelector('#provider-name-input').value.trim(),
        officialUrl: modalBody.querySelector('#provider-url-input').value.trim(),
        apiKey: modalBody.querySelector('#provider-key-input').value.trim(),
        endpoint: modalBody.querySelector('#provider-endpoint-input').value.trim(),
        apiFormat: modalBody.querySelector('#provider-format-input').value,
        model: modalBody.querySelector('#provider-model-input').value.trim(),
        modelMapping: modalBody.querySelector('#provider-mapping-input').value.trim(),
        configJson: modalBody.querySelector('#provider-json-input').value
      });
      showToast('模型配置已更新');
    }
  });
}

function openCompressionConfig() {
  const role = activeRole.value;
  const options = roleStore.compressionOptions.map(
    (option) => `
      <label class="choice-row">
        <input type="radio" name="compression-level" value="${option}" ${role.compression === option ? 'checked' : ''} />
        <span>${option}</span>
      </label>
    `
  ).join('');

  open({
    modalTitle: `${role.name} / 压缩强度`,
    modalSubtitle: '选择这个角色默认使用的上下文压缩强度。',
    saveLabel: '保存压缩强度',
    content: `<div class="choice-stack">${options}</div>`,
    onSave(modalBody) {
      const nextCompression = modalBody.querySelector('input[name="compression-level"]:checked');
      if (nextCompression) {
        roleStore.updateRole(roleStore.activeRoleId, { compression: nextCompression.value });
        showToast('压缩强度已保存');
      }
    }
  });
}

function openRoleDescriptionEditor() {
  const role = activeRole.value;
  open({
    modalTitle: `${role.name} / 角色说明`,
    modalSubtitle: '修改角色名称、职责说明和矩阵里的展示文案。',
    saveLabel: '保存角色说明',
    content: `
      <div class="modal-form-grid">
        <label class="modal-field">
          <span>角色名称</span>
          <input class="text-input" id="role-name-input" type="text" value="${escapeHTML(role.name)}" />
        </label>
        <label class="modal-field">
          <span>别名</span>
          <input class="text-input" id="role-alias-input" type="text" value="${escapeHTML(role.alias)}" />
        </label>
        <label class="modal-field modal-field-full">
          <span>职责说明</span>
          <textarea class="text-input modal-textarea" id="role-description-input">${escapeHTML(role.description)}</textarea>
        </label>
        <label class="modal-field modal-field-full">
          <span>矩阵说明</span>
          <textarea class="text-input modal-textarea" id="role-matrix-input">${escapeHTML(role.matrixCopy)}</textarea>
        </label>
      </div>
    `,
    onSave(modalBody) {
      const nextName = modalBody.querySelector('#role-name-input').value.trim() || role.name;
      const nextAlias = modalBody.querySelector('#role-alias-input').value.trim() || role.alias;
      const nextDescription = modalBody.querySelector('#role-description-input').value.trim() || role.description;
      const nextMatrixCopy = modalBody.querySelector('#role-matrix-input').value.trim() || role.matrixCopy;
      roleStore.updateRole(roleStore.activeRoleId, {
        name: nextName,
        alias: nextAlias,
        description: nextDescription,
        matrixCopy: nextMatrixCopy
      });
      if (role.sourceThreadId) {
        threadStore.updateThread(role.sourceThreadId, {
          label: nextName,
          summary: nextDescription
        });
      }
      showToast('角色说明已更新');
    }
  });
}

function openPromptPrefixEditor() {
  const role = activeRole.value;
  const layerOptions = roleStore.promptLayerOptions.map(
    (layer) => `
      <label class="choice-row">
        <input type="checkbox" name="prompt-layer" value="${layer}" ${role.promptLayers.includes(layer) ? 'checked' : ''} />
        <span>${layer}</span>
      </label>
    `
  ).join('');

  open({
    modalTitle: `${role.name} / 提示前缀`,
    modalSubtitle: '控制这个角色在接任务前先看到什么边界提示。',
    saveLabel: '保存提示前缀',
    content: `
      <div class="modal-form-grid">
        <label class="modal-field modal-field-full">
          <span>提示前缀</span>
          <textarea class="text-input modal-textarea" id="prompt-prefix-input">${escapeHTML(role.promptPrefix)}</textarea>
        </label>
        <div class="modal-field modal-field-full">
          <span>注入位置</span>
          <div class="choice-stack">${layerOptions}</div>
        </div>
      </div>
    `,
    onSave(modalBody) {
      const selectedLayers = Array.from(modalBody.querySelectorAll('input[name="prompt-layer"]:checked')).map(
        (input) => input.value
      );
      roleStore.updateRole(roleStore.activeRoleId, {
        promptPrefix: modalBody.querySelector('#prompt-prefix-input').value.trim() || role.promptPrefix,
        promptLayers: selectedLayers.length ? selectedLayers : role.promptLayers
      });
      showToast('提示前缀已更新');
    }
  });
}

function openRoleConfigCopy() {
  const role = activeRole.value;
  const config = JSON.stringify(
    {
      role: role.name,
      alias: role.alias,
      model: role.model,
      provider: role.provider,
      compression: role.compression,
      tools: role.tools,
      promptPrefix: role.promptPrefix,
      routing: role.routing
    },
    null,
    2
  );

  open({
    modalTitle: `${role.name} / 当前配置`,
    modalSubtitle: '这里展示的是可复制的角色配置快照。',
    saveLabel: '复制完成',
    content: `
      <label class="modal-field modal-field-full">
        <span>配置 JSON</span>
        <textarea class="text-input modal-textarea modal-codearea" id="role-config-output">${escapeHTML(config)}</textarea>
      </label>
    `,
    onSave() {
      showToast('配置快照已准备好');
    }
  });
}

function updateAlias(event) {
  const value = event.target.value.trim();
  if (value) roleStore.updateRole(roleStore.activeRoleId, { alias: value });
}

function updateCustomDuty(event) {
  roleStore.updateRole(roleStore.activeRoleId, { customDuty: event.target.value });
}

function toggleDuty(duty, checked) {
  const role = activeRole.value;
  const duties = new Set(role.duties);
  if (checked) duties.add(duty);
  else duties.delete(duty);
  roleStore.updateRole(roleStore.activeRoleId, { duties: Array.from(duties) });
  showToast('职责已更新');
}

onMounted(async () => {
  await threadStore.hydrateFromBackend();
  await roleStore.hydrateFromBackend();
  await syncThread(route.query.thread);
});
watch(() => route.query.thread, (threadId) => syncThread(threadId));
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar settings-sidebar">
      <section class="recent-file-block">
        <div class="sidebar-section-head">
          <span>角色编排</span>
          <span class="light-meta" id="role-count">{{ sessionRoleIds.length }} 个</span>
        </div>
        <div class="dialogue-summary-card">
          <strong id="selected-thread-label">{{ context.label }}</strong>
          <span id="selected-thread-file">{{ context.folder }}</span>
          <p id="selected-thread-copy">{{ context.summary }}</p>
        </div>
        <div class="agent-directory" id="agent-directory" aria-label="角色编排">
          <button
            v-for="roleId in sessionRoleIds"
            :key="roleId"
            class="directory-item agent-item"
            :class="{ 'is-active': roleStore.activeRoleId === roleId }"
            type="button"
            @click="setActiveRole(roleId)"
          >
            <span class="directory-icon" aria-hidden="true" v-html="roleStore.getRole(roleId).icon"></span>
            <strong>{{ roleStore.getRole(roleId).name }}</strong>
            <span>{{ roleStore.getRole(roleId).shortDescription }}</span>
          </button>
        </div>
      </section>
    </aside>

    <main class="main-stage">
      <section class="settings-surface">
        <div class="surface-top">
          <div class="stage-meta">
            <span class="stage-kicker">机器人设置</span>
            <h2>Agent Profiles</h2>
            <p class="page-note">这一页跟随当前目录和会话走：目录里的每条会话都会映射成一个可编辑角色，新增会话先作为未编排角色进入这里。</p>
          </div>
          <TopNav active="robot" include-back />
        </div>

        <div class="settings-content">
          <section class="settings-panel">
            <div class="panel-head">
              <strong>当前对话</strong>
              <span id="context-role-total">{{ sessionRoleIds.length }} 个角色</span>
            </div>
            <div class="context-strip">
              <div class="context-chip">
                <span>关联目录</span>
                <strong id="context-file">{{ context.folder }}</strong>
              </div>
              <div class="context-chip">
                <span>选中会话</span>
                <strong id="context-thread">{{ context.label }}</strong>
              </div>
              <div class="context-chip">
                <span>联动说明</span>
                <strong>同目录会话、当前角色和角色矩阵同步刷新</strong>
              </div>
            </div>
          </section>

          <section class="settings-panel" id="primary-agent">
            <div class="panel-head">
              <strong>当前角色</strong>
              <span id="active-role-tag">{{ activeRole.tag }}</span>
            </div>
            <div class="agent-identity">
              <div class="identity-main">
                <div class="identity-title">
                  <span class="agent-avatar" id="active-role-avatar" aria-hidden="true" v-html="activeRole.icon"></span>
                  <div>
                    <strong id="active-role-name">{{ activeRole.name }}</strong>
                    <span id="active-role-description">{{ activeRole.description }}</span>
                  </div>
                </div>
                <div class="inline-edit-list">
                  <div class="inline-edit-card">
                    <div class="inline-edit-copy">
                      <span class="inline-edit-label">编辑对象</span>
                      <strong id="active-role-target">{{ activeRole.name }}</strong>
                    </div>
                    <span class="inline-static-note">从同目录会话角色或角色矩阵切换</span>
                  </div>
                  <div class="inline-edit-card">
                    <div class="inline-edit-copy">
                      <span class="inline-edit-label">默认模型</span>
                      <strong id="active-role-model">{{ activeRole.model }}</strong>
                    </div>
                    <button class="tiny-action" id="open-model-config" type="button" @click="openModelConfig">切换模型</button>
                  </div>
                  <div class="inline-edit-card">
                    <div class="inline-edit-copy">
                      <span class="inline-edit-label">压缩强度</span>
                      <strong id="active-role-compression">{{ activeRole.compression }}</strong>
                    </div>
                    <button class="tiny-action" id="open-compression-config" type="button" @click="openCompressionConfig">调整强度</button>
                  </div>
                </div>
              </div>
              <div class="quick-action-stack">
                <button class="quick-edit-button" id="edit-role-description" type="button" @click="openRoleDescriptionEditor">修改角色说明</button>
                <button class="quick-edit-button" id="edit-prompt-prefix" type="button" @click="openPromptPrefixEditor">编辑提示前缀</button>
                <button class="quick-edit-button" id="copy-role-config" type="button" @click="openRoleConfigCopy">复制当前角色配置</button>
              </div>
            </div>
          </section>

          <section class="settings-panel" id="route-agent">
            <div class="panel-head">
              <strong>模型与工具路由</strong>
              <span>Routing</span>
            </div>
            <div class="field-list">
              <div class="field-row">
                <div class="field-copy">
                  <strong>主模型分配</strong>
                  <span id="active-role-routing">{{ activeRole.routing }}</span>
                </div>
                <div class="chip-row" id="routing-chip-row" v-html="renderList(activeRole.routingChips)"></div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>工具权限</strong>
                  <span>不同角色只暴露必要的读写与浏览能力，减少角色漂移。</span>
                </div>
                <div class="tool-row" id="active-role-tools" v-html="renderList(activeRole.tools)"></div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>交接规则</strong>
                  <span>当前角色在这个会话里的交接口径，保持和左侧角色编排一致。</span>
                </div>
                <div class="value-box" id="active-role-handoff">{{ activeRole.handoff }}</div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>提示前缀</strong>
                  <span>当前角色接任务前先收紧自己的工作边界，避免拿错职责。</span>
                </div>
                <div class="value-box" id="active-role-prefix">{{ activeRole.promptPrefix }}</div>
              </div>
            </div>
          </section>

          <section class="settings-panel" id="test-agent">
            <div class="panel-head">
              <strong>角色矩阵</strong>
              <span>Role Matrix</span>
            </div>
            <div class="matrix-grid" id="role-matrix">
              <button
                v-for="roleId in sessionRoleIds"
                :key="roleId"
                class="role-matrix-card"
                :class="{ 'is-active': roleStore.activeRoleId === roleId }"
                type="button"
                @click="setActiveRole(roleId)"
              >
                <strong>{{ roleStore.getRole(roleId).name }}</strong>
                <p>{{ roleStore.getRole(roleId).matrixCopy }}</p>
              </button>
            </div>
          </section>

          <section class="settings-panel" id="snapshot-agent">
            <div class="panel-head">
              <strong>新增机器人入口</strong>
              <span>Expansion</span>
            </div>
            <div class="field-list">
              <div class="field-row">
                <div class="field-copy">
                  <strong>命名规范</strong>
                  <span>别名改成直接可编辑的输入框，角色职责用预置复选框和自定义补充来表达。</span>
                </div>
                <div class="field-stack">
                  <label class="stack-label" for="alias-input">别名</label>
                  <input class="text-input" id="alias-input" type="text" :value="activeRole.alias" @input="updateAlias" />
                </div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>角色职责</strong>
                  <span>先勾 2 到 4 个职责方向就够了，不够再补一句自定义说明。</span>
                </div>
                <div class="responsibility-stack">
                  <div class="duty-hint-row">
                    <span v-for="duty in roleStore.dutyPresets" :key="duty" class="status-chip">{{ duty }}</span>
                  </div>
                  <div class="checkbox-grid" id="role-duty-options">
                    <label v-for="duty in roleStore.dutyPresets" :key="duty" class="check-item">
                      <input
                        type="checkbox"
                        :value="duty"
                        :checked="activeRole.duties.includes(duty)"
                        @change="toggleDuty(duty, $event.target.checked)"
                      />
                      <span>{{ duty }}</span>
                    </label>
                  </div>
                  <label class="field-stack" for="custom-duty-input">
                    <span class="stack-label">自定义职责</span>
                    <input
                      class="text-input"
                      id="custom-duty-input"
                      type="text"
                      :value="activeRole.customDuty"
                      placeholder="例如：负责接口回放、埋点核对、验收收口"
                      @input="updateCustomDuty"
                    />
                  </label>
                </div>
              </div>
              <div class="field-row">
                <div class="field-copy">
                  <strong>Prompt 注入位置</strong>
                  <span>保留三个常见注入层级，作为新增角色时的默认落点。</span>
                </div>
                <div class="chip-row" id="prompt-layer-row" v-html="renderList(activeRole.promptLayers)"></div>
              </div>
            </div>
            <div class="settings-note">当前目录下的会话会作为角色进入编排；新增会话默认是未编排角色，可在这里补齐模型、职责和 Prompt。</div>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>
