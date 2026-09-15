<script setup lang="ts">
import { computed } from 'vue';
import { Doughnut } from 'vue-chartjs';
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
} from 'chart.js';

ChartJS.register(ArcElement, Tooltip, Legend);

const props = defineProps<{
  eventCounts: Record<string, number>;
}>();

const EVENT_COLORS: Record<string, string> = {
  PHONE_USE: '#d46a2e',
  PHONE_NEAR_FACE: '#efab76',
  PHONE_VISIBLE: '#f0c9a8',
  PHONE_DETECTED_SHORT: '#f5dcc8',
  DISTRACTION: '#10252d',
  DROWSINESS: '#c34a4a',
  YAWNING: '#627076',
};

const sortedEntries = computed(() =>
  Object.entries(props.eventCounts).sort((a, b) => b[1] - a[1])
);

const chartData = computed(() => ({
  labels: sortedEntries.value.map(([type]) => type.replace(/_/g, ' ')),
  datasets: [
    {
      data: sortedEntries.value.map(([, count]) => count),
      backgroundColor: sortedEntries.value.map(
        ([type]) => EVENT_COLORS[type] ?? '#a0adb3'
      ),
      borderColor: 'rgba(255, 255, 255, 0.9)',
      borderWidth: 3,
      hoverBorderWidth: 0,
      hoverOffset: 8,
    },
  ],
}));

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: '62%',
  plugins: {
    legend: {
      position: 'bottom' as const,
      labels: {
        padding: 16,
        usePointStyle: true,
        pointStyle: 'circle' as const,
        font: { size: 11, weight: '600' as const },
        color: '#627076',
      },
    },
    tooltip: {
      backgroundColor: 'rgba(16, 37, 45, 0.92)',
      cornerRadius: 8,
      padding: 10,
      bodyFont: { size: 12 },
      titleFont: { size: 12, weight: '600' as const },
    },
  },
};
</script>

<template>
  <div style="position: relative; height: 280px;">
    <Doughnut :data="chartData" :options="chartOptions as any" />
  </div>
</template>
