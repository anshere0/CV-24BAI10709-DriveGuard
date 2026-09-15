<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

import MetricCard from "../components/MetricCard.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { fetchHealth, fetchJobs, fetchSessions } from "../services/api";
import type { AnalysisJobDto, AnalysisSessionDto, HealthDto } from "../types/backend";
import { getSourceOriginMeta } from "../utils/analysisPresentation";

const loading = ref<boolean>(true);
const error = ref<string>("");
const health = ref<HealthDto | null>(null);
const jobs = ref<AnalysisJobDto[]>([]);
const sessions = ref<AnalysisSessionDto[]>([]);

const completedJobs = computed(() => jobs.value.filter((job) => job.status === "completed").length);
const inFlightJobs = computed(() =>
  jobs.value.filter((job) => job.status === "queued" || job.status === "processing").length,
);
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
const recentLiveJob = computed(() => jobs.value.find((job) => job.source_origin === "web_live") ?? null);
const healthTone = computed(() => (health.value?.status === "ok" ? "text-bg-success" : "text-bg-danger"));

async function loadDashboard(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const [healthResponse, jobsResponse, sessionsResponse] = await Promise.all([
      fetchHealth(),
      fetchJobs(),
      fetchSessions(),
    ]);
    health.value = healthResponse;
    jobs.value = jobsResponse.items;
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
        <span class="stage-badge mb-3">Demo Control Center</span>
        <h1 class="display-6 fw-bold mb-3">DriveGuard AI demo cockpit</h1>
        <p class="lead mb-4">
          Orchestrate the whole story from one place: upload a clip, run a live browser demo, review the
          backend queue, and open the final risk session in one clean flow.
        </p>
        <div class="d-flex flex-wrap gap-3">
          <RouterLink class="btn btn-dark btn-lg" :to="{ name: 'live-demo' }">Launch live demo</RouterLink>
          <RouterLink class="btn btn-outline-dark btn-lg" :to="{ name: 'new-analysis' }">
            Upload from desktop
          </RouterLink>
          <button class="btn btn-outline-dark btn-lg" type="button" @click="loadDashboard">Refresh data</button>
        </div>
      </div>

      <div class="col-lg-5">
        <div class="glass-panel rounded-4 p-4 demo-spotlight">
          <div class="section-title mb-3">Demo path</div>
          <div class="demo-path-list">
            <div class="demo-path-item">
              <span class="demo-path-index">1</span>
              <div>
                <div class="fw-semibold">Capture or upload</div>
                <div class="small text-secondary">Choose desktop upload or browser camera capture.</div>
              </div>
            </div>
            <div class="demo-path-item">
              <span class="demo-path-index">2</span>
              <div>
                <div class="fw-semibold">Track processing live</div>
                <div class="small text-secondary">Follow progress, ETA, and cancel if needed.</div>
              </div>
            </div>
            <div class="demo-path-item">
              <span class="demo-path-index">3</span>
              <div>
                <div class="fw-semibold">Present the session review</div>
                <div class="small text-secondary">Open one polished session summary with incidents and score.</div>
              </div>
            </div>
          </div>

          <div class="soft-card rounded-4 p-3 mt-4">
            <div class="d-flex justify-content-between align-items-center gap-3">
              <div>
                <div class="fw-semibold">Backend heartbeat</div>
                <div class="small text-secondary">Queue mode: {{ health?.queue_backend ?? "unknown" }}</div>
              </div>
              <span class="badge rounded-pill px-3 py-2" :class="healthTone">
                {{ health?.status ?? (loading ? "checking" : "offline") }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <div v-if="error" class="alert alert-danger">{{ error }}</div>

  <section class="row g-4 mb-4">
    <div class="col-md-6 col-xl-3">
      <MetricCard label="Jobs loaded" :value="jobs.length" helper="Latest backend job records" />
    </div>
    <div class="col-md-6 col-xl-3">
      <MetricCard label="Active jobs" :value="inFlightJobs" helper="Queued or processing right now" />
    </div>
    <div class="col-md-6 col-xl-3">
      <MetricCard label="Completed jobs" :value="completedJobs" helper="Ready for review" />
    </div>
    <div class="col-md-6 col-xl-3">
      <MetricCard label="Average score" :value="averageScore" helper="Across persisted sessions" />
    </div>
  </section>

  <section class="row g-4 mb-4">
    <div class="col-lg-8">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Demo launch pads</div>
        <div class="row g-3">
          <div class="col-md-4">
            <RouterLink class="soft-card rounded-4 p-3 demo-cta d-block h-100" :to="{ name: 'live-demo' }">
              <div class="section-title mb-2">Live browser</div>
              <div class="fw-semibold mb-2">Record from PC camera</div>
              <div class="small text-secondary">Best path for a live wow demo directly in the browser.</div>
            </RouterLink>
          </div>
          <div class="col-md-4">
            <RouterLink class="soft-card rounded-4 p-3 demo-cta d-block h-100" :to="{ name: 'new-analysis' }">
              <div class="section-title mb-2">Desktop upload</div>
              <div class="fw-semibold mb-2">Submit a prepared vehicle clip</div>
              <div class="small text-secondary">Ideal when you want a reliable prerecorded scenario.</div>
            </RouterLink>
          </div>
          <div class="col-md-4">
            <RouterLink class="soft-card rounded-4 p-3 demo-cta d-block h-100" :to="{ name: 'jobs' }">
              <div class="section-title mb-2">Queue review</div>
              <div class="fw-semibold mb-2">Open persisted analysis jobs</div>
              <div class="small text-secondary">Jump straight to processed sessions and explain the pipeline.</div>
            </RouterLink>
          </div>
        </div>
      </div>
    </div>

    <div class="col-lg-4">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Latest reviewed session</div>
        <div v-if="loading" class="text-secondary">Loading latest session…</div>
        <div v-else-if="latestSession" class="d-grid gap-3">
          <div class="soft-card rounded-4 p-3">
            <div class="d-flex justify-content-between align-items-start gap-3">
              <div>
                <div class="fw-semibold">{{ latestSession.source_name }}</div>
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
            :to="{ name: 'session-detail', params: { sessionId: latestSession.id } }"
          >
            Open latest session
          </RouterLink>
        </div>
        <div v-else class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">No session yet</div>
          <div class="small text-secondary">Start with a desktop upload or a browser live demo.</div>
        </div>
      </div>
    </div>
  </section>

  <section class="row g-4">
    <div class="col-lg-7">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="d-flex justify-content-between align-items-center gap-3 mb-3">
          <div>
            <div class="section-title">Recent jobs</div>
            <div class="small text-secondary">Last queue activity across upload and live demo flows.</div>
          </div>
          <RouterLink class="btn btn-outline-dark btn-sm" :to="{ name: 'jobs' }">Open jobs</RouterLink>
        </div>

        <div v-if="loading" class="text-secondary">Loading jobs…</div>
        <div v-else-if="jobs.length === 0" class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">No jobs yet</div>
          <div class="small text-secondary">Create a desktop upload or launch the live demo to seed the queue.</div>
        </div>
        <div v-else class="table-responsive">
          <table class="table align-middle mb-0">
            <thead>
              <tr>
                <th>Job</th>
                <th>Status</th>
                <th>Source</th>
                <th>Progress</th>
                <th>Incidents</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in jobs.slice(0, 6)" :key="job.id">
                <td class="fw-semibold">{{ job.id }}</td>
                <td><StatusBadge :status="job.status" /></td>
                <td>{{ getSourceOriginMeta(job.source_origin).label }}</td>
                <td>{{ job.progress_percent.toFixed(0) }}%</td>
                <td>{{ job.total_incidents }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="col-lg-5">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Live demo pulse</div>
        <div v-if="recentLiveJob" class="d-grid gap-3">
          <div class="soft-card rounded-4 p-3">
            <div class="d-flex justify-content-between align-items-start gap-3">
              <div>
                <div class="fw-semibold">Latest browser demo</div>
                <div class="small text-secondary mt-1">{{ recentLiveJob.id }}</div>
              </div>
              <StatusBadge :status="recentLiveJob.status" />
            </div>
            <div class="small text-secondary mt-3">
              {{ recentLiveJob.progress_phase }} • {{ recentLiveJob.progress_percent.toFixed(1) }}%
            </div>
          </div>
          <RouterLink
            v-if="recentLiveJob.sessions.length > 0"
            class="btn btn-outline-dark"
            :to="{ name: 'session-detail', params: { sessionId: recentLiveJob.sessions[0].id } }"
          >
            Open live demo result
          </RouterLink>
          <RouterLink v-else class="btn btn-dark" :to="{ name: 'live-demo' }">Run a new live demo</RouterLink>
        </div>
        <div v-else class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">No browser live demo yet</div>
          <div class="small text-secondary">Launch the live demo to create a more immersive walkthrough.</div>
        </div>
      </div>
    </div>
  </section>
</template>
