package hu.kpark.trackingService.smsProcessing

import android.content.Context
import hu.kpark.data.Vehicle

abstract class SmsProcessor {
    // Creates a response from the given message
    abstract fun interpretMessage(context: Context, message: String): Response

    // Returns the start message string which starts parking based on vehicle type
    abstract fun getStartMessage(vehicle: Vehicle): String

    class Response(val type: ResponseType, val info: String)

    enum class ResponseType {
        Started, // Parking successfully started
        ManualStop, // Parking was stopped by the application or the user
        AutomaticStop, // Parking was automatically stopped
        StopForecast, // Parking will stop soon

        NoActiveParking, // App tried to end a non-existent parking
        NotEnoughBalance, // Parking cannot be started because the sim card has not enough balance

        ParkingStartsLater, // Currently no need for parking, will start in the next payment season
        ParkingRequestDeleted, // Future parking request was deleted

        Unhandled // Sms response couldn't be handled by the smsProcessor
    }
}