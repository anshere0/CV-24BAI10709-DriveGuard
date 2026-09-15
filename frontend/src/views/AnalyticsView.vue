<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import { fetchSession, fetchSessionIncidents } from '../services/api';
import type { AnalysisSessionDto, IncidentDto } from '../types/backend';
import RiskChart from '../components/RiskChart.vue';
import EventDonutChart from '../components/EventDonutChart.vue';

const route = useRoute();
const sessionId = route.params.sessionId as string;

const session = ref<AnalysisSessionDto | null>(null);
const incidents = ref<IncidentDto[]>([]);
const isLoading = ref(true);
const error = ref<string | null>(null);

const riskTimelineLabels = ref<string[]>([]);
const riskTimelineData = ref<number[]>([]);

async function loadData() {
  try {
    isLoading.value = true;
    session.value = await fetchSession(sessionId);
    const incidentResult = await fetchSessionIncidents(sessionId);
    incidents.value = incidentResult.items;
    generateTimeline();
  } catch (err: any) {
    error.value = err.message || 'Failed to load analytics data';
  } finally {
    isLoading.value = false;
  }
}

function generateTimeline() {
  if (!session.value) return;
  const labels: string[] = [];
  const data: number[] = [];
  const duration = session.value.duration_seconds;

  if (incidents.value.length === 0) {
    labels.push("0.0s");
    data.push(100);
    labels.push(`${duration.toFixed(1)}s`);
    data.push(session.value.score);
    riskTimelineLabels.value = labels;
    riskTimelineData.value = data;
    return;
  }

  let currentScore = 100;
  labels.push("0.0s");
  data.push(currentScore);

  const sortedIncidents = [...incidents.value].sort((a, b) => a.started_at_seconds - b.started_at_seconds);

  for (const incident of sortedIncidents) {
    const time = incident.started_at_seconds;
    if (labels.length === 0 || labels[labels.length - 1] !== `${time.toFixed(1)}s`) {
      labels.push(`${time.toFixed(1)}s`);
      data.push(currentScore);
    }
    currentScore = Math.max(0, currentScore - incident.max_severity);
    labels.push(`${time.toFixed(1)}s`);
    data.push(currentScore);
  }

  if (duration > 0) {
    labels.push(`${duration.toFixed(1)}s`);
    data.push(currentScore);
  }

  riskTimelineLabels.value = labels;
  riskTimelineData.value = data;
}

const severityClass = computed(() => {
  if (!session.value) return 'session-score-attention';
  if (session.value.score >= 80) return 'session-score-excellent';
  if (session.value.score >= 60) return 'session-score-attention';
  return 'session-score-critical';
});

const severityLabel = computed(() => {
  if (!session.value) return 'Unknown';
  if (session.value.score >= 80) return 'Low Risk';
  if (session.value.score >= 60) return 'Moderate Risk';
  if (session.value.score >= 40) return 'High Risk';
  return 'Critical Risk';
});

const sourceFileName = computed(() => {
  if (!session.value) return '';
  return session.value.source_name.split(/[/\\]/).pop() ?? session.value.source_name;
});

const eventTypeSummary = computed(() => {
  const counts: Record<string, { count: number; totalPenalty: number }> = {};
  for (const inc of incidents.value) {
    if (!counts[inc.event_type]) {
      counts[inc.event_type] = { count: 0, totalPenalty: 0 };
    }
    counts[inc.event_type].count += inc.occurrences;
    counts[inc.event_type].totalPenalty += inc.max_severity;
  }
  return Object.entries(counts).sort((a, b) => b[1].totalPenalty - a[1].totalPenalty);
});

const donutCounts = computed(() => {
  const counts: Record<string, number> = {};
  for (const inc of incidents.value) {
    counts[inc.event_type] = (counts[inc.event_type] ?? 0) + inc.occurrences;
  }
  return counts;
});

const penaltyTotal = computed(() => {
  if (!session.value) return 0;
  return 100 - session.value.score;
});

