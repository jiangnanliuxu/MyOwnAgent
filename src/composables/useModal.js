import { reactive } from 'vue';

const modalState = reactive({
  visible: false,
  title: '配置',
  subtitle: '在这里补全当前配置。',
  content: '',
  saveLabel: '保存配置',
  onSave: null
});

export function useModal() {
  function open({ modalTitle, modalSubtitle, content, saveLabel, onSave }) {
    modalState.title = modalTitle;
    modalState.subtitle = modalSubtitle;
    modalState.content = content;
    modalState.saveLabel = saveLabel || '保存配置';
    modalState.onSave = onSave || null;
    modalState.visible = true;
  }

  function close() {
    modalState.visible = false;
    modalState.content = '';
    modalState.onSave = null;
  }

  return { modalState, open, close };
}
