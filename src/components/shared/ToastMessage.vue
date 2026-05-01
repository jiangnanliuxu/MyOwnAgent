<script setup>
import { useToast } from '../../composables/useToast';

const { toastState } = useToast();
</script>

<template>
  <div id="toast" class="toast" :class="{ 'is-hidden': !toastState.visible }">{{ toastState.message }}</div>
</template>
