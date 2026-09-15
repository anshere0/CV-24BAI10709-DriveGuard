<script setup lang="ts">
defineProps<{
  label: string;
  value: string | number;
  helper?: string;
}>();
</script>

<template>
  <div class="glass-panel rounded-4 p-3 h-100">
    <div class="section-title">{{ label }}</div>
    <div class="metric-value mt-2">{{ value }}</div>
    <div v-if="helper" class="small text-secondary mt-2">{{ helper }}</div>
  </div>
</template>
