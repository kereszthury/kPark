package hu.kpark.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicle")
    fun getAll(): List<Vehicle>

    @Query("SELECT * FROM vehicle WHERE plate = :plate LIMIT 1")
    fun find(plate: String): Vehicle?

    @Insert
    fun insert(vehicle: Vehicle): Long

    @Update
    fun update(vehicle: Vehicle)

    @Delete
    fun deleteItem(vehicle: Vehicle)
}