package hu.kpark.trackingService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class StartReceiver : BroadcastReceiver() {
    // Starts the parking tracker service on boot if it was running before the restart
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val preferences =
                context.getSharedPreferences(ParkingTracker.PREFERENCES, Context.MODE_PRIVATE)
            if (preferences.getString(
                    ParkingTracker.PREFERENCE_STATE, ParkingTracker.STOPPED
                ) == ParkingTracker.STOPPED
            ) return

            val serviceIntent = Intent(context, ParkingTracker::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}