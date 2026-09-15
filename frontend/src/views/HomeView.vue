<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

import MetricCard from "../components/MetricCard.vue";
import { fetchHealth, fetchSessions } from "../services/api";
import type { AnalysisSessionDto, HealthDto } from "../types/backend";
import { getSourceOriginMeta } from "../utils/analysisPresentation";

const loading = ref<boolean>(true);
const error = ref<string>("");
const health = ref<HealthDto | null>(null);
const sessions = ref<AnalysisSessionDto[]>([]);

const averageScore = computed(() => {
  if (sessions.value.length === 0) {
    return "—";
  }
  const total = sessions.value.reduce((sum, session) => sum + session.score, 0);
  return Math.round(total / sessions.value.length);
});

const latestSession = computed(() => sessions.value[0] ?? null);
const latestSessionOrigin = computed(() =>
  latestSession.value ? getSourceOriginMeta(latestSession.value.source_origin) : null,
);
const healthTone = computed(() => (health.value?.status === "ok" ? "text-bg-success" : "text-bg-danger"));

const triggers = [
  {
    icon: "😴",
    title: "Drowsiness Detection",
    description: "Monitors the Eye Aspect Ratio (EAR) using MediaPipe Face Landmarker. Triggers when eyes remain closed beyond the configured threshold.",
    tech: "EAR < 0.23 for 1.5s",
    penalty: "-15 points",
  },
  {
    icon: "📱",
    title: "Phone Use Detection",
    description: "Uses YOLOv8 object detection to identify cell phones. Calculates spatial proximity to the driver's face using bounding box intersection.",
    tech: "YOLO confidence > 0.25",
    penalty: "-10 points",
  },
  {
    icon: "🫣",
    title: "Distraction Detection",
    description: "Tracks head orientation using facial landmark pitch and yaw angles. Triggers when the driver looks away from the road for too long.",
    tech: "Yaw > 0.035 for 2.0s",
    penalty: "-8 points",
  },
  {
    icon: "🥱",
    title: "Yawning Detection",
    description: "Analyzes the Mouth Aspect Ratio (MAR) from face landmarks. Detects prolonged yawning as an indicator of fatigue.",
    tech: "MAR > 0.55 for 1.0s",
    penalty: "-3 points",
  },
];

async function loadDashboard(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const [healthResponse, sessionsResponse] = await Promise.all([
      fetchHealth(),
      fetchSessions(),
    ]);
    health.value = healthResponse;
    sessions.value = sessionsResponse.items;
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load dashboard data.";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadDashboard();
});
</script>

