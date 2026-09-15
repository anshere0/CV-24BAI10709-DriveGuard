package com.driveguard.mobile.inference.face

data class FaceSignalSnapshot(
    val facePresent: Boolean = false,
    val eyeClosurePercent: Int = 0,
    val yawnPercent: Int = 0,
    val headTurnPercent: Int = 0,
    val headPitchPercent: Int = 0,
    val headOrientationLabel: String = "Centering",
    val gazeDirectionLabel: String = "Forward",
    val gazeDown: Boolean = false,
    val headNodDetected: Boolean = false,
    val statusLabel: String = "Waiting for face landmarks...",
)