function downloadPdfReport() {
  if (!session.value) return;
  const s = session.value;
  const incs = incidents.value;
  const lines: string[] = [];

  lines.push('DRIVEGUARD AI — SESSION ANALYSIS REPORT');
  lines.push('='.repeat(50));
  lines.push('');
  lines.push(`Session ID:    ${s.id}`);
  lines.push(`Source:        ${sourceFileName.value}`);
  lines.push(`Date:          ${new Date(s.created_at).toLocaleString()}`);
  lines.push(`Duration:      ${s.duration_seconds.toFixed(1)}s`);
  lines.push(`Frames:        ${s.frame_count}`);
  lines.push(`Risk Score:    ${s.score} / 100 (${severityLabel.value})`);
  lines.push(`Total Penalty: -${penaltyTotal.value}`);
  lines.push('');
  lines.push('-'.repeat(50));
  lines.push('EVENT TYPE BREAKDOWN');
  lines.push('-'.repeat(50));

  if (eventTypeSummary.value.length === 0) {
    lines.push('No risky events detected — clean session.');
  } else {
    for (const [type, stats] of eventTypeSummary.value) {
      lines.push(`  ${type.replace(/_/g, ' ').padEnd(24)} | ${String(stats.count).padStart(3)} occurrences | -${stats.totalPenalty} penalty`);
    }
  }

  lines.push('');
  lines.push('-'.repeat(50));
  lines.push('INCIDENT LOG');
  lines.push('-'.repeat(50));

  if (incs.length === 0) {
    lines.push('No incidents recorded.');
  } else {
    lines.push(`${'Time'.padEnd(18)} ${'Type'.padEnd(24)} ${'Count'.padStart(5)} ${'Penalty'.padStart(7)}  Details`);
    lines.push('-'.repeat(90));
    for (const inc of incs) {
      const time = `${inc.started_at_seconds.toFixed(1)}s-${inc.ended_at_seconds.toFixed(1)}s`;
      lines.push(`${time.padEnd(18)} ${inc.event_type.padEnd(24)} ${String(inc.occurrences).padStart(5)} ${('-' + inc.max_severity).padStart(7)}  ${inc.last_message}`);
    }
  }

  lines.push('');
  lines.push('-'.repeat(50));
  lines.push('CV PIPELINE CONFIGURATION');
  lines.push('-'.repeat(50));
  lines.push('Model:         YOLOv8n + MediaPipe Face Landmarker');
  lines.push('Detections:    Drowsiness (EAR), Yawning (MAR), Distraction (Head Pose), Phone Use (YOLO)');
  lines.push('Risk Engine:   Weighted penalty scoring with severity thresholds');
  lines.push('');
  lines.push('Generated by DriveGuard AI — Computer Vision Course Project');

  const blob = new Blob([lines.join('\n')], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `driveguard-report-${s.id.slice(0, 8)}.txt`;
  a.click();
  URL.revokeObjectURL(url);
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div v-if="isLoading" class="glass-panel rounded-4 p-5 text-center text-secondary">
    Loading Analytics...
  </div>

  <div v-else-if="error" class="alert alert-danger">
    {{ error }}
  </div>

  <template v-else-if="session">
    <!-- Hero header -->
    <section class="hero-panel rounded-4 p-4 p-lg-5 mb-4">
      <div class="row g-4 align-items-center">
        <div class="col-lg-7">
          <span class="stage-badge mb-3">Session Analytics</span>
          <h1 class="display-6 fw-bold mb-2">{{ sourceFileName }}</h1>
          <p class="lead text-secondary mb-0">
            {{ session.frame_count }} frames analyzed over {{ session.duration_seconds.toFixed(1) }}s
            — {{ incidents.length }} incident{{ incidents.length === 1 ? '' : 's' }} detected.
          </p>
          <div class="d-flex flex-wrap gap-2 mt-3">
            <button class="btn btn-dark" @click="downloadPdfReport">
              ⬇ Download Report
            </button>
            <RouterLink class="btn btn-outline-dark" :to="{ name: 'session-detail', params: { sessionId: session.id } }">
              Session Details
            </RouterLink>
          </div>
        </div>
        <div class="col-lg-5 d-flex justify-content-center">
          <div class="session-score-shell" :class="severityClass">
            <div class="session-score-value">{{ session.score }}</div>
            <div class="small mt-1">{{ severityLabel }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Stats Row -->
    <section class="row g-4 mb-4">
      <div class="col-6 col-xl-3">
        <div class="glass-panel rounded-4 p-3 h-100">
          <div class="section-title">Duration</div>
          <div class="metric-value mt-2">{{ session.duration_seconds.toFixed(1) }}s</div>
          <div class="small text-secondary mt-1">Clip length analyzed</div>
        </div>
      </div>
      <div class="col-6 col-xl-3">
        <div class="glass-panel rounded-4 p-3 h-100">
          <div class="section-title">Incidents</div>
          <div class="metric-value mt-2">{{ incidents.length }}</div>
          <div class="small text-secondary mt-1">Total detected events</div>
        </div>
      </div>
      <div class="col-6 col-xl-3">
        <div class="glass-panel rounded-4 p-3 h-100">
          <div class="section-title">Frames</div>
          <div class="metric-value mt-2">{{ session.frame_count }}</div>
          <div class="small text-secondary mt-1">Frames processed by CV</div>
        </div>
      </div>
      <div class="col-6 col-xl-3">
        <div class="glass-panel rounded-4 p-3 h-100">
          <div class="section-title">Penalty</div>
          <div class="metric-value mt-2">-{{ penaltyTotal }}</div>
          <div class="small text-secondary mt-1">Total points deducted</div>
        </div>
      </div>
    </section>

    <!-- Risk Timeline Chart -->
    <section class="glass-panel rounded-4 p-4 mb-4">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
          <div class="section-title">Risk Score Timeline</div>
          <div class="small text-secondary">How the driver's risk score changed over time</div>
        </div>
      </div>
      <RiskChart v-if="riskTimelineData.length > 0" :labels="riskTimelineLabels" :data="riskTimelineData" />
      <div v-else class="empty-panel rounded-4 p-4 text-center text-secondary">
        No timeline data available for this session.
      </div>
    </section>

    <!-- Donut Chart + Event Breakdown -->
    <section class="row g-4 mb-4">
      <div class="col-lg-5">
        <div class="glass-panel rounded-4 p-4 h-100">
          <div class="section-title mb-3">Event Distribution</div>
          <div v-if="Object.keys(donutCounts).length > 0">
            <EventDonutChart :event-counts="donutCounts" />
          </div>
          <div v-else class="empty-panel rounded-4 p-4 text-center">
            <div class="fw-semibold mb-2">Clean Session</div>
            <div class="small text-secondary">No events were detected.</div>
          </div>
        </div>
      </div>

      <div class="col-lg-7">
        <div class="glass-panel rounded-4 p-4 h-100">
          <div class="section-title mb-3">Event Type Breakdown</div>
          <div v-if="eventTypeSummary.length === 0" class="empty-panel rounded-4 p-4">
            <div class="fw-semibold mb-2">Clean session</div>
            <div class="small text-secondary">No risky behavior was detected during this recording.</div>
          </div>
          <div v-else class="d-grid gap-3">
            <div v-for="[type, stats] in eventTypeSummary" :key="type" class="soft-card rounded-4 p-3">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <span class="fw-semibold">{{ type.replace(/_/g, ' ') }}</span>
                <span class="score-pill score-pill-sm">-{{ stats.totalPenalty }}</span>
              </div>
              <div class="small text-secondary">
                {{ stats.count }} occurrence{{ stats.count === 1 ? '' : 's' }} across all incident windows
              </div>
              <div class="progress progress-shell mt-2">
                <div
                  class="progress-bar processing-progress-bar"
                  role="progressbar"
                  :style="{ width: `${Math.min(100, (stats.totalPenalty / Math.max(penaltyTotal, 1)) * 100)}%` }"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Incident Log -->
    <section class="glass-panel rounded-4 p-4 mb-4">
      <div class="section-title mb-3">Incident Log</div>
      <div v-if="incidents.length === 0" class="empty-panel rounded-4 p-4">
        <div class="fw-semibold mb-2">No incidents</div>
        <div class="small text-secondary">The driver exhibited safe behavior throughout this session.</div>
      </div>
      <div v-else class="table-responsive">
        <table class="table table-sm align-middle small mb-0">
          <thead>
            <tr>
              <th>Time</th>
              <th>Type</th>
              <th>Count</th>
              <th>Penalty</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="inc in incidents" :key="inc.id">
              <td>{{ inc.started_at_seconds.toFixed(1) }}s — {{ inc.ended_at_seconds.toFixed(1) }}s</td>
              <td>
                <span class="stage-badge" style="font-size: 0.7rem;">{{ inc.event_type.replace(/_/g, ' ') }}</span>
              </td>
              <td>{{ inc.occurrences }}</td>
              <td class="fw-semibold" style="color: var(--dg-accent-dark);">-{{ inc.max_severity }}</td>
              <td class="text-secondary">{{ inc.last_message }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Navigation -->
    <section class="glass-panel rounded-4 p-4">
      <div class="d-flex flex-wrap gap-3">
        <RouterLink class="btn btn-outline-dark" :to="{ name: 'history' }">
          All Sessions
        </RouterLink>
        <RouterLink class="btn btn-outline-dark" :to="{ name: 'monitor' }">
          Record New Session
        </RouterLink>
      </div>
    </section>
  </template>
</template>