<template>
  <section class="hero-panel rounded-4 p-4 p-lg-5 mb-4">
    <div class="row g-4 align-items-center">
      <div class="col-lg-7">
        <span class="stage-badge mb-3">DriveGuard AI</span>
        <h1 class="display-6 fw-bold mb-3">Driver Monitoring Dashboard</h1>
        <p class="lead mb-4">
          Real-time computer vision system for monitoring driver behavior, detecting drowsiness,
          phone usage, distraction, and yawning using YOLOv8 and MediaPipe.
        </p>
        <div class="d-flex flex-wrap gap-3">
          <RouterLink class="btn btn-dark btn-lg" :to="{ name: 'monitor' }">Launch Live Monitor</RouterLink>
          <button class="btn btn-outline-dark btn-lg" type="button" @click="loadDashboard">Refresh Data</button>
        </div>
      </div>

      <div class="col-lg-5">
        <div class="glass-panel rounded-4 p-4">
          <div class="section-title mb-3">System Status</div>
          <div class="soft-card rounded-4 p-3">
            <div class="d-flex justify-content-between align-items-center gap-3">
              <div>
                <div class="fw-semibold">Backend Connection</div>
                <div class="small text-secondary">Database: {{ health?.database_url ?? "unknown" }}</div>
              </div>
              <span class="badge rounded-pill px-3 py-2" :class="healthTone">
                {{ health?.status ?? (loading ? "checking" : "offline") }}
              </span>
            </div>
          </div>
          <div class="soft-card rounded-4 p-3 mt-3">
            <div class="d-flex justify-content-between align-items-center gap-3">
              <div>
                <div class="fw-semibold">CV Engine</div>
                <div class="small text-secondary">YOLOv8n + MediaPipe Face Landmarker</div>
              </div>
              <span class="badge rounded-pill px-3 py-2 text-bg-success">active</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <div v-if="error" class="alert alert-danger">{{ error }}</div>

  <section class="row g-4 mb-4">
    <div class="col-md-6 col-xl-4">
      <MetricCard label="Total Sessions" :value="sessions.length" helper="Total recorded monitoring sessions" />
    </div>
    <div class="col-md-6 col-xl-4">
      <MetricCard label="Average Score" :value="averageScore" helper="Across all recorded sessions" />
    </div>
    <div class="col-md-6 col-xl-4">
      <MetricCard label="Latest Score" :value="latestSession?.score ?? '—'" helper="Risk score of the latest session" />
    </div>
  </section>

  <!-- What We Monitor -->
  <section class="glass-panel rounded-4 p-4 mb-4">
    <div class="section-title mb-1">What The AI Monitors</div>
    <div class="small text-secondary mb-4">
      DriveGuard uses computer vision to track these four categories of driver behavior in real-time.
      Each trigger deducts points from the starting risk score of 100.
    </div>
    <div class="row g-3">
      <div v-for="trigger in triggers" :key="trigger.title" class="col-md-6 col-xl-3">
        <div class="soft-card rounded-4 p-3 h-100 trigger-card">
          <div class="trigger-icon mb-2">{{ trigger.icon }}</div>
          <div class="fw-bold mb-2">{{ trigger.title }}</div>
          <div class="small text-secondary mb-3">{{ trigger.description }}</div>
          <div class="d-flex justify-content-between align-items-center">
            <span class="trigger-tech-badge">{{ trigger.tech }}</span>
            <span class="trigger-penalty-badge">{{ trigger.penalty }}</span>
          </div>
        </div>
      </div>
    </div>
  </section>

  <section class="row g-4 mb-4">
    <div class="col-lg-8">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="d-flex justify-content-between align-items-center gap-3 mb-3">
          <div>
            <div class="section-title">Recent Sessions</div>
            <div class="small text-secondary">Latest driver monitoring activity.</div>
          </div>
          <RouterLink class="btn btn-outline-dark btn-sm" :to="{ name: 'history' }">View All</RouterLink>
        </div>

        <div v-if="loading" class="text-secondary">Loading sessions…</div>
        <div v-else-if="sessions.length === 0" class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">No sessions yet</div>
          <div class="small text-secondary">Launch the live monitor to record a session.</div>
        </div>
        <div v-else class="table-responsive">
          <table class="table align-middle mb-0">
            <thead>
              <tr>
                <th>Date</th>
                <th>Source</th>
                <th>Score</th>
                <th>Duration</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="session in sessions.slice(0, 6)" :key="session.id">
                <td>{{ new Date(session.created_at).toLocaleString() }}</td>
                <td class="text-break" style="max-width: 200px;">{{ session.source_name.split(/[/\\]/).pop() }}</td>
                <td>
                  <span class="badge" :class="session.score >= 80 ? 'bg-success' : session.score >= 60 ? 'bg-warning' : 'bg-danger'">
                    {{ session.score }}
                  </span>
                </td>
                <td>{{ session.duration_seconds.toFixed(1) }}s</td>
                <td>
                  <div class="d-flex gap-1">
                    <RouterLink class="btn btn-sm btn-outline-dark" :to="{ name: 'session-detail', params: { sessionId: session.id } }">
                      Review
                    </RouterLink>
                    <RouterLink class="btn btn-sm btn-dark" :to="{ name: 'analytics', params: { sessionId: session.id } }">
                      Analytics
                    </RouterLink>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="col-lg-4">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Latest session</div>
        <div v-if="loading" class="text-secondary">Loading latest session…</div>
        <div v-else-if="latestSession" class="d-grid gap-3">
          <div class="soft-card rounded-4 p-3">
            <div class="d-flex justify-content-between align-items-start gap-3">
              <div>
                <div class="fw-semibold">{{ latestSession.source_name.split(/[/\\]/).pop() }}</div>
                <div class="small text-secondary mt-1">{{ latestSessionOrigin?.detail }}</div>
              </div>
              <div class="score-pill">{{ latestSession.score }}</div>
            </div>
          </div>
          <div class="small text-secondary">
            {{ latestSession.frame_count }} frames • {{ latestSession.duration_seconds.toFixed(1) }}s •
            {{ Object.keys(latestSession.event_counts).length }} event types
          </div>
          <RouterLink
            class="btn btn-dark"
            :to="{ name: 'analytics', params: { sessionId: latestSession.id } }"
          >
            View Analytics
          </RouterLink>
        </div>
        <div v-else class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">No session yet</div>
          <div class="small text-secondary">Start a live monitoring session to generate data.</div>
        </div>
      </div>
    </div>
  </section>

  <!-- How It Works pipeline -->
  <section class="glass-panel rounded-4 p-4">
    <div class="section-title mb-1">How The Pipeline Works</div>
    <div class="small text-secondary mb-4">
      From camera capture to scored session report — the CV processing pipeline in 4 stages.
    </div>
    <div class="row g-3">
      <div class="col-md-6 col-xl-3">
        <div class="soft-card rounded-4 p-3 h-100">
          <div class="trigger-icon mb-2">1️⃣</div>
          <div class="fw-bold mb-1">Video Capture</div>
          <div class="small text-secondary">Browser webcam records a short clip and uploads it as a WebM file to the FastAPI backend.</div>
        </div>
      </div>
      <div class="col-md-6 col-xl-3">
        <div class="soft-card rounded-4 p-3 h-100">
          <div class="trigger-icon mb-2">2️⃣</div>
          <div class="fw-bold mb-1">Object Detection</div>
          <div class="small text-secondary">YOLOv8n scans each frame for persons, cell phones, and other objects with bounding box tracking.</div>
        </div>
      </div>
      <div class="col-md-6 col-xl-3">
        <div class="soft-card rounded-4 p-3 h-100">
          <div class="trigger-icon mb-2">3️⃣</div>
          <div class="fw-bold mb-1">Face Analysis</div>
          <div class="small text-secondary">MediaPipe Face Landmarker extracts 478 landmarks to compute EAR, MAR, and head pose angles per frame.</div>
        </div>
      </div>
      <div class="col-md-6 col-xl-3">
        <div class="soft-card rounded-4 p-3 h-100">
          <div class="trigger-icon mb-2">4️⃣</div>
          <div class="fw-bold mb-1">Risk Scoring</div>
          <div class="small text-secondary">Event Engine aggregates detections into incidents. Risk Engine applies weighted penalties to produce a final score.</div>
        </div>
      </div>
    </div>
  </section>
</template>
