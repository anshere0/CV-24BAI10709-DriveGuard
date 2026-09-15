<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";

import busCockpitDemoUrl from "../assets/bus-cockpit-demo.svg";
import FlowTimeline from "../components/FlowTimeline.vue";
import SourceBadge from "../components/SourceBadge.vue";
import { useAnalysisSubmission } from "../composables/useAnalysisSubmission";
import { buildJobTimeline } from "../utils/analysisPresentation";

type DemoSceneMode = "standard" | "bus_cockpit";
type SeatbeltMode = "off" | "on";

const router = useRouter();

const livePreview = ref<HTMLVideoElement | null>(null);
const browserError = ref<string>("");
const cameraReady = ref<boolean>(false);
const recording = ref<boolean>(false);
const preparingCamera = ref<boolean>(false);
const recordedPreviewUrl = ref<string>("");
const recordingDuration = ref<number>(12);
const elapsedSeconds = ref<number>(0);
const sceneMode = ref<DemoSceneMode>("bus_cockpit");
const seatbeltMode = ref<SeatbeltMode>("off");

let mediaStream: MediaStream | null = null;
let mediaRecorder: MediaRecorder | null = null;
let recordTimer: number | null = null;
let autoStopTimer: number | null = null;
let recordingStartedAt = 0;
let recordedChunks: Blob[] = [];

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
  canCancelJob,
  processingDetails,
  completedSessionId,
  submitFile,
} = useAnalysisSubmission({
  sourceOrigin: "web_live",
  onCompletedSession(sessionId) {
    void router.push({ name: "session-detail", params: { sessionId } });
  },
});

const mediaSupported = computed<boolean>(() => {
  return typeof navigator !== "undefined"
    && Boolean(navigator.mediaDevices?.getUserMedia)
    && typeof MediaRecorder !== "undefined";
});
const canStartRecording = computed<boolean>(() => mediaSupported.value && !recording.value && !loading.value);
const canStopRecording = computed<boolean>(() => recording.value);
const cockpitSceneActive = computed<boolean>(() => sceneMode.value === "bus_cockpit");
const recordingProgress = computed<number>(() => {
  if (!recording.value || recordingDuration.value <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((elapsedSeconds.value / recordingDuration.value) * 100));
});
const recordingProgressStyle = computed(() => ({ width: `${recordingProgress.value}%` }));
const livePreviewShellClass = computed(() => ({
  "live-preview-shell-cockpit": cockpitSceneActive.value,
}));
const livePreviewShellStyle = computed<Record<string, string>>(() => {
  const styles: Record<string, string> = {};
  if (cockpitSceneActive.value) {
    styles["--cockpit-demo-bg"] = `url("${busCockpitDemoUrl}")`;
  }
  return styles;
});
const liveStatusLabel = computed<string>(() => {
  if (!mediaSupported.value) {
    return "This browser does not support the required camera APIs.";
  }
  if (recording.value) {
    return `Recording in progress: ${elapsedSeconds.value.toFixed(1)}s / ${recordingDuration.value}s`;
  }
  if (cameraReady.value) {
    return cockpitSceneActive.value ? "Cockpit demo feed armed and ready." : "Camera armed and ready.";
  }
  return "Camera not armed yet.";
});
const captureStateLabel = computed<string>(() => {
  if (preparingCamera.value) {
    return "Preparing camera access...";
  }
  if (recording.value) {
    return "Live browser recording in progress.";
  }
  if (recordedPreviewUrl.value) {
    return "Latest browser capture is ready and already submitted.";
  }
  return "No clip captured yet.";
});
const immersiveModeSummary = computed<string>(() => {
  if (!cockpitSceneActive.value) {
    return "Standard browser camera preview with the production upload workflow.";
  }
  return "Immersive cockpit staging with a transport HUD and a virtual seatbelt overlay for demos.";
});
const cameraStateBadge = computed<string>(() => {
  if (preparingCamera.value) {
    return "Arming";
  }
  if (recording.value) {
    return "Recording";
  }
  if (cameraReady.value) {
    return "Ready";
  }
  return "Offline";
});
const attentionStateLabel = computed<string>(() => {
  if (recording.value) {
    return "Monitoring attentiveness";
  }
  if (cameraReady.value) {
    return "Awaiting capture";
  }
  return "Standby";
});
const seatbeltBadgeClass = computed<string>(() => (
  seatbeltMode.value === "on" ? "hud-chip-seatbelt-on" : "hud-chip-seatbelt-off"
));
const seatbeltText = computed<string>(() => (
  seatbeltMode.value === "on" ? "Seatbelt engaged" : "Seatbelt missing"
));
const virtualSeatbeltClass = computed(() => ({
  "virtual-seatbelt-on": seatbeltMode.value === "on",
  "virtual-seatbelt-off": seatbeltMode.value === "off",
}));
const recordingHint = computed<string>(() => {
  if (!cockpitSceneActive.value) {
    return "The clip will stop automatically after the selected duration and upload itself.";
  }
  return "The cockpit scene, HUD, and seatbelt overlay stay visible during recording while the real clip is captured for backend analysis.";
});

