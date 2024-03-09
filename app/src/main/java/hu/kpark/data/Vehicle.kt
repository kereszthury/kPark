package hu.kpark.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import hu.kpark.R

@Entity(tableName = "vehicle")
data class Vehicle(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) var id: Long? = null,
    @ColumnInfo(name = "plate") var plate: String,
    @ColumnInfo(name = "identifier") var identifier: String?,
    @ColumnInfo(name = "type") var type: VehicleType,

    ) {
    enum class VehicleType(val stringResource: Int) {
        Car(R.string.car), Motorcycle(R.string.motorcycle), Truck(R.string.truck), Bus(R.string.bus);

        companion object {
            @JvmStatic
            @TypeConverter
            fun getByOrdinal(ordinal: Int): VehicleType? {
                var result: VehicleType? = null
                for (type in values()) {
                    if (type.ordinal == ordinal) {
                        result = type
                        break
                    }
                }
                return result
            }

            @JvmStatic
            @TypeConverter
            fun toInt(type: VehicleType): Int {
                return type.ordinal
            }

            fun getIcon(type: VehicleType): Int {
                return when (type) {
                    Car -> R.drawable.type_car
                    Motorcycle -> R.drawable.type_motorbike
                    Truck -> R.drawable.type_truck
                    Bus -> R.drawable.type_bus
                }
            }
        }
    }

    companion object {

        private val plateFormats = listOf<Pair<Regex, (String) -> String>>(
            Pair(Regex("[a-zA-z][a-zA-z][a-zA-z][0-9][0-9][0-9]")) { plate ->
                "${plate.substring(0, 3)}-${plate.substring(3, 6)}"
            },
            Pair(Regex("[a-zA-z][a-zA-z][a-zA-z][a-zA-z][0-9][0-9][0-9]")) { plate ->
                "${plate.substring(0, 4)}-${plate.substring(4, 7)}"
            },
        )

        fun isPlateValid(plate: String): Boolean {
            for (format in plateFormats) {
                if (format.first.matches(plate)) return true
            }
            return false
        }

        fun getPlateDisplayFormat(plate: String): String {
            for (format in plateFormats) {
                if (format.first.matches(plate)) return format.second(plate).uppercase()
            }

            return plate
        }
    }
}