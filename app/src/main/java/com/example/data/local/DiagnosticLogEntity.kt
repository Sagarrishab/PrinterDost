package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val printerId: Int,
    val printerName: String,
    val ipAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOnline: Boolean,
    val latencyMs: Int,
    val port9100Open: Boolean,
    val port631Open: Boolean,
    val port80Open: Boolean,
    val port515Open: Boolean,
    val selectedSymptom: String,
    val diagnosisResult: String
)
