import type { AnalysisJobDto, IncidentDto } from "../types/backend";

export interface SourceOriginMeta {
  label: string;
  detail: string;
  tone: "upload" | "live" | "mobile";
}

export interface ScoreMeta {
  label: string;
  tone: "excellent" | "attention" | "critical";
  helper: string;
}

export interface TimelineStep {
  key: string;
  label: string;
  state: "pending" | "active" | "complete" | "error";
}

export function getSourceOriginMeta(sourceOrigin: string): SourceOriginMeta {
  if (sourceOrigin === "web_live") {
    return {
      label: "Web live",
      detail: "Recorded from the browser camera demo.",
      tone: "live",
    };
  }
  if (sourceOrigin === "android_upload") {
    return {
      label: "Android edge",
      detail: "Captured from the Android edge client with local suspicion pre-triage.",
      tone: "mobile",
    };
  }
  return {
    label: "Web upload",
    detail: "Uploaded from the desktop browser.",
    tone: "upload",
  };
}

export function getScoreMeta(score: number): ScoreMeta {
  if (score >= 85) {
    return {
      label: "Controlled",
      tone: "excellent",
      helper: "Driver behavior remained largely within safe thresholds.",
    };
  }
  if (score >= 65) {
    return {
      label: "Needs review",
      tone: "attention",
      helper: "Some risky behavior should be reviewed in context.",
    };
  }
  return {
    label: "High risk",
    tone: "critical",
    helper: "Multiple risky behaviors materially affected the session score.",
  };
}

export function getDominantIncident(incidents: IncidentDto[]): IncidentDto | null {
  if (incidents.length === 0) {
    return null;
  }
  return [...incidents].sort((left, right) => {
    if (right.max_severity !== left.max_severity) {
      return right.max_severity - left.max_severity;
    }
    return right.occurrences - left.occurrences;
  })[0];
}

export function getTopIncidents(incidents: IncidentDto[], limit = 3): IncidentDto[] {
  return [...incidents]
    .sort((left, right) => {
      if (right.max_severity !== left.max_severity) {
        return right.max_severity - left.max_severity;
      }
      return right.occurrences - left.occurrences;
    })
    .slice(0, limit);
}

export function formatIncidentWindow(incident: IncidentDto): string {
  return `${incident.started_at_seconds.toFixed(1)}s - ${incident.ended_at_seconds.toFixed(1)}s`;
}

export function buildJobTimeline(job: AnalysisJobDto | null): TimelineStep[] {
  const status = job?.status ?? "idle";
  const phase = job?.progress_phase ?? "queued";
  const isFailed = status === "failed";
  const isCanceled = status === "canceled";
  const isCompleted = status === "completed";
  const isWorking = status === "queued" || status === "processing";

  return [
    {
      key: "upload",
      label: "Upload",
      state: isCompleted || isFailed || isCanceled || isWorking ? "complete" : "pending",
    },
    {
      key: "queued",
      label: "Queued",
      state:
        phase === "queued"
          ? "active"
          : isCompleted || isFailed || isCanceled || phase !== "queued"
            ? "complete"
            : "pending",
    },
    {
      key: "processing",
      label: "Processing",
      state:
        phase === "processing"
          ? "active"
          : isCompleted
            ? "complete"
            : isFailed || isCanceled
              ? "error"
              : "pending",
    },
    {
      key: "completed",
      label: isCanceled ? "Canceled" : isFailed ? "Failed" : "Completed",
      state:
        isCompleted
          ? "complete"
          : isFailed || isCanceled
            ? "error"
            : status === "processing"
              ? "pending"
              : "pending",
    },
  ];
}