async function toggleRecording(): Promise<void> {
  if (recording.value) {
    stopRecording();
    return;
  }
  await startRecording();
}

async function startRecording(): Promise<void> {
  browserError.value = "";
  if (!mediaSupported.value) {
    browserError.value = "Camera recording is not supported in this browser.";
    return;
  }

  try {
    await ensureCameraReady();
    if (mediaStream === null) {
      browserError.value = "Unable to access the camera stream.";
      return;
    }

    cleanupRecordedPreview();
    recordedChunks = [];
    const mimeType = resolveMimeType();
    mediaRecorder = mimeType ? new MediaRecorder(mediaStream, { mimeType }) : new MediaRecorder(mediaStream);
    mediaRecorder.ondataavailable = (event: BlobEvent) => {
      if (event.data.size > 0) {
        recordedChunks.push(event.data);
      }
    };
    mediaRecorder.onstop = () => {
      const recordedBlob = new Blob(recordedChunks, { type: mimeType || "video/webm" });
      recordedChunks = [];
      if (recordedBlob.size === 0) {
        browserError.value = "The recording finished but produced an empty clip.";
        return;
      }
      recordedPreviewUrl.value = URL.createObjectURL(recordedBlob);
      const file = new File([recordedBlob], buildRecordingName(), {
        type: recordedBlob.type || "video/webm",
      });
      void submitFile(file, {
        idle: "Waiting for live camera recording.",
        uploading: "Uploading recorded live demo clip.",
      });
    };

    recording.value = true;
    elapsedSeconds.value = 0;
    recordingStartedAt = performance.now();
    mediaRecorder.start();

    recordTimer = window.setInterval(() => {
      elapsedSeconds.value = Number(((performance.now() - recordingStartedAt) / 1000).toFixed(1));
    }, 200);
    autoStopTimer = window.setTimeout(() => {
      stopRecording();
    }, recordingDuration.value * 1000);
  } catch (err) {
    browserError.value = err instanceof Error ? err.message : "Failed to start browser recording.";
  }
}

function stopRecording(): void {
  if (!recording.value) {
    return;
  }

  recording.value = false;
  clearRecordingTimers();

  if (mediaRecorder !== null && mediaRecorder.state !== "inactive") {
    mediaRecorder.stop();
  }
}

async function ensureCameraReady(): Promise<void> {
  if (mediaStream !== null) {
    attachStream(mediaStream);
    cameraReady.value = true;
    return;
  }

  preparingCamera.value = true;
  browserError.value = "";
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: "user",
        width: { ideal: 1280 },
        height: { ideal: 720 },
      },
      audio: false,
    });
    attachStream(mediaStream);
    cameraReady.value = true;
  } catch (err) {
    browserError.value = err instanceof Error ? err.message : "Camera permission was denied.";
    cameraReady.value = false;
  } finally {
    preparingCamera.value = false;
  }
}

function attachStream(stream: MediaStream): void {
  if (livePreview.value === null) {
    return;
  }
  livePreview.value.srcObject = stream;
  void livePreview.value.play();
}

function releaseCamera(): void {
  if (mediaStream !== null) {
    for (const track of mediaStream.getTracks()) {
      track.stop();
    }
  }
  mediaStream = null;
  cameraReady.value = false;
  if (livePreview.value !== null) {
    livePreview.value.srcObject = null;
  }
}

function cleanupRecordedPreview(): void {
  if (recordedPreviewUrl.value) {
    URL.revokeObjectURL(recordedPreviewUrl.value);
    recordedPreviewUrl.value = "";
  }
}

function clearRecordingTimers(): void {
  if (recordTimer !== null) {
    window.clearInterval(recordTimer);
    recordTimer = null;
  }
  if (autoStopTimer !== null) {
    window.clearTimeout(autoStopTimer);
    autoStopTimer = null;
  }
}

