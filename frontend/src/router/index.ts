import { createRouter, createWebHistory } from "vue-router";

import HomeView from "../views/HomeView.vue";
import LiveDemoView from "../views/LiveDemoView.vue";
import SessionHistoryView from "../views/SessionHistoryView.vue";
import SessionDetailView from "../views/SessionDetailView.vue";
import AnalyticsView from "../views/AnalyticsView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomeView,
    },
    {
      path: "/monitor",
      name: "monitor",
      component: LiveDemoView,
    },
    {
      path: "/history",
      name: "history",
      component: SessionHistoryView,
    },
    {
      path: "/sessions/:sessionId",
      name: "session-detail",
      component: SessionDetailView,
      props: true,
    },
    {
      path: "/analytics/:sessionId",
      name: "analytics",
      component: AnalyticsView,
      props: true,
    },
  ],
});
