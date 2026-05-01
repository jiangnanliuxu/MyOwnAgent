import { createRouter, createWebHistory } from 'vue-router';
import MainWorkspace from '../views/MainWorkspace.vue';
import SystemSettings from '../views/SystemSettings.vue';
import RobotSettings from '../views/RobotSettings.vue';
import IntegrationSettings from '../views/IntegrationSettings.vue';

const routes = [
  { path: '/', name: 'workspace', component: MainWorkspace, meta: { title: 'Agent Desk / 助手工作台' } },
  { path: '/settings', name: 'settings', component: SystemSettings, meta: { title: 'Agent Desk / 设置' } },
  { path: '/robot-settings', name: 'robot-settings', component: RobotSettings, meta: { title: 'Agent Desk / 机器人设置' } },
  { path: '/integration', name: 'integration', component: IntegrationSettings, meta: { title: 'Agent Desk / 能力管理' } }
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to) {
    if (to.hash) return { el: to.hash, behavior: 'smooth' };
    return { top: 0 };
  }
});

router.afterEach((to) => {
  document.title = to.meta.title || 'Agent Desk';
});
