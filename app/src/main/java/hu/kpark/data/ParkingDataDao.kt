package hu.kpark.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ParkingDataDao {
    @Query("SELECT * FROM parkingData")
    fun getAll(): List<ParkingData>

    @Query("SELECT * FROM parkingData WHERE endTime IS NULL ORDER BY startTime DESC")
    fun getActive(): List<ParkingData>

    @Query("DELETE FROM parkingData WHERE id NOT IN (SELECT id FROM parkingData ORDER BY startTime DESC LIMIT 10)")
    fun deleteOldData()

    @Insert
    fun insert(parkingData: ParkingData): Long

    @Update
    fun update(parkingData: ParkingData)

    @Delete
    fun deleteItem(parkingData: ParkingData)
}