function buildRecordingName(): string {
  const stamp = new Date().toISOString().replace(/:/g, "-");
  return `driveguard-live-demo-${stamp}.webm`;
}

function resolveMimeType(): string {
  const candidates = [
    "video/webm;codecs=vp9,opus",
    "video/webm;codecs=vp8,opus",
    "video/webm",
  ];

  for (const candidate of candidates) {
    if (MediaRecorder.isTypeSupported(candidate)) {
      return candidate;
    }
  }
  return "";
}

function openCompletedSession(): void {
  if (!completedSessionId.value) {
    return;
  }
  void router.push({ name: "session-detail", params: { sessionId: completedSessionId.value } });
}

onBeforeUnmount(() => {
  stopRecording();
  clearRecordingTimers();
  cleanupRecordedPreview();
  releaseCamera();
});
</script>

<template>
  <section class="hero-panel rounded-4 p-4 p-lg-5 mb-4">
    <div class="row g-4 align-items-center">
      <div class="col-lg-8">
        <span class="stage-badge mb-3">Live Demo</span>
        <h1 class="display-6 fw-bold mb-3">Run a browser camera demonstration</h1>
        <p class="lead mb-0">
          Record a short clip directly from your PC camera, upload it automatically, and let the standard
          backend workflow produce a scored session you can inspect immediately.
        </p>
      </div>
      <div class="col-lg-4">
        <div class="soft-card rounded-4 p-3">
          <div class="section-title mb-2">Best use</div>
          <div class="small text-secondary">
            This is the strongest "show it live" path in the MVP: capture, upload, process, then jump straight
            into the finished session.
          </div>
        </div>
      </div>
    </div>
  </section>

  <div v-if="browserError" class="alert alert-danger">{{ browserError }}</div>
  <div v-if="error" class="alert alert-danger">{{ error }}</div>

  <section class="row g-4 mb-4">
    <div class="col-xl-8">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-3">
          <div>
            <div class="section-title">Camera bay</div>
            <div class="small text-secondary">{{ liveStatusLabel }}</div>
          </div>
          <div class="d-flex flex-wrap gap-2">
            <select v-model="sceneMode" class="form-select live-duration-select" :disabled="recording || loading">
              <option value="bus_cockpit">Bus Cockpit Demo</option>
              <option value="standard">Standard Camera</option>
            </select>
            <select v-model="recordingDuration" class="form-select live-duration-select" :disabled="recording || loading">
              <option :value="8">8 seconds</option>
              <option :value="12">12 seconds</option>
              <option :value="15">15 seconds</option>
            </select>
            <button class="btn btn-outline-dark" type="button" :disabled="preparingCamera || cameraReady" @click="ensureCameraReady">
              {{ preparingCamera ? "Arming..." : "Enable camera" }}
            </button>
            <button class="btn btn-dark" type="button" :disabled="!canStartRecording && !canStopRecording" @click="toggleRecording">
              {{ recording ? "Stop recording" : "Start live demo" }}
            </button>
            <button class="btn btn-outline-dark" type="button" :disabled="recording" @click="releaseCamera">Release camera</button>
          </div>
        </div>

        <div class="soft-card rounded-4 p-3 mb-3">
          <div class="d-flex flex-wrap justify-content-between align-items-center gap-3">
            <div>
              <div class="fw-semibold">Demo staging</div>
              <div class="small text-secondary">{{ immersiveModeSummary }}</div>
            </div>
            <div class="d-flex flex-wrap gap-2">
              <button
                class="btn btn-sm"
                :class="seatbeltMode === 'on' ? 'btn-dark' : 'btn-outline-dark'"
                type="button"
                :disabled="!cockpitSceneActive"
                @click="seatbeltMode = 'on'"
              >
                Seatbelt ON
              </button>
              <button
                class="btn btn-sm"
                :class="seatbeltMode === 'off' ? 'btn-dark' : 'btn-outline-dark'"
                type="button"
                :disabled="!cockpitSceneActive"
                @click="seatbeltMode = 'off'"
              >
                Seatbelt OFF
              </button>
            </div>
          </div>
        </div>

        <div class="live-preview-shell rounded-4 overflow-hidden mb-3" :class="livePreviewShellClass" :style="livePreviewShellStyle">
          <video ref="livePreview" class="live-preview-video" autoplay muted playsinline />
          <div v-if="cockpitSceneActive" class="live-preview-cockpit-frame" aria-hidden="true">
            <div class="cockpit-windshield-glow" />
            <div class="cockpit-dashboard-halo" />
          </div>
          <div v-if="cockpitSceneActive" class="live-preview-seatbelt" :class="virtualSeatbeltClass" aria-hidden="true" />
          <div class="live-preview-overlay">
            <div class="live-overlay-grid">
              <div class="cockpit-hud-card rounded-4">
                <div class="section-title text-light-emphasis mb-2">Driver status</div>
                <div class="d-flex flex-wrap gap-2">
                  <span class="hud-chip">{{ cameraStateBadge }}</span>
                  <span class="hud-chip" :class="seatbeltBadgeClass">{{ seatbeltText }}</span>
                  <span class="hud-chip">{{ attentionStateLabel }}</span>
                </div>
              </div>
              <div class="live-overlay-actions">
                <span class="live-indicator" :class="{ active: recording }">{{ recording ? "REC" : "READY" }}</span>
                <div v-if="cockpitSceneActive" class="cockpit-mode-badge">Bus cockpit scene</div>
              </div>
            </div>
          </div>
        </div>

        <div class="row g-3">
          <div class="col-md-7">
            <div class="soft-card rounded-4 p-3 h-100">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <div class="fw-semibold">Recording progress</div>
                <div class="small text-secondary">{{ recording ? `${elapsedSeconds.toFixed(1)}s` : "idle" }}</div>
              </div>
              <div class="progress progress-shell">
                <div class="progress-bar upload-progress-bar" role="progressbar" :style="recordingProgressStyle" />
              </div>
              <div class="small text-secondary mt-2">
                {{ recordingHint }}
              </div>
            </div>
          </div>
          <div class="col-md-5">
            <div class="soft-card rounded-4 p-3 h-100">
              <div class="fw-semibold mb-2">Capture state</div>
              <SourceBadge source-origin="web_live" />
              <div class="small text-secondary mt-2">{{ captureStateLabel }}</div>
              <div v-if="cockpitSceneActive" class="small text-secondary mt-2">
                The visual cockpit and seatbelt overlays are demo staging layers; the recorded clip still follows the
                same backend analysis pipeline as before.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="col-xl-4">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Recorded clip</div>
        <div v-if="recordedPreviewUrl" class="d-grid gap-3">
          <video class="recorded-preview-video rounded-4" :src="recordedPreviewUrl" controls playsinline />
          <div class="small text-secondary">
            Latest browser capture is ready and has been submitted to the backend workflow automatically.
          </div>
        </div>
        <div v-else class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">No clip captured yet</div>
          <div class="small text-secondary">Enable the camera and record a short live demo.</div>
        </div>
      </div>
    </div>
  </section>

  <section class="row g-4">
    <div class="col-lg-7">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Analysis progress</div>

        <div class="soft-card rounded-4 p-3 mb-4">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <div class="fw-semibold">Pipeline timeline</div>
            <div class="small text-secondary text-capitalize">{{ progressMode }}</div>
          </div>
          <FlowTimeline :steps="buildJobTimeline(createdJob)" />
        </div>

        <div class="progress-stack">
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

        <div class="d-flex flex-wrap gap-3 mt-4">

          <RouterLink class="btn btn-outline-dark btn-lg" :to="{ name: 'history' }">Open session history</RouterLink>
        </div>
      </div>
    </div>

    <div class="col-lg-5">
      <div class="glass-panel rounded-4 p-4 h-100">
        <div class="section-title mb-3">Backend result</div>

        <div v-if="uploadedVideo" class="soft-card rounded-4 p-3 mb-3">
          <div class="d-flex justify-content-between align-items-start gap-3">
            <div>
              <div class="fw-semibold">Uploaded clip</div>
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
            <RouterLink class="btn btn-outline-dark btn-sm" :to="{ name: 'history' }">View history</RouterLink>
            <button class="btn btn-dark btn-sm" type="button" :disabled="!completedSessionId" @click="openCompletedSession">
              Open session
            </button>
          </div>
        </div>

        <div v-if="!uploadedVideo && !createdJob && progressMode === 'idle'" class="empty-panel rounded-4 p-4">
          <div class="fw-semibold mb-2">Waiting for browser capture</div>
          <div class="small text-secondary">The live demo will create a normal backend job after a short recording.</div>
        </div>
      </div>
    </div>
  </section>
</template>
