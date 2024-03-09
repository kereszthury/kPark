package hu.kpark.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ZoneDataDao {
    @Query("SELECT * FROM zoneData")
    fun getAll(): List<ZoneData>

    @Query("SELECT code FROM zoneData ORDER BY (:latitude - zoneData.latitude) * (:latitude - zoneData.latitude) + (:longitude - zoneData.longitude) * (:longitude - zoneData.longitude) LIMIT 1")
    fun find(latitude: Double, longitude: Double): String?

    @Insert
    fun insert(zoneData: ZoneData): Long

    @Update
    fun update(zoneData: ZoneData)

    @Delete
    fun deleteItem(zoneData: ZoneData)
}