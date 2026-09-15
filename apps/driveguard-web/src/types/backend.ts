export interface HealthDto {
  status: string;
  queue_backend: string;
  database_url: string;
}

export interface UploadedVideoDto {
  id: string;
  original_filename: string;
  stored_path: string;
  source_origin: string;
  content_type: string | null;
  size_bytes: number;
  created_at: string;
}

export interface DeviceDto {
  id: string;
  platform: string;
  display_name: string | null;
  manufacturer: string | null;
  model: string | null;
  os_version: string | null;
  app_version: string | null;
  created_at: string;
  updated_at: string;
  last_seen_at: string;
}

export interface DeviceSessionDto {
  id: string;
  device_id: string;
  source_origin: string;
  backend_url: string | null;
  status: string;
  started_at: string;
  ended_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface EdgeEventDto {
  id: string;
  device_session_id: string;
  uploaded_video_id: string | null;
  analysis_job_id: string | null;
  analysis_session_id: string | null;
  source_origin: string;
  suspected_event_type: string;
  suspected_event_title: string | null;
  local_confidence: number;
  local_priority_score: number;
  triggered_at: string;
  capture_started_at: string | null;
  capture_ended_at: string | null;
  clip_file_name: string | null;
  edge_signals: Record<string, unknown>;
  server_confirmation_status: string;
  server_confirmed_event_types: string[];
  created_at: string;
  updated_at: string;
}

export interface SessionEdgeSummaryDto {
  session_id: string;
  source_origin: string;
  has_edge_context: boolean;
  local_suspected_count: number;
  confirmed_count: number;
  not_confirmed_count: number;
  pending_confirmation_count: number;
  device: DeviceDto | null;
  device_session: DeviceSessionDto | null;
  edge_events: EdgeEventDto[];
}

export interface ReportArtifactDto {
  id: string;
  job_id: string;
  session_id: string | null;
  artifact_type: string;
  path: string;
  created_at: string;
}

export interface IncidentDto {
  id: string;
  session_id: string;
  event_type: string;
  event_key: string;
  source_name: string;
  started_at_seconds: number;
  ended_at_seconds: number;
  max_severity: number;
  occurrences: number;
  last_message: string;
}

export interface AnalysisSessionDto {
  id: string;
  job_id: string;
  source_name: string;
  source_path: string | null;
  source_origin: string;
  frame_count: number;
  duration_seconds: number;
  score: number;
  penalties: Record<string, number>;
  event_counts: Record<string, number>;
  output_directory: string;
  export_json_path: string | null;
  export_csv_path: string | null;
  created_at: string;
  incidents: IncidentDto[];
  artifacts: ReportArtifactDto[];
}

export interface AnalysisJobDto {
  id: string;
  status: string;
  source_type: string;
  source_origin: string;
  source_paths: string[];
  uploaded_video_ids: string[];
  config_path: string;
  queue_job_id: string | null;
  error_message: string | null;
  cancel_requested: boolean;
  progress_percent: number;
  progress_phase: string;
  progress_message: string | null;
  processed_frames: number;
  total_frames_estimate: number;
  estimated_remaining_seconds: number | null;
  total_sources: number;
  total_incidents: number;
  average_score: number;
  batch_report_export_path: string | null;
  created_at: string;
  started_at: string | null;
  completed_at: string | null;
  sessions: AnalysisSessionDto[];
  artifacts: ReportArtifactDto[];
}

export interface AnalysisJobListDto {
  items: AnalysisJobDto[];
  total: number;
  status_filter: string | null;
  limit: number;
}

export interface AnalysisSessionListDto {
  items: AnalysisSessionDto[];
  total: number;
  job_id: string | null;
}

export interface IncidentListDto {
  items: IncidentDto[];
  total: number;
  session_id: string;
}

export interface CreateAnalysisJobRequestDto {
  source_type: "video" | "batch";
  source_origin: "web_upload" | "web_live" | "android_upload";
  source_paths: string[];
  uploaded_video_ids: string[];
  config_path: string;
}
