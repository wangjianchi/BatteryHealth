package com.batteryhealth.app.data.repository

import androidx.room.*
import com.batteryhealth.app.data.model.ChargingRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingRecordDao {
    @Query("SELECT * FROM charging_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<ChargingRecord>>

    @Query("SELECT * FROM charging_records ORDER BY timestamp ASC")
    fun getAllRecordsAsc(): Flow<List<ChargingRecord>>

    @Query("SELECT COUNT(*) FROM charging_records")
    suspend fun getRecordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ChargingRecord)

    @Delete
    suspend fun deleteRecord(record: ChargingRecord)

    @Query("DELETE FROM charging_records")
    suspend fun deleteAllRecords()
}

@Database(
    entities = [ChargingRecord::class],
    version = 2,  // 升级版本号
    exportSchema = false
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun chargingRecordDao(): ChargingRecordDao

    companion object {
        @Volatile
        private var INSTANCE: BatteryDatabase? = null

        fun getDatabase(context: android.content.Context): BatteryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BatteryDatabase::class.java,
                    "battery_health_database"
                ).fallbackToDestructiveMigration() // 开发模式：清空数据库重建
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
