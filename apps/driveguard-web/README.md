# DriveGuard AI Web

This folder contains the current web dashboard MVP for DriveGuard AI.

## Target stack

- Vue 3
- TypeScript only
- Vite
- Bootstrap 5

## Intended MVP role

This client now supports the first browser-based demonstration flow:

1. upload a video from a desktop browser
2. trigger an analysis job through the FastAPI backend
3. show analysis jobs, sessions, incidents, and score results
4. record a short clip directly from the PC camera through the browser
5. upload the recorded clip automatically and process it through the same backend flow
6. provide a clean review surface for persisted backend results
7. distinguish `web_upload`, `web_live`, and later `android_upload` as explicit demo sources
8. present a more polished flow from landing page to final session review

## Local run

```bash
npm install
npm run dev
```

## Production build

```bash
npm run build
```
