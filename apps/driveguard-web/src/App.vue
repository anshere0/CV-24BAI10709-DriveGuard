<script setup lang="ts">
import { computed, ref } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

import { backendBaseUrl, setBackendBaseUrl } from "./config/backend";

const route = useRoute();
const draftBaseUrl = ref<string>(backendBaseUrl.value);

const currentTitle = computed(() => {
  if (route.name === "home") {
    return "Home";
  }
  if (route.name === "new-analysis") {
    return "New Analysis";
  }
  if (route.name === "live-demo") {
    return "Live Demo";
  }
  if (route.name === "jobs") {
    return "Jobs";
  }
  if (route.name === "session-detail") {
    return "Session Detail";
  }
  return "DriveGuard AI";
});

function saveBackendUrl(): void {
  setBackendBaseUrl(draftBaseUrl.value);
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar-panel">
      <div>
        <div class="small text-uppercase fw-bold text-secondary mb-2">DriveGuard AI</div>
        <h1 class="h3 fw-bold mb-3">Operations HMI</h1>
        <p class="text-secondary mb-4">
          One polished demo surface for browser uploads, live camera capture, and backend session review.
        </p>
      </div>

      <nav class="d-grid gap-2">
        <RouterLink class="nav-tile" :class="{ active: route.name === 'home' }" to="/">Home</RouterLink>
        <RouterLink class="nav-tile" :class="{ active: route.name === 'new-analysis' }" to="/new-analysis">
          New Analysis
        </RouterLink>
        <RouterLink class="nav-tile" :class="{ active: route.name === 'live-demo' }" to="/live-demo">
          Live Demo
        </RouterLink>
        <RouterLink class="nav-tile" :class="{ active: route.name === 'jobs' }" to="/jobs">Jobs</RouterLink>
      </nav>

      <div class="glass-panel rounded-4 p-3">
        <div class="section-title mb-2">Demo flow</div>
        <div class="demo-step-list">
          <div class="demo-step-item">1. Capture or upload a video</div>
          <div class="demo-step-item">2. Watch backend progress and ETA</div>
          <div class="demo-step-item">3. Open the final session review</div>
        </div>
      </div>

      <div class="glass-panel rounded-4 p-3 mt-auto">
        <div class="section-title mb-2">Backend URL</div>
        <input v-model="draftBaseUrl" class="form-control form-control-sm mb-2" type="text" />
        <button class="btn btn-dark btn-sm w-100" type="button" @click="saveBackendUrl">Apply</button>
        <div class="small text-secondary mt-2">{{ backendBaseUrl }}</div>
      </div>
    </aside>

    <main class="content-shell">
      <header class="topbar glass-panel rounded-4 p-3 p-lg-4 mb-4">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-3">
          <div>
            <div class="section-title">Current view</div>
            <div class="h4 fw-bold mb-0">{{ currentTitle }}</div>
          </div>
          <div class="d-flex flex-wrap align-items-center gap-2">
            <RouterLink class="btn btn-dark btn-sm" :to="{ name: 'live-demo' }">Run Demo</RouterLink>
            <a class="btn btn-outline-dark btn-sm" :href="`${backendBaseUrl}/docs`" target="_blank" rel="noreferrer">
              Swagger
            </a>
            <a class="btn btn-outline-dark btn-sm" :href="`${backendBaseUrl}/redoc`" target="_blank" rel="noreferrer">
              ReDoc
            </a>
          </div>
        </div>
      </header>

      <RouterView />
    </main>
  </div>
</template>
