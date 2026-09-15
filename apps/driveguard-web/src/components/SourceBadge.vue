<script setup lang="ts">
import { computed } from "vue";

import { getSourceOriginMeta } from "../utils/analysisPresentation";

const props = defineProps<{
  sourceOrigin: string;
}>();

const meta = computed(() => getSourceOriginMeta(props.sourceOrigin));
</script>

<template>
  <span class="source-badge" :class="`source-badge-${meta.tone}`" :title="meta.detail">
    {{ meta.label }}
  </span>
</template>
