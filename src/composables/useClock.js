import { onBeforeUnmount, onMounted, ref } from 'vue';

function formatClock() {
  return new Date().toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

export function useClock() {
  const time = ref(formatClock());
  let timer = null;

  onMounted(() => {
    timer = window.setInterval(() => {
      time.value = formatClock();
    }, 1000);
  });

  onBeforeUnmount(() => {
    window.clearInterval(timer);
  });

  return { time };
}
