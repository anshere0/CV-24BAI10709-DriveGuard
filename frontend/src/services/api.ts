import { getBackendBaseUrl } from "../config/backend";
import type {
  AnalysisJobDto,
  AnalysisJobListDto,
  AnalysisSessionDto,
  AnalysisSessionListDto,
  CreateAnalysisJobRequestDto,
  HealthDto,
  IncidentListDto,
  UploadedVideoDto,
} from "../types/backend";

interface UploadVideoOptions {
  onProgress?: (percent: number) => void;
  sourceOrigin?: "web_upload" | "web_live";
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${getBackendBaseUrl()}${path}`, {
    headers: {
      Accept: "application/json",
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed with status ${response.status}`);
  }

  return (await response.json()) as T;
}

export async function fetchHealth(): Promise<HealthDto> {
  return requestJson<HealthDto>("/health");
}

export async function fetchJobs(status?: string): Promise<AnalysisJobListDto> {
  const query = new URLSearchParams();
  query.set("limit", "50");
  if (status) {
    query.set("status", status);
  }
  return requestJson<AnalysisJobListDto>(`/analysis-jobs?${query.toString()}`);
}

export async function fetchJob(jobId: string): Promise<AnalysisJobDto> {
  return requestJson<AnalysisJobDto>(`/analysis-jobs/${jobId}`);
}

export async function fetchSessions(jobId?: string): Promise<AnalysisSessionListDto> {
  const query = new URLSearchParams();
  if (jobId) {
    query.set("job_id", jobId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return requestJson<AnalysisSessionListDto>(`/sessions${suffix}`);
}

export async function fetchSession(sessionId: string): Promise<AnalysisSessionDto> {
  return requestJson<AnalysisSessionDto>(`/sessions/${sessionId}`);
}

export async function fetchSessionIncidents(sessionId: string): Promise<IncidentListDto> {
  return requestJson<IncidentListDto>(`/sessions/${sessionId}/incidents`);
}

export async function uploadVideo(file: File, options?: UploadVideoOptions): Promise<UploadedVideoDto> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("source_origin", options?.sourceOrigin ?? "web_upload");

  return new Promise<UploadedVideoDto>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${getBackendBaseUrl()}/videos`);
    xhr.responseType = "json";

    xhr.upload.onprogress = (event: ProgressEvent<EventTarget>) => {
      if (!event.lengthComputable || !options?.onProgress) {
        return;
      }
      const percent = Math.min(100, Math.round((event.loaded / event.total) * 100));
      options.onProgress(percent);
    };

    xhr.onerror = () => {
      reject(new Error("Upload failed."));
    };

    xhr.onload = () => {
      if (xhr.status < 200 || xhr.status >= 300) {
        const message =
          typeof xhr.response === "string"
            ? xhr.response
            : (xhr.response as { detail?: string } | null)?.detail ?? `Upload failed with status ${xhr.status}`;
        reject(new Error(message));
        return;
      }

      options?.onProgress?.(100);
      resolve(xhr.response as UploadedVideoDto);
    };

    xhr.send(formData);
  });
}

export async function createAnalysisJob(payload: CreateAnalysisJobRequestDto): Promise<AnalysisJobDto> {
  return requestJson<AnalysisJobDto>("/analysis-jobs", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}
