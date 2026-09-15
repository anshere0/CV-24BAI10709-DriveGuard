<script setup lang="ts">
import { computed, ref } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

import { backendBaseUrl, setBackendBaseUrl } from "./config/backend";

const route = useRoute();
const draftBaseUrl = ref<string>(backendBaseUrl.value);

const currentTitle = computed(() => {
  if (route.name === "home") {
    return "Dashboard";
  }
  if (route.name === "monitor") {
    return "Live Monitoring";
  }
  if (route.name === "history") {
    return "Session History";
  }
  if (route.name === "session-detail") {
    return "Session Detail";
  }
  if (route.name === "analytics") {
    return "Analytics";
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
          One polished demo surface for live camera capture and backend session review.
        </p>
      </div>

      <nav class="d-grid gap-2">
        <RouterLink class="nav-tile" :class="{ active: route.name === 'home' }" to="/">Dashboard</RouterLink>
        <RouterLink class="nav-tile" :class="{ active: route.name === 'monitor' }" to="/monitor">
          Live Monitoring
        </RouterLink>
        <RouterLink class="nav-tile" :class="{ active: route.name === 'history' }" to="/history">
          Session History
        </RouterLink>
      </nav>

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
            <RouterLink class="btn btn-dark btn-sm" :to="{ name: 'monitor' }">Start Monitoring</RouterLink>
            <a class="btn btn-outline-dark btn-sm" :href="`${backendBaseUrl}/docs`" target="_blank" rel="noreferrer">
              Swagger API
            </a>
          </div>
        </div>
      </header>

      <RouterView />
    </main>
  </div>
</template>
