import { onBeforeUnmount, onMounted, ref } from 'vue';

export function useSectionDirectory(items) {
  const activeHash = ref(items[0]?.hash || '');
  let observer = null;

  function setActive(hash) {
    activeHash.value = hash;
  }

  function jumpTo(hash) {
    const section = document.querySelector(hash);
    if (!section) return;
    section.scrollIntoView({ behavior: 'smooth', block: 'start' });
    history.replaceState(null, '', hash);
    setActive(hash);
  }

  onMounted(() => {
    const sections = items
      .map((item) => ({ ...item, section: document.querySelector(item.hash) }))
      .filter((item) => item.section);

    if (!sections.length) return;

    observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (!visible) return;
        const match = sections.find((item) => item.section === visible.target);
        if (match) setActive(match.hash);
      },
      { root: null, rootMargin: '-18% 0px -55% 0px', threshold: [0.2, 0.45, 0.7] }
    );

    sections.forEach((item) => observer.observe(item.section));
    const current = sections.find((item) => item.hash === window.location.hash) || sections[0];
    setActive(current.hash);
  });

  onBeforeUnmount(() => {
    observer?.disconnect();
  });

  return { activeHash, jumpTo, setActive };
}
