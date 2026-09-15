<script setup lang="ts">
import { onMounted, ref } from "vue";
import { fetchSessions } from "../services/api";
import type { AnalysisSessionDto } from "../types/backend";

const sessions = ref<AnalysisSessionDto[]>([]);
const isLoading = ref(true);
const error = ref<string | null>(null);

async function loadSessions() {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await fetchSessions();
    sessions.value = result.items;
  } catch (err: any) {
    error.value = err.message || "Failed to load sessions.";
  } finally {
    isLoading.value = false;
  }
}

onMounted(() => {
  loadSessions();
});
</script>

<template>
  <div class="glass-panel rounded-4 p-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h2 class="h5 fw-bold mb-0">Session History</h2>
      <button class="btn btn-outline-dark btn-sm" @click="loadSessions" :disabled="isLoading">
        Refresh
      </button>
    </div>

    <div v-if="isLoading" class="text-center text-secondary py-5">
      Loading sessions...
    </div>
    
    <div v-else-if="error" class="alert alert-danger">
      {{ error }}
    </div>

    <div v-else-if="sessions.length === 0" class="text-center text-secondary py-5">
      No analysis sessions found.
    </div>

    <div v-else class="table-responsive">
      <table class="table align-middle">
        <thead>
          <tr>
            <th>Date</th>
            <th>Source</th>
            <th>Duration</th>
            <th>Score</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="session in sessions" :key="session.id">
            <td>{{ new Date(session.created_at).toLocaleString() }}</td>
            <td>{{ session.source_name }}</td>
            <td>{{ session.duration_seconds.toFixed(1) }}s</td>
            <td>
              <span class="badge" :class="session.score >= 80 ? 'bg-success' : session.score >= 60 ? 'bg-warning' : 'bg-danger'">
                {{ session.score }}
              </span>
            </td>
            <td>
              <div class="d-flex gap-2">
                <RouterLink class="btn btn-sm btn-dark" :to="{ name: 'session-detail', params: { sessionId: session.id } }">
                  Details
                </RouterLink>
                <RouterLink class="btn btn-sm btn-outline-dark" :to="{ name: 'analytics', params: { sessionId: session.id } }">
                  Analytics
                </RouterLink>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
