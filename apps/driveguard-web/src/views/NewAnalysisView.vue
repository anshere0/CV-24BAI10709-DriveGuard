<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { RouterLink, useRouter } from "vue-router";

import FlowTimeline from "../components/FlowTimeline.vue";
import SourceBadge from "../components/SourceBadge.vue";
import { useAnalysisSubmission } from "../composables/useAnalysisSubmission";
import { buildJobTimeline } from "../utils/analysisPresentation";

const router = useRouter();
const selectedFile = ref<File | null>(null);

const {
  loading,
  canceling,
  error,
  uploadedVideo,
  createdJob,
  uploadProgress,
  processingProgress,
  uploadStageLabel,
  processingStageLabel,
  progressMode,
  uploadProgressStyle,
  processingProgressStyle,
  isJobTerminal,
  canCancelJob,
  processingDetails,
  completedSessionId,
  resetState,
  submitFile,
  cancelCurrentJob,
} = useAnalysisSubmission({
  sourceOrigin: "web_upload",
});

const fileLabel = computed(() => selectedFile.value?.name ?? "No file selected");
const selectedFileSizeLabel = computed(() => {
  if (!selectedFile.value) {
    return "Select a cabin video to begin.";
  }
  return `${(selectedFile.value.size / (1024 * 1024)).toFixed(1)} MB`;
});

watch(
  () => selectedFile.value,
  (file) => {
    if (!file) {
      resetState("Waiting for video selection.");
      return;
    }
    resetState(file.name);
  },
);

function onFileChange(event: Event): void {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] ?? null;
}

async function submitAnalysis(): Promise<void> {
  if (!selectedFile.value) {
    return;
  }
  await submitFile(selectedFile.value, {
    idle: "Waiting for video selection.",
    uploading: "Uploading video to backend storage.",
  });
}

function openFirstSession(): void {
  if (!completedSessionId.value) {
    return;
  }
  void router.push({ name: "session-detail", params: { sessionId: completedSessionId.value } });
}
</script>

