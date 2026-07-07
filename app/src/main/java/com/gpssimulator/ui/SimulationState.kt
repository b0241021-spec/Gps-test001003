package com.gpssimulator.ui
data class SimulationState(
    val isRunning: Boolean = false,
    val currentSpeed: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val progress: Int = 0,
    val speedMultiplier: Float = 1.0f,
    val statusText: String = ""
)
