export interface HealthDto {
  status: string;
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
  source_origin: "web_upload" | "web_live";
  source_paths: string[];
  uploaded_video_ids: string[];
  config_path: string;
}
