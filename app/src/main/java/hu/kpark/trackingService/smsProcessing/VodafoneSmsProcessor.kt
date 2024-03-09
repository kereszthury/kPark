package hu.kpark.trackingService.smsProcessing

import android.content.Context
import hu.kpark.R
import hu.kpark.data.Vehicle

class VodafoneSmsProcessor : SmsProcessor() {

    override fun interpretMessage(context: Context, message: String): Response {
        return when {
            message.matches(Regex(".*Parkol.sa \\d{4} z.n.ban.* elindult.*")) -> {
                val finder = Regex("\\d{2}:\\d{2}").find(message)
                val startTime = finder?.value
                val endTime = finder?.next()?.value
                Response(
                    ResponseType.Started, context.getString(
                        R.string.response_message_start, startTime, endTime
                    )
                )
            }

            message.matches(Regex(".* kezdett parkol.sa .* k.r.s.re le.llt.*")) -> {
                val endTime = Regex("\\d{2}:\\d{2}").find(message)?.next()?.value
                Response(
                    ResponseType.ManualStop,
                    context.getString(R.string.response_message_manual_stop, endTime)
                )
            }

            message.matches(Regex(".*megkezdett parkol.sa .* lej.rt.*")) -> {
                val endTime = Regex("\\d{2}:\\d{2}").find(message)?.next()?.value
                Response(
                    ResponseType.AutomaticStop,
                    context.getString(R.string.response_message_automatic_stop, endTime)
                )
            }

            message.matches(Regex(".* ind.tott parkol.sa hamarosan.* lej.r.*")) -> {
                val endTime = Regex("\\d{2}:\\d{2}").find(message)?.next()?.value
                Response(
                    ResponseType.StopForecast,
                    context.getString(R.string.response_message_stop_forecast, endTime)
                )
            }

            message.matches(Regex(".* .nnek nincs a rendszerben fut. parkol.sa.*")) -> {
                Response(
                    ResponseType.NoActiveParking,
                    context.getString(R.string.response_message_no_parking)
                )
            }

            message.matches(Regex(".*Sikertelen v.s.rl.s!.*")) -> {
                Response(
                    ResponseType.NotEnoughBalance,
                    context.getString(R.string.response_message_no_balance)
                )
            }

            message.matches(Regex(".*Parkol.sa \\d{4} z.n.ban .* el fog indulni.*")) -> {
                val startTime = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}").find(message)?.value
                Response(
                    ResponseType.ParkingStartsLater,
                    context.getString(R.string.response_message_future_parking, startTime)
                )
            }

            message.matches(Regex(".*Parkol.s.t t.r.lt.k.*")) -> {
                Response(
                    ResponseType.ParkingRequestDeleted,
                    context.getString(R.string.response_message_parking_cancelled)
                )
            }

            else -> Response(
                ResponseType.Unhandled,
                context.getString(R.string.response_message_unhandled, message)
            )
        }
    }

    override fun getStartMessage(vehicle: Vehicle): String {
        return when (vehicle.type) {
            Vehicle.VehicleType.Car -> vehicle.plate
            Vehicle.VehicleType.Motorcycle -> vehicle.plate + ",M"
            Vehicle.VehicleType.Truck -> vehicle.plate + ",T"
            Vehicle.VehicleType.Bus -> vehicle.plate + ",B"
        }
    }
}