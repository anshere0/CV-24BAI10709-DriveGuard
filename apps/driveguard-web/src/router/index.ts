import { createRouter, createWebHistory } from "vue-router";

import HomeView from "../views/HomeView.vue";
import JobsView from "../views/JobsView.vue";
import LiveDemoView from "../views/LiveDemoView.vue";
import NewAnalysisView from "../views/NewAnalysisView.vue";
import SessionDetailView from "../views/SessionDetailView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomeView,
    },
    {
      path: "/new-analysis",
      name: "new-analysis",
      component: NewAnalysisView,
    },
    {
      path: "/live-demo",
      name: "live-demo",
      component: LiveDemoView,
    },
    {
      path: "/jobs",
      name: "jobs",
      component: JobsView,
    },
    {
      path: "/sessions/:sessionId",
      name: "session-detail",
      component: SessionDetailView,
      props: true,
    },
  ],
});
