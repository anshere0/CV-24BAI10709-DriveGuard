package com.driveguard.mobile.inference.objects

enum class RiskObjectCategory {
    PHONE,
    DRINK,
}

data class RiskObjectDetection(
    val category: RiskObjectCategory,
    val label: String,
    val confidencePercent: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class RiskObjectSnapshot(
    val detections: List<RiskObjectDetection> = emptyList(),
    val statusLabel: String = "Waiting for object detections...",
) {
    val hasPhone: Boolean
        get() = detections.any { detection -> detection.category == RiskObjectCategory.PHONE }

    val hasDrink: Boolean
        get() = detections.any { detection -> detection.category == RiskObjectCategory.DRINK }

    val hasRiskObject: Boolean
        get() = detections.isNotEmpty()
}
