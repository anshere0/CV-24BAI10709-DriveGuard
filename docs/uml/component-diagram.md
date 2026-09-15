# Component Diagram

```mermaid
componentDiagram
    package "Frontend (Vue.js)" {
        [Dashboard UI]
        [Live Monitor]
        [Session Analytics]
    }

    package "Backend (FastAPI)" {
        [API Routes]
        [Job Manager]
        [Database ORM]
    }

    package "CV Engine (Python)" {
        [Pipeline Controller]
        [Object Detector (YOLO)]
        [Face Mesh Analyzer]
        [Risk Scoring]
    }

    database "PostgreSQL / SQLite" {
        [Sessions Table]
        [Events Table]
    }

    [Dashboard UI] --> [API Routes] : HTTPS
    [Live Monitor] --> [API Routes] : HTTPS
    
    [API Routes] --> [Job Manager]
    [Job Manager] --> [Pipeline Controller] : Dispatches
    
    [Pipeline Controller] --> [Object Detector (YOLO)]
    [Pipeline Controller] --> [Face Mesh Analyzer]
    [Pipeline Controller] --> [Risk Scoring]
    
    [Database ORM] --> [Sessions Table]
    [Database ORM] --> [Events Table]
    
    [Pipeline Controller] --> [Database ORM] : Persists Results
```
