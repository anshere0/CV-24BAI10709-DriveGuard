<script setup lang="ts">
const props = defineProps<{
  status: string;
}>();

function badgeClass(status: string): string {
  if (status === "completed") {
    return "text-bg-success";
  }
  if (status === "processing") {
    return "text-bg-info";
  }
  if (status === "queued") {
    return "text-bg-warning";
  }
  return "text-bg-danger";
}
</script>

<template>
  <span class="badge rounded-pill" :class="badgeClass(props.status)">
    {{ props.status }}
  </span>
</template>
