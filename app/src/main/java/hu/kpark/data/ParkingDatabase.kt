package hu.kpark.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ParkingData::class, Vehicle::class, ZoneData::class], version = 1)
@TypeConverters(value = [Vehicle.VehicleType::class])
abstract class ParkingDatabase: RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun parkingDataDao(): ParkingDataDao
    abstract fun zoneDataDao(): ZoneDataDao

    companion object {
        fun getDatabase(applicationContext: Context): ParkingDatabase {
            return Room.databaseBuilder(
                applicationContext,
                ParkingDatabase::class.java,
                "parking-database"
            ).build()
        }
    }
}