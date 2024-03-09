package hu.kpark.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parkingData")
data class ParkingData(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) var id: Long? = null,
    @ColumnInfo(name = "vehiclePlate") var vehiclePlate: String,

    @ColumnInfo(name = "phoneNumber") var phoneNumber: String,

    @ColumnInfo(name = "cancelled") var cancelled: Boolean,
    @ColumnInfo(name = "startTime") var startTime: Long,
    @ColumnInfo(name = "endTime") var endTime: Long? = null,
)
