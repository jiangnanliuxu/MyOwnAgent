import { ref, watch } from 'vue';

export function useLocalStorage(key, fallbackValue) {
  const stored = typeof window === 'undefined' ? null : window.localStorage.getItem(key);
  const value = ref(stored ?? fallbackValue);

  watch(value, (nextValue) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(key, nextValue);
    }
  });

  return value;
}
