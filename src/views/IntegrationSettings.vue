<script setup>
import TopNav from '../components/layout/TopNav.vue';
import { useModal } from '../composables/useModal';
import { useSectionDirectory } from '../composables/useSectionDirectory';
import { useToast } from '../composables/useToast';
import { useMcpStore } from '../stores/mcp';
import { useSkillStore } from '../stores/skill';

const skillStore = useSkillStore();
const mcpStore = useMcpStore();
const { open } = useModal();
const { show: showToast } = useToast();

const directoryItems = [
  { hash: '#skill-overview', title: 'Skill 管理', copy: '安装来源、启用状态、Prompt 注入点和使用范围。' },
  { hash: '#prompt-mounts', title: 'Prompt 装载', copy: '技能如何进入系统层、角色层、任务层和记忆层。' },
  { hash: '#mcp-overview', title: 'MCP 接口管理', copy: '传输方式、认证、地址映射、健康检查和失败回退。' },
  { hash: '#integration-health', title: '运行健康', copy: '查看最近的联调状态、冷启动耗时和失败原因。' }
];

const { activeHash, jumpTo } = useSectionDirectory(directoryItems);

function escapeHTML(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function toggleSkill(skill) {
  const updated = skillStore.toggleSkill(skill.id);
  showToast(`${updated.name} 已${updated.status}`);
}

function openSkillModal(skill = null) {
  const target = skill || { id: '', name: '', source: 'local skill', status: '启用', scope: '', mounts: ['任务层'], lastRun: '新建' };
  open({
    modalTitle: skill ? `${skill.name} / Skill 配置` : '新增 Skill',
    modalSubtitle: '配置 skill 来源、启用状态和 Prompt 挂载位置。',
    saveLabel: skill ? '保存 Skill' : '新增 Skill',
    content: `
      <div class="modal-form-grid">
        <label class="modal-field">
          <span>Skill 名称</span>
          <input class="text-input" id="skill-name-input" type="text" value="${escapeHTML(target.name)}" />
        </label>
        <label class="modal-field">
          <span>来源</span>
          <input class="text-input" id="skill-source-input" type="text" value="${escapeHTML(target.source)}" />
        </label>
        <label class="modal-field modal-field-full">
          <span>使用范围</span>
          <textarea class="text-input modal-textarea" id="skill-scope-input">${escapeHTML(target.scope)}</textarea>
        </label>
        <label class="modal-field">
          <span>状态</span>
          <select class="text-input" id="skill-status-input">
            <option ${target.status === '启用' ? 'selected' : ''}>启用</option>
            <option ${target.status === '停用' ? 'selected' : ''}>停用</option>
            <option ${target.status === '待启用' ? 'selected' : ''}>待启用</option>
          </select>
        </label>
        <label class="modal-field">
          <span>挂载层</span>
          <input class="text-input" id="skill-mount-input" type="text" value="${escapeHTML(target.mounts.join(', '))}" />
        </label>
      </div>
    `,
    onSave(modalBody) {
      const patch = {
        name: modalBody.querySelector('#skill-name-input').value.trim() || target.name || 'custom-skill',
        source: modalBody.querySelector('#skill-source-input').value.trim() || 'local skill',
        scope: modalBody.querySelector('#skill-scope-input').value.trim() || '自定义能力',
        status: modalBody.querySelector('#skill-status-input').value,
        mounts: modalBody
          .querySelector('#skill-mount-input')
          .value.split(',')
          .map((item) => item.trim())
          .filter(Boolean),
        lastRun: target.lastRun || '新建'
      };
      patch.id = target.id || patch.name.toLowerCase().replaceAll(' ', '-');

      if (skill) {
        skillStore.saveSkill(skill, patch);
      } else {
        skillStore.addSkill(patch);
      }
      showToast('Skill 配置已保存');
    }
  });
}

function openEndpointModal(endpoint = null) {
  const target = endpoint || {
    id: '',
    name: '',
    transport: 'stdio',
    status: '待连接',
    auth: 'local',
    url: 'mcp://',
    tools: '',
    latency: '未检查'
  };
  open({
    modalTitle: endpoint ? `${endpoint.name} / MCP 接口` : '新增 MCP 接口',
    modalSubtitle: '配置接口名称、传输方式、认证方式、地址和工具映射。',
    saveLabel: endpoint ? '保存接口' : '新增接口',
    content: `
      <div class="modal-form-grid">
        <label class="modal-field">
          <span>接口名称</span>
          <input class="text-input" id="endpoint-name-input" type="text" value="${escapeHTML(target.name)}" />
        </label>
        <label class="modal-field">
          <span>传输方式</span>
          <input class="text-input" id="endpoint-transport-input" type="text" value="${escapeHTML(target.transport)}" />
        </label>
        <label class="modal-field">
          <span>认证方式</span>
          <input class="text-input" id="endpoint-auth-input" type="text" value="${escapeHTML(target.auth)}" />
        </label>
        <label class="modal-field">
          <span>状态</span>
          <select class="text-input" id="endpoint-status-input">
            <option ${target.status === '已连接' ? 'selected' : ''}>已连接</option>
            <option ${target.status === '待连接' ? 'selected' : ''}>待连接</option>
            <option ${target.status === '异常' ? 'selected' : ''}>异常</option>
          </select>
        </label>
        <label class="modal-field modal-field-full">
          <span>请求地址 / 标识</span>
          <input class="text-input" id="endpoint-url-input" type="text" value="${escapeHTML(target.url)}" />
        </label>
        <label class="modal-field modal-field-full">
          <span>工具映射</span>
          <textarea class="text-input modal-textarea" id="endpoint-tools-input">${escapeHTML(target.tools)}</textarea>
        </label>
      </div>
    `,
    onSave(modalBody) {
      const patch = {
        name: modalBody.querySelector('#endpoint-name-input').value.trim() || target.name || 'Custom MCP',
        transport: modalBody.querySelector('#endpoint-transport-input').value.trim() || 'stdio',
        auth: modalBody.querySelector('#endpoint-auth-input').value.trim() || 'local',
        status: modalBody.querySelector('#endpoint-status-input').value,
        url: modalBody.querySelector('#endpoint-url-input').value.trim() || 'mcp://',
        tools: modalBody.querySelector('#endpoint-tools-input').value.trim() || 'custom_tool',
        latency: target.latency || '未检查'
      };
      patch.id = target.id || patch.name.toLowerCase().replaceAll(' ', '-');

      if (endpoint) {
        Object.assign(endpoint, patch);
      } else {
        mcpStore.addEndpoint(patch);
      }
      showToast('MCP 接口已保存');
    }
  });
}

function checkEndpoint(endpoint) {
  mcpStore.checkEndpoint(endpoint.id);
  showToast(`${endpoint.name} 检查完成`);
}

function checkAllEndpoints() {
  mcpStore.checkAll();
  showToast('批量健康检查完成');
}
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar settings-sidebar">
      <section class="recent-file-block">
        <div class="sidebar-section-head">
          <span>管理目录</span>
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
            <span class="stage-kicker">能力管理</span>
            <h2>Skill &amp; MCP</h2>
            <p class="page-note">这一页负责管理 agent 的能力来源和接口边界，既看 skill 是怎么装进去的，也看 MCP 是怎么接进来的。</p>
          </div>
          <TopNav active="integration" include-back />
        </div>

        <div class="settings-content">
          <section class="settings-panel" id="skill-overview">
            <div class="panel-head">
              <strong>Skill 管理</strong>
              <span id="skill-count-label">{{ skillStore.skills.length }} 个已装载</span>
            </div>
            <div class="context-strip">
              <div class="context-chip">
                <span>默认挂载</span>
                <strong>系统层 + 角色层</strong>
              </div>
              <div class="context-chip">
                <span>当前策略</span>
                <strong>按页面场景自动装载</strong>
              </div>
              <div class="context-chip">
                <span>最近变更</span>
                <strong id="latest-skill-change">{{ skillStore.skills[0]?.name }} 已接入</strong>
              </div>
            </div>
            <div class="section-action-bar">
              <button class="tiny-action" id="open-install-skill" type="button" @click="openSkillModal(null)">新增 Skill</button>
              <button class="tiny-action" id="open-sync-skill-policy" type="button" @click="showToast('装载策略已同步')">同步装载策略</button>
            </div>
            <div class="catalog-grid" id="skill-catalog">
              <article v-for="skill in skillStore.skills" :key="skill.id" class="catalog-card">
                <div class="catalog-card-head">
                  <strong>{{ skill.name }}</strong>
                  <span class="status-chip">{{ skill.status }}</span>
                </div>
                <p>{{ skill.scope }}</p>
                <div class="meta-grid">
                  <span>来源</span><strong>{{ skill.source }}</strong>
                  <span>挂载</span><strong>{{ skill.mounts.join(' / ') }}</strong>
                  <span>最近</span><strong>{{ skill.lastRun }}</strong>
                </div>
                <div class="card-action-row">
                  <button class="tiny-action" type="button" :data-skill-toggle="skill.id" @click="toggleSkill(skill)">
                    {{ skill.status === '启用' ? '停用' : '启用' }}
                  </button>
                  <button class="tiny-action" type="button" :data-skill-config="skill.id" @click="openSkillModal(skill)">配置</button>
                </div>
              </article>
            </div>
          </section>

          <section class="settings-panel" id="prompt-mounts">
            <div class="panel-head">
              <strong>Prompt 装载</strong>
              <span>Mount Rules</span>
            </div>
            <div class="mount-grid" id="skill-mount-grid">
              <div v-for="skill in skillStore.skills" :key="skill.id" class="mount-card">
                <strong>{{ skill.name }}</strong>
                <p>{{ skill.mounts.join('、') }}</p>
                <span>{{ skill.scope }}</span>
              </div>
            </div>
          </section>

          <section class="settings-panel" id="mcp-overview">
            <div class="panel-head">
              <strong>MCP 接口管理</strong>
              <span id="endpoint-count-label">{{ mcpStore.endpoints.length }} 条接口</span>
            </div>
            <div class="section-action-bar">
              <button class="tiny-action" id="open-add-endpoint" type="button" @click="openEndpointModal(null)">新增接口</button>
              <button class="tiny-action" id="open-endpoint-check" type="button" @click="checkAllEndpoints">批量健康检查</button>
            </div>
            <div class="endpoint-list" id="mcp-endpoint-list">
              <article v-for="endpoint in mcpStore.endpoints" :key="endpoint.id" class="endpoint-card">
                <div class="endpoint-main">
                  <strong>{{ endpoint.name }}</strong>
                  <p>{{ endpoint.tools }}</p>
                  <div class="meta-grid">
                    <span>传输</span><strong>{{ endpoint.transport }}</strong>
                    <span>认证</span><strong>{{ endpoint.auth }}</strong>
                    <span>地址</span><strong>{{ endpoint.url }}</strong>
                  </div>
                </div>
                <div class="endpoint-side">
                  <span class="status-chip">{{ endpoint.status }}</span>
                  <span class="light-meta">{{ endpoint.latency }}</span>
                  <button class="tiny-action" type="button" :data-endpoint-check="endpoint.id" @click="checkEndpoint(endpoint)">检查</button>
                  <button class="tiny-action" type="button" :data-endpoint-edit="endpoint.id" @click="openEndpointModal(endpoint)">编辑</button>
                </div>
              </article>
            </div>
          </section>

          <section class="settings-panel" id="integration-health">
            <div class="panel-head">
              <strong>运行健康</strong>
              <span>Health Checks</span>
            </div>
            <div class="health-list" id="integration-health-list">
              <div v-for="item in mcpStore.healthItems" :key="item[0]" class="health-item">
                <strong>{{ item[0] }}</strong>
                <span class="status-chip">{{ item[1] }}</span>
                <p>{{ item[2] }}</p>
              </div>
            </div>
            <div class="settings-note">Skill 负责“能力来源”，MCP 负责“接口入口”，这两层一起稳定，平台才会真正稳定。</div>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>
