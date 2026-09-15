<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink } from "vue-router";

import SourceBadge from "../components/SourceBadge.vue";
import { fetchSession, fetchSessionIncidents } from "../services/api";
import type { AnalysisSessionDto, IncidentDto } from "../types/backend";
import {
  formatIncidentWindow,
  getDominantIncident,
  getScoreMeta,
  getTopIncidents,
  getSourceOriginMeta,
} from "../utils/analysisPresentation";

const props = defineProps<{
  sessionId: string;
}>();

const loading = ref<boolean>(true);
const error = ref<string>("");
const session = ref<AnalysisSessionDto | null>(null);
const incidents = ref<IncidentDto[]>([]);
const eventCountEntries = computed(() =>
  Object.entries(session.value?.event_counts ?? {}).sort((left, right) => right[1] - left[1]),
);
const scoreMeta = computed(() => (session.value ? getScoreMeta(session.value.score) : null));
const dominantIncident = computed(() => getDominantIncident(incidents.value));
const topIncidents = computed(() => getTopIncidents(incidents.value));
const sourceMeta = computed(() =>
  session.value ? getSourceOriginMeta(session.value.source_origin) : getSourceOriginMeta("web_upload"),
);

async function loadSession(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const [sessionResponse, incidentsResponse] = await Promise.all([
      fetchSession(props.sessionId),
      fetchSessionIncidents(props.sessionId),
    ]);
    session.value = sessionResponse;
    incidents.value = incidentsResponse.items;
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load session detail.";
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.sessionId,
  () => {
    void loadSession();
  },
);

onMounted(() => {
  void loadSession();
});
</script>

