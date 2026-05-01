import { reactive } from 'vue';

const toastState = reactive({
  message: '',
  visible: false,
  timer: null
});

export function useToast() {
  function show(message) {
    toastState.message = message;
    toastState.visible = true;
    window.clearTimeout(toastState.timer);
    toastState.timer = window.setTimeout(() => {
      toastState.visible = false;
    }, 2200);
  }

  return { toastState, show };
}
