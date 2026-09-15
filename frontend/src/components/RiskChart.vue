<script setup lang="ts">
import { computed } from 'vue';
import { Line } from 'vue-chartjs';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

const props = defineProps<{
  labels: string[];
  data: number[];
}>();

const chartData = computed(() => ({
  labels: props.labels,
  datasets: [
    {
      label: 'Risk Score',
      backgroundColor: (ctx: any) => {
        const canvas = ctx.chart?.ctx;
        if (!canvas) return 'rgba(212, 106, 46, 0.15)';
        const gradient = canvas.createLinearGradient(0, 0, 0, 300);
        gradient.addColorStop(0, 'rgba(212, 106, 46, 0.25)');
        gradient.addColorStop(1, 'rgba(212, 106, 46, 0.02)');
        return gradient;
      },
      borderColor: '#d46a2e',
      borderWidth: 2.5,
      data: props.data,
      fill: true,
      tension: 0.3,
      stepped: false,
      pointRadius: props.data.length <= 20 ? 4 : 2,
      pointBackgroundColor: '#d46a2e',
      pointBorderColor: '#fff',
      pointBorderWidth: 2,
      pointHoverRadius: 6,
    }
  ]
}));

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: {
    mode: 'index' as const,
    intersect: false,
  },
  plugins: {
    legend: {
      display: false,
    },
    tooltip: {
      backgroundColor: 'rgba(16, 37, 45, 0.9)',
      titleFont: { size: 12, weight: '600' as const },
      bodyFont: { size: 12 },
      cornerRadius: 8,
      padding: 10,
      callbacks: {
        label: (context: any) => `Risk Score: ${context.parsed.y}`,
      },
    },
  },
  scales: {
    x: {
      ticks: {
        maxTicksLimit: 10,
        maxRotation: 0,
        autoSkip: true,
        color: '#627076',
        font: { size: 11 },
      },
      grid: {
        display: false,
      },
      border: {
        display: false,
      },
    },
    y: {
      min: 0,
      max: 100,
      ticks: {
        stepSize: 20,
        color: '#627076',
        font: { size: 11 },
      },
      grid: {
        color: 'rgba(16, 37, 45, 0.06)',
      },
      border: {
        display: false,
      },
    },
  },
};
</script>

<template>
  <div style="position: relative; height: 320px;">
    <Line :data="chartData" :options="chartOptions as any" />
  </div>
</template>
