import { computed, onBeforeUnmount, ref } from "vue";

import { createAnalysisJob, fetchJob, uploadVideo } from "../services/api";
import type { AnalysisJobDto, UploadedVideoDto } from "../types/backend";

interface UseAnalysisSubmissionOptions {
  configPath?: string;
  sourceOrigin?: "web_upload" | "web_live";
  onCompletedSession?: (sessionId: string) => void;
}

export function useAnalysisSubmission(options?: UseAnalysisSubmissionOptions) {
  const loading = ref<boolean>(false);
  const canceling = ref<boolean>(false);
  const error = ref<string>("");
  const uploadedVideo = ref<UploadedVideoDto | null>(null);
  const createdJob = ref<AnalysisJobDto | null>(null);
  const uploadProgress = ref<number>(0);
  const processingProgress = ref<number>(0);
  const uploadStageLabel = ref<string>("Waiting for input.");
  const processingStageLabel = ref<string>("No analysis job started yet.");
  const progressMode = ref<"idle" | "uploading" | "creating" | "processing" | "completed" | "failed" | "canceled">(
    "idle",
  );

  let pollHandle: number | null = null;

  const uploadProgressStyle = computed(() => ({ width: `${uploadProgress.value}%` }));
  const processingProgressStyle = computed(() => ({ width: `${processingProgress.value}%` }));
  const isJobTerminal = computed<boolean>(() => {
    const status = createdJob.value?.status;
    return status === "completed" || status === "failed" || status === "canceled";
  });
  const canCancelJob = computed<boolean>(() => {
    if (!createdJob.value) {
      return false;
    }
    return !isJobTerminal.value && !createdJob.value.cancel_requested;
  });
  const processingDetails = computed<string>(() => {
    const job = createdJob.value;
    if (!job) {
      return "Waiting for analysis progress.";
    }

    const frameDetails =
      job.total_frames_estimate > 0 ? `${job.processed_frames}/${job.total_frames_estimate} frames processed` : null;
    const etaDetails =
      job.estimated_remaining_seconds !== null ? `ETA ${formatDuration(job.estimated_remaining_seconds)}` : null;

    return [frameDetails, etaDetails].filter((value): value is string => Boolean(value)).join(" • ")
      || "Waiting for analysis progress.";
  });
  const completedSessionId = computed<string | null>(() => createdJob.value?.sessions[0]?.id ?? null);

  function resetState(idleUploadLabel = "Waiting for input."): void {
    uploadedVideo.value = null;
    createdJob.value = null;
    uploadProgress.value = 0;
    processingProgress.value = 0;
    uploadStageLabel.value = idleUploadLabel;
    processingStageLabel.value = "No analysis job started yet.";
    progressMode.value = "idle";
    error.value = "";
    stopPolling();
  }

  async function submitFile(file: File, labels?: { idle?: string; uploading?: string }): Promise<void> {
    loading.value = true;
    error.value = "";
    uploadedVideo.value = null;
    createdJob.value = null;
    uploadProgress.value = 0;
    processingProgress.value = 0;
    progressMode.value = "uploading";
    uploadStageLabel.value = labels?.uploading ?? "Uploading video to backend storage.";
    processingStageLabel.value = "Waiting for upload completion.";
    stopPolling();

    try {
      const uploaded = await uploadVideo(file, {
        sourceOrigin: options?.sourceOrigin ?? "web_upload",
        onProgress(percent: number): void {
          uploadProgress.value = percent;
          uploadStageLabel.value =
            percent >= 100 ? "Upload complete. Creating analysis job." : `Uploading video: ${percent}%`;
        },
      });
      uploadedVideo.value = uploaded;
      progressMode.value = "creating";
      processingStageLabel.value = "Submitting analysis job to backend.";

      const job = await createAnalysisJob({
        source_type: "video",
        source_origin: options?.sourceOrigin ?? "web_upload",
        source_paths: [],
        uploaded_video_ids: [uploaded.id],
        config_path: options?.configPath ?? "config.yaml",
      });
    createdJob.value = job;
    syncJobProgress(job);

      if (isTerminalStatus(job.status)) {
        progressMode.value = resolveTerminalMode(job.status);
        notifyCompletedSession(job);
      } else {
        progressMode.value = "processing";
        startPolling(job.id);
      }
    } catch (err) {
      progressMode.value = "failed";
      error.value = err instanceof Error ? err.message : "Analysis creation failed.";
      processingStageLabel.value = "Analysis workflow failed.";
    } finally {
      loading.value = false;
    }
  }


  function syncJobProgress(job: AnalysisJobDto): void {
    processingProgress.value = Math.max(0, Math.min(100, job.progress_percent));
    processingStageLabel.value =
      job.progress_message ??
      (job.status === "queued" ? "Job queued for execution." : `Job status: ${job.status}`);
  }

  function stopPolling(): void {
    if (pollHandle !== null) {
      window.clearInterval(pollHandle);
      pollHandle = null;
    }
  }

  function startPolling(jobId: string): void {
    stopPolling();
    pollHandle = window.setInterval(async () => {
      try {
        const job = await fetchJob(jobId);
        createdJob.value = job;
        syncJobProgress(job);
        if (isTerminalStatus(job.status)) {
          progressMode.value = resolveTerminalMode(job.status);
          stopPolling();
          notifyCompletedSession(job);
        }
      } catch (err) {
        error.value = err instanceof Error ? err.message : "Failed to refresh analysis job.";
        progressMode.value = "failed";
        stopPolling();
      }
    }, 1200);
  }

  function notifyCompletedSession(job: AnalysisJobDto): void {
    const sessionId = job.sessions[0]?.id;
    if (!sessionId) {
      return;
    }
    options?.onCompletedSession?.(sessionId);
  }

  onBeforeUnmount(() => {
    stopPolling();
  });

  return {
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
    stopPolling,
  };
}

function isTerminalStatus(status: string): boolean {
  return ["completed", "failed", "canceled"].includes(status);
}

function resolveTerminalMode(status: string): "completed" | "failed" | "canceled" {
  if (status === "completed") {
    return "completed";
  }
  if (status === "canceled") {
    return "canceled";
  }
  return "failed";
}

function formatDuration(totalSeconds: number): string {
  const safeSeconds = Math.max(0, Math.round(totalSeconds));
  const minutes = Math.floor(safeSeconds / 60);
  const seconds = safeSeconds % 60;
  if (minutes <= 0) {
    return `${seconds}s`;
  }
  return `${minutes}m ${seconds.toString().padStart(2, "0")}s`;
}
