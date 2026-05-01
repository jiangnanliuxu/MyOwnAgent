<script setup>
import ModalHost from './components/shared/ModalHost.vue';
import ToastMessage from './components/shared/ToastMessage.vue';
</script>

<template>
  <router-view />
  <ModalHost />
  <ToastMessage />
</template>
