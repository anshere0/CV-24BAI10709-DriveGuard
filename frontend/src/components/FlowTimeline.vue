<script setup lang="ts">
import type { TimelineStep } from "../utils/analysisPresentation";

defineProps<{
  steps: TimelineStep[];
}>();
</script>

<template>
  <div class="flow-timeline">
    <div v-for="step in steps" :key="step.key" class="flow-step" :class="`flow-step-${step.state}`">
      <div class="flow-step-dot" />
      <div class="flow-step-label">{{ step.label }}</div>
    </div>
  </div>
</template>
