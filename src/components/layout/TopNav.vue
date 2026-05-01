<script setup>
import { computed } from 'vue';
import ClockWidget from './ClockWidget.vue';

const props = defineProps({
  active: {
    type: String,
    required: true
  },
  includeBack: {
    type: Boolean,
    default: false
  },
  robotThreadId: {
    type: String,
    default: null
  }
});

const icons = {
  back: '<path d="M15 18 9 12l6-6"></path>',
  settings: '<path d="M12 3v3"></path><path d="M12 18v3"></path><path d="m4.9 4.9 2.1 2.1"></path><path d="m17 17 2.1 2.1"></path><path d="M3 12h3"></path><path d="M18 12h3"></path><path d="m4.9 19.1 2.1-2.1"></path><path d="m17 7 2.1-2.1"></path><circle cx="12" cy="12" r="3.2"></circle>',
  robot: '<rect x="6" y="8" width="12" height="10" rx="2"></rect><path d="M12 4v4"></path><path d="M9 12h.01"></path><path d="M15 12h.01"></path><path d="M9 16h6"></path>',
  integration: '<path d="M8 7h8"></path><path d="M8 12h8"></path><path d="M8 17h5"></path><rect x="4" y="4" width="16" height="16" rx="2"></rect>'
};

const robotHref = computed(() =>
  props.robotThreadId ? `/robot-settings?thread=${encodeURIComponent(props.robotThreadId)}` : '/robot-settings'
);
</script>

<template>
  <div class="top-actions">
    <a v-if="includeBack" class="nav-pill" href="/">
      <svg viewBox="0 0 24 24" aria-hidden="true" v-html="icons.back"></svg>
      <span>返回工作台</span>
    </a>
    <a class="nav-pill" :class="{ 'is-active': active === 'settings' }" href="/settings">
      <svg viewBox="0 0 24 24" aria-hidden="true" v-html="icons.settings"></svg>
      <span>设置</span>
    </a>
    <a
      class="nav-pill"
      :class="{ 'is-active': active === 'robot' }"
      :href="active === 'robot' ? '/robot-settings' : robotHref"
      id="robot-settings-link"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true" v-html="icons.robot"></svg>
      <span>机器人设置</span>
    </a>
    <a class="nav-pill" :class="{ 'is-active': active === 'integration' }" href="/integration">
      <svg viewBox="0 0 24 24" aria-hidden="true" v-html="icons.integration"></svg>
      <span>能力管理</span>
    </a>
    <ClockWidget />
  </div>
</template>
