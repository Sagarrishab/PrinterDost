package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Query("SELECT * FROM printers ORDER BY lastChecked DESC, name ASC")
    fun getAllPrinters(): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printers WHERE id = :id")
    suspend fun getPrinterById(id: Int): PrinterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrinter(printer: PrinterEntity): Long

    @Update
    suspend fun updatePrinter(printer: PrinterEntity)

    @Delete
    suspend fun deletePrinter(printer: PrinterEntity)

    @Query("DELETE FROM printers WHERE id = :id")
    suspend fun deletePrinterById(id: Int)

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC")
    fun getAllDiagnosticLogs(): Flow<List<DiagnosticLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosticLog(log: DiagnosticLogEntity)

    @Query("DELETE FROM diagnostic_logs WHERE printerId = :printerId")
    suspend fun deleteLogsForPrinter(printerId: Int)

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearAllDiagnosticLogs()
}
