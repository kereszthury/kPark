package hu.kpark.trackingService.smsProcessing

import android.content.Context
import hu.kpark.R
import hu.kpark.data.Vehicle

class UnspecificSmsProcessor : SmsProcessor() {
    override fun interpretMessage(context: Context, message: String): Response = Response(
        ResponseType.Unhandled, context.getString(R.string.response_message_unhandled, message)
    )

    override fun getStartMessage(vehicle: Vehicle): String = vehicle.plate
}