<template>
  <section class="hero-panel rounded-4 p-4 p-lg-5 mb-4">
    <div class="row g-4 align-items-center">
      <div class="col-lg-8">
        <span class="stage-badge mb-3">Session Detail</span>
        <h1 class="display-6 fw-bold mb-3">Risk session review</h1>
        <p class="lead mb-0">
          Present one complete analysis outcome with a readable score summary, key incidents, source context,
          and exported artifacts.
        </p>
      </div>
      <div class="col-lg-4">
        <div class="soft-card rounded-4 p-3">
          <div class="section-title mb-2">Navigation</div>
          <div class="d-flex flex-wrap gap-2">
            <RouterLink class="btn btn-outline-dark btn-sm" :to="{ name: 'history' }">Back to history</RouterLink>
            <RouterLink class="btn btn-dark btn-sm" :to="{ name: 'monitor' }">Run another demo</RouterLink>
          </div>
        </div>
      </div>
    </div>
  </section>

  <div v-if="error" class="alert alert-danger">{{ error }}</div>
  <section v-if="loading" class="glass-panel rounded-4 p-4 text-secondary">Loading session…</section>

  <template v-else-if="session">
    <section class="row g-4 mb-4">
      <div class="col-xl-8">
        <div class="glass-panel rounded-4 p-4 h-100">
          <div class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-4">
            <div>
              <div class="section-title mb-2">Executive summary</div>
              <div class="h4 fw-bold mb-2">{{ scoreMeta?.label }}</div>
              <div class="text-secondary">{{ scoreMeta?.helper }}</div>
            </div>
            <div class="session-score-shell" :class="`session-score-${scoreMeta?.tone ?? 'attention'}`">
              <div class="session-score-value">{{ session.score }}</div>
              <div class="small">risk score</div>
            </div>
          </div>

          <div class="row g-3">
            <div class="col-md-6 col-xl-3">
              <div class="soft-card rounded-4 p-3 h-100">
                <div class="section-title">Source</div>
                <SourceBadge class="mt-3" :source-origin="session.source_origin" />
                <div class="small text-secondary mt-2">{{ sourceMeta.detail }}</div>
              </div>
            </div>
            <div class="col-md-6 col-xl-3">
              <div class="soft-card rounded-4 p-3 h-100">
                <div class="section-title">Incidents</div>
                <div class="metric-value mt-2">{{ incidents.length }}</div>
                <div class="small text-secondary mt-2">Persisted incident windows</div>
              </div>
            </div>
            <div class="col-md-6 col-xl-3">
              <div class="soft-card rounded-4 p-3 h-100">
                <div class="section-title">Frames</div>
                <div class="metric-value mt-2">{{ session.frame_count }}</div>
                <div class="small text-secondary mt-2">Processed frame count</div>
              </div>
            </div>
            <div class="col-md-6 col-xl-3">
              <div class="soft-card rounded-4 p-3 h-100">
                <div class="section-title">Duration</div>
                <div class="metric-value mt-2">{{ session.duration_seconds.toFixed(1) }}s</div>
                <div class="small text-secondary mt-2">Evaluated clip window</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="col-xl-4">
        <div class="glass-panel rounded-4 p-4 h-100">
          <div class="section-title mb-3">Critical incident spotlight</div>
          <div v-if="dominantIncident" class="soft-card rounded-4 p-3 mb-3">
            <div class="fw-semibold mb-2">{{ dominantIncident.event_type }}</div>
            <div class="small text-secondary mb-2">{{ formatIncidentWindow(dominantIncident) }}</div>
            <div class="small text-secondary">
              Severity {{ dominantIncident.max_severity }} • {{ dominantIncident.occurrences }} occurrence(s)
            </div>
            <div class="small text-secondary mt-2">{{ dominantIncident.last_message }}</div>
          </div>
          <div v-else class="empty-panel rounded-4 p-4">
            <div class="fw-semibold mb-2">No incidents persisted</div>
            <div class="small text-secondary">This session completed without stored incident windows.</div>
          </div>

          <div class="soft-card rounded-4 p-3">
            <div class="fw-semibold mb-2">Session provenance</div>
            <div class="small text-secondary">{{ session.source_name }}</div>
            <div class="small text-secondary mt-2 text-break">{{ session.source_path ?? "Uploaded source" }}</div>
          </div>
        </div>
      </div>
    </section>

    <section class="row g-4">
      <div class="col-lg-7">
        <div class="glass-panel rounded-4 p-4 h-100">
          <div class="d-flex justify-content-between align-items-center gap-3 mb-3">
            <div>
              <div class="section-title">Incident review</div>
              <div class="small text-secondary">Major incidents surfaced first for a cleaner demo narrative.</div>
            </div>
          </div>

          <div v-if="topIncidents.length > 0" class="d-grid gap-3 mb-4">
            <div v-for="incident in topIncidents" :key="incident.id" class="soft-card rounded-4 p-3">
              <div class="d-flex justify-content-between align-items-start gap-3">
                <div>
                  <div class="fw-semibold">{{ incident.event_type }}</div>
                  <div class="small text-secondary mt-1">{{ formatIncidentWindow(incident) }}</div>
                </div>
                <div class="score-pill score-pill-sm">S{{ incident.max_severity }}</div>
              </div>
              <div class="small text-secondary mt-2">
                {{ incident.occurrences }} occurrence(s) • {{ incident.last_message }}
              </div>
            </div>
          </div>

          <div v-if="incidents.length > 0" class="table-responsive">
            <table class="table align-middle mb-0">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Window</th>
                  <th>Severity</th>
                  <th>Occurrences</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="incident in incidents" :key="incident.id">
                  <td class="fw-semibold">{{ incident.event_type }}</td>
                  <td>{{ formatIncidentWindow(incident) }}</td>
                  <td>{{ incident.max_severity }}</td>
                  <td>{{ incident.occurrences }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-panel rounded-4 p-4">
            <div class="fw-semibold mb-2">No incident table to show</div>
            <div class="small text-secondary">This session currently has no stored incidents.</div>
          </div>
        </div>
      </div>

      <div class="col-lg-5">
        <div class="glass-panel rounded-4 p-4 h-100">
          <div class="section-title mb-3">Session context</div>

          <div class="soft-card rounded-4 p-3 mb-3">
            <div class="fw-semibold mb-2">Event counts</div>
            <div v-if="eventCountEntries.length === 0" class="small text-secondary">No event counts stored.</div>
            <div
              v-for="[name, value] in eventCountEntries"
              :key="name"
              class="d-flex justify-content-between align-items-center small mb-2"
            >
              <span>{{ name }}</span>
              <span class="fw-semibold">{{ value }}</span>
            </div>
          </div>

          <div class="soft-card rounded-4 p-3 mb-3">
            <div class="fw-semibold mb-2">Exports and artifacts</div>
            <div v-if="session.artifacts.length === 0" class="small text-secondary">No artifacts listed.</div>
            <div v-for="artifact in session.artifacts" :key="artifact.id" class="artifact-row">
              <div>
                <div class="fw-semibold small">{{ artifact.artifact_type }}</div>
                <div class="small text-secondary text-break">{{ artifact.path }}</div>
              </div>
            </div>
          </div>

          <div class="soft-card rounded-4 p-3">
            <div class="fw-semibold mb-2">Presentation note</div>
            <div class="small text-secondary">
              This screen is now designed to be shown directly during a demo, with the score narrative,
              incident spotlight, and provenance all visible without extra explanation.
            </div>
          </div>
        </div>
      </div>
    </section>

  </template>
</template>
