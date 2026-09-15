<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

import FlowTimeline from "../components/FlowTimeline.vue";
import SourceBadge from "../components/SourceBadge.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { fetchJobs } from "../services/api";
import type { AnalysisJobDto } from "../types/backend";
import { buildJobTimeline } from "../utils/analysisPresentation";

const jobs = ref<AnalysisJobDto[]>([]);
const loading = ref<boolean>(true);
const error = ref<string>("");
const statusFilter = ref<string>("");
const sourceFilter = ref<string>("");
const query = ref<string>("");

const filteredJobs = computed(() => {
  return jobs.value.filter((job) => {
    const matchesStatus = !statusFilter.value || job.status === statusFilter.value;
    const matchesSource = !sourceFilter.value || job.source_origin === sourceFilter.value;
    const haystack = [
      job.id,
      job.source_paths[0] ?? "",
      job.progress_phase,
      job.progress_message ?? "",
    ]
      .join(" ")
      .toLowerCase();
    const matchesQuery = !query.value || haystack.includes(query.value.toLowerCase());
    return matchesStatus && matchesSource && matchesQuery;
  });
});

const headlineJob = computed(() => filteredJobs.value[0] ?? null);

async function loadJobs(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const response = await fetchJobs();
    jobs.value = response.items;
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load jobs.";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadJobs();
});
</script>

<template>
  <section class="hero-panel rounded-4 p-4 p-lg-5 mb-4">
    <div class="row g-4 align-items-center">
      <div class="col-lg-8">
        <span class="stage-badge mb-3">Jobs</span>
        <h1 class="display-6 fw-bold mb-3">Persisted analysis queue</h1>
        <p class="lead mb-0">
          Track queued, processing, completed, failed, or canceled work from one readable operational screen,
          including Android edge uploads alongside web demo flows.
        </p>
      </div>
      <div class="col-lg-4">
        <div class="soft-card rounded-4 p-3">
          <div class="section-title mb-2">Quick path</div>
          <div class="small text-secondary mb-3">
            Start from desktop upload, browser live demo, or Android edge capture, then return here to review the queue.
          </div>
          <div class="d-flex flex-wrap gap-2">
            <RouterLink class="btn btn-dark btn-sm" :to="{ name: 'new-analysis' }">Desktop upload</RouterLink>
            <RouterLink class="btn btn-outline-dark btn-sm" :to="{ name: 'live-demo' }">Live demo</RouterLink>
          </div>
        </div>
      </div>
    </div>
  </section>

  <div v-if="error" class="alert alert-danger">{{ error }}</div>

  <section class="glass-panel rounded-4 p-4 mb-4">
    <div class="row g-3 align-items-end">
      <div class="col-md-4">
        <label class="form-label fw-semibold">Search</label>
        <input v-model="query" class="form-control" type="text" placeholder="Job id, phase, path…" />
      </div>
      <div class="col-md-3">
        <label class="form-label fw-semibold">Status</label>
        <select v-model="statusFilter" class="form-select">
          <option value="">All statuses</option>
          <option value="queued">queued</option>
          <option value="processing">processing</option>
          <option value="completed">completed</option>
          <option value="failed">failed</option>
          <option value="canceled">canceled</option>
        </select>
      </div>
      <div class="col-md-3">
        <label class="form-label fw-semibold">Source</label>
        <select v-model="sourceFilter" class="form-select">
          <option value="">All sources</option>
          <option value="web_upload">web_upload</option>
          <option value="web_live">web_live</option>
          <option value="android_upload">android_upload</option>
        </select>
      </div>
      <div class="col-md-2">
        <button class="btn btn-dark w-100" type="button" @click="loadJobs">Refresh</button>
      </div>
    </div>
  </section>

  <section class="row g-4 mb-4">
    <div class="col-lg-5">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Selected queue headline</div>
        <div v-if="loading" class="text-secondary">Loading jobs…</div>
        <div v-else-if="headlineJob" class="d-grid gap-3">
          <div class="soft-card rounded-4 p-3">
            <div class="d-flex justify-content-between align-items-start gap-3 mb-3">
              <div>
                <div class="fw-semibold">{{ headlineJob.id }}</div>
                <div class="small text-secondary mt-1">
                  {{ headlineJob.progress_message ?? headlineJob.progress_phase }}
                </div>
              </div>
              <StatusBadge :status="headlineJob.status" />
            </div>
            <div class="d-flex flex-wrap gap-2 mb-3">
              <SourceBadge :source-origin="headlineJob.source_origin" />
              <span class="score-pill score-pill-sm">{{ headlineJob.average_score.toFixed(0) || "0" }}</span>
            </div>
            <FlowTimeline :steps="buildJobTimeline(headlineJob)" />
          </div>
          <div class="small text-secondary">
            {{ headlineJob.total_incidents }} incidents • {{ headlineJob.total_sources }} source(s) •
            {{ headlineJob.progress_percent.toFixed(0) }}% complete
          </div>
          <RouterLink
            v-if="headlineJob.sessions.length > 0"
            class="btn btn-dark"
            :to="{ name: 'session-detail', params: { sessionId: headlineJob.sessions[0].id } }"
          >
            Open resulting session
          </RouterLink>
        </div>
        <div v-else class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">No matching jobs</div>
          <div class="small text-secondary">Adjust the filters or create a new analysis flow.</div>
        </div>
      </div>
    </div>

    <div class="col-lg-7">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="d-flex justify-content-between align-items-center gap-3 mb-3">
          <div>
            <div class="section-title">Job queue</div>
            <div class="small text-secondary">Readable view of source, status, progress, and next action.</div>
          </div>
          <div class="small text-secondary">{{ filteredJobs.length }} job(s) shown</div>
        </div>

        <div v-if="loading" class="text-secondary">Loading jobs…</div>
        <div v-else-if="filteredJobs.length === 0" class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">Nothing to review yet</div>
          <div class="small text-secondary">Run a browser demo or upload a file to populate this queue.</div>
        </div>
        <div v-else class="table-responsive">
          <table class="table align-middle mb-0">
            <thead>
              <tr>
                <th>Job</th>
                <th>Status</th>
                <th>Source</th>
                <th>Progress</th>
                <th>Score</th>
                <th class="text-end">Action</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in filteredJobs" :key="job.id">
                <td class="fw-semibold">
                  <div>{{ job.id }}</div>
                  <div class="small text-secondary">{{ job.progress_phase }}</div>
                </td>
                <td><StatusBadge :status="job.status" /></td>
                <td><SourceBadge :source-origin="job.source_origin" /></td>
                <td>
                  <div class="small fw-semibold">{{ job.progress_percent.toFixed(0) }}%</div>
                  <div class="small text-secondary">{{ job.total_incidents }} incident(s)</div>
                </td>
                <td>{{ job.average_score ? job.average_score.toFixed(1) : "—" }}</td>
                <td class="text-end">
                  <RouterLink
                    v-if="job.sessions.length > 0"
                    class="btn btn-outline-dark btn-sm"
                    :to="{ name: 'session-detail', params: { sessionId: job.sessions[0].id } }"
                  >
                    Open session
                  </RouterLink>
                  <span v-else class="small text-secondary">No session yet</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </section>
</template>