<template>
  <section class="hero-panel rounded-4 p-4 p-lg-5 mb-4">
    <div class="row g-4 align-items-center">
      <div class="col-lg-8">
        <span class="stage-badge mb-3">Desktop Upload</span>
        <h1 class="display-6 fw-bold mb-3">Create a backend analysis job</h1>
        <p class="lead mb-0">
          Upload a vehicle cabin clip from your machine, then track transfer, queueing, processing, and
          the final session review from one guided flow.
        </p>
      </div>
      <div class="col-lg-4">
        <div class="soft-card rounded-4 p-3">
          <div class="section-title mb-2">Why use this path</div>
          <div class="small text-secondary">
            Best for a controlled MVP demo when you already have a convincing prerecorded scenario.
          </div>
        </div>
      </div>
    </div>
  </section>

  <div v-if="error" class="alert alert-danger">{{ error }}</div>

  <section class="row g-4">
    <div class="col-lg-7">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Upload workflow</div>
        <div class="soft-card rounded-4 p-3 mb-4">
          <label class="form-label fw-semibold" for="analysis-file">Video file</label>
          <input id="analysis-file" class="form-control mb-3" type="file" accept="video/*" @change="onFileChange" />
          <div class="d-flex justify-content-between align-items-center gap-3 flex-wrap">
            <div>
              <div class="fw-semibold">{{ fileLabel }}</div>
              <div class="small text-secondary">{{ selectedFileSizeLabel }}</div>
            </div>
            <SourceBadge source-origin="web_upload" />
          </div>
        </div>

        <div class="soft-card rounded-4 p-3 mb-4">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <div class="fw-semibold">Pipeline timeline</div>
            <div class="small text-secondary text-capitalize">{{ progressMode }}</div>
          </div>
          <FlowTimeline :steps="buildJobTimeline(createdJob)" />
        </div>

        <div class="progress-stack mb-4">
          <div class="soft-card rounded-4 p-3">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <div class="fw-semibold">Upload progress</div>
              <div class="small text-secondary">{{ uploadProgress }}%</div>
            </div>
            <div class="progress progress-shell">
              <div class="progress-bar upload-progress-bar" role="progressbar" :style="uploadProgressStyle" />
            </div>
            <div class="small text-secondary mt-2">{{ uploadStageLabel }}</div>
          </div>

          <div class="soft-card rounded-4 p-3">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <div class="fw-semibold">Processing progress</div>
              <div class="small text-secondary">{{ processingProgress }}%</div>
            </div>
            <div class="progress progress-shell">
              <div class="progress-bar processing-progress-bar" role="progressbar" :style="processingProgressStyle" />
            </div>
            <div class="small text-secondary mt-2">{{ processingStageLabel }}</div>
            <div class="small text-secondary mt-1">{{ processingDetails }}</div>
          </div>
        </div>

        <div class="d-flex flex-wrap gap-3">
          <button class="btn btn-dark btn-lg" type="button" :disabled="loading || !selectedFile" @click="submitAnalysis">
            {{ loading ? "Submitting..." : "Upload and create job" }}
          </button>
          <button
            class="btn btn-outline-danger btn-lg"
            type="button"
            :disabled="!canCancelJob || canceling"
            @click="cancelCurrentJob"
          >
            {{ canceling ? "Stopping..." : "Stop processing" }}
          </button>
          <RouterLink class="btn btn-outline-dark btn-lg" :to="{ name: 'live-demo' }">Switch to live demo</RouterLink>
        </div>
      </div>
    </div>

    <div class="col-lg-5">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Result surface</div>

        <div v-if="!uploadedVideo && !createdJob && progressMode === 'idle'" class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">No upload sent yet</div>
          <div class="small text-secondary">Choose a video file to activate the desktop upload path.</div>
        </div>

        <div v-if="uploadedVideo" class="soft-card rounded-4 p-3 mb-3">
          <div class="d-flex justify-content-between align-items-start gap-3">
            <div>
              <div class="fw-semibold">Uploaded video</div>
              <div class="small text-secondary mt-1">{{ uploadedVideo.original_filename }}</div>
            </div>
            <SourceBadge :source-origin="uploadedVideo.source_origin" />
          </div>
          <div class="small text-secondary mt-2 text-break">{{ uploadedVideo.stored_path }}</div>
        </div>

        <div v-if="createdJob" class="soft-card rounded-4 p-3">
          <div class="d-flex justify-content-between align-items-start gap-3">
            <div>
              <div class="fw-semibold">Created job</div>
              <div class="small text-secondary mt-1">{{ createdJob.id }}</div>
            </div>
            <span
              class="badge rounded-pill"
              :class="{
                'text-bg-success': createdJob.status === 'completed',
                'text-bg-danger': createdJob.status === 'failed',
                'text-bg-secondary': createdJob.status === 'canceled',
                'text-bg-warning': !['completed', 'failed', 'canceled'].includes(createdJob.status),
              }"
            >
              {{ createdJob.status }}
            </span>
          </div>

          <div class="small text-secondary mt-3">
            {{ createdJob.total_incidents }} incidents • {{ createdJob.total_sources }} source(s)
          </div>
          <div class="small text-secondary mt-2">
            {{ createdJob.progress_phase }} • {{ createdJob.progress_percent.toFixed(1) }}%
          </div>
          <div class="small text-secondary mt-2">{{ processingDetails }}</div>

          <div class="d-flex flex-wrap gap-2 mt-3">
            <RouterLink class="btn btn-outline-dark btn-sm" :to="{ name: 'jobs' }">View jobs</RouterLink>
            <button
              class="btn btn-dark btn-sm"
              type="button"
              :disabled="!completedSessionId || !isJobTerminal"
              @click="openFirstSession"
            >
              Open session
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
