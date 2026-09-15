import { ref } from "vue";

const STORAGE_KEY = "driveguard-backend-url";
const DEFAULT_URL = "http://127.0.0.1:8000";

const initialValue = globalThis.localStorage?.getItem(STORAGE_KEY) ?? DEFAULT_URL;

export const backendBaseUrl = ref<string>(initialValue);

export function setBackendBaseUrl(value: string): void {
  const normalized = value.trim().replace(/\/+$/, "") || DEFAULT_URL;
  backendBaseUrl.value = normalized;
  globalThis.localStorage?.setItem(STORAGE_KEY, normalized);
}

export function getBackendBaseUrl(): string {
  return backendBaseUrl.value;
}
