<script setup>
import { useClock } from '../../composables/useClock';

const { time } = useClock();
</script>

<template>
  <span class="soft-clock" id="clock-02">{{ time }}</span>
</template>
