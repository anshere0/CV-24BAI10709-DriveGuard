# Use Case Diagram

```mermaid
usecaseDiagram
    actor Evaluator as "Operator / Evaluator"
    
    package "DriveGuard System" {
        usecase "Upload Video" as UC1
        usecase "Monitor Live Camera" as UC2
        usecase "Review Session History" as UC3
        usecase "View Risk Analytics" as UC4
        usecase "Run Headless CLI" as UC5
    }
    
    Evaluator --> UC1
    Evaluator --> UC2
    Evaluator --> UC3
    Evaluator --> UC4
    Evaluator --> UC5
```
