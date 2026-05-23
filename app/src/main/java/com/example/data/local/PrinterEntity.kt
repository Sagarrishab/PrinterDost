package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "printers")
data class PrinterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ipAddress: String,
    val brand: String, // e.g. "HP", "Canon", "Epson", "Brother", "Generic"
    val location: String, // e.g. "Main Office", "Home Office"
    val isOnline: Boolean = false,
    val latencyMs: Int = -1,
    val lastChecked: Long = 0,
    val port9100Open: Boolean = false, // JetDirect
    val port631Open: Boolean = false,  // IPP
    val port80Open: Boolean = false,   // HTTP Admin
    val port515Open: Boolean = false   // LPR
)
