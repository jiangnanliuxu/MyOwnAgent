<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useModal } from '../../composables/useModal';

const bodyRef = ref(null);
const saving = ref(false);
const { modalState, close } = useModal();

async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    await modalState.onSave?.(bodyRef.value);
    close();
  } catch (error) {
    console.error(error);
  } finally {
    saving.value = false;
  }
}

function handleKeydown(event) {
  if (event.key === 'Escape' && modalState.visible) {
    close();
  }
}

onMounted(() => document.addEventListener('keydown', handleKeydown));
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown));
</script>

<template>
  <div id="modal-host" class="modal-host" :class="{ 'is-hidden': !modalState.visible }">
    <div class="modal-backdrop" data-close-modal="true" @click="close"></div>
    <section class="modal-sheet" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div class="modal-head">
        <div>
          <strong id="modal-title">{{ modalState.title }}</strong>
          <span id="modal-subtitle">{{ modalState.subtitle }}</span>
        </div>
        <button class="icon-button" id="modal-close-button" type="button" data-close-modal="true" data-tooltip="关闭" @click="close">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6 18 18"></path>
            <path d="M18 6 6 18"></path>
          </svg>
        </button>
      </div>
      <div class="modal-body" id="modal-body" ref="bodyRef" v-html="modalState.content"></div>
      <div class="modal-footer">
        <button class="tiny-action" id="modal-cancel-button" type="button" data-close-modal="true" @click="close">取消</button>
        <button class="tiny-action modal-primary-action" id="modal-save-button" type="button" :disabled="saving" @click="save">
          {{ saving ? '保存中...' : modalState.saveLabel }}
        </button>
      </div>
    </section>
  </div>
</template>
