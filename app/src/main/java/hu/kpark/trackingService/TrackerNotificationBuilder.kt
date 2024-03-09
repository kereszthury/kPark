package hu.kpark.trackingService

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import hu.kpark.Permissions
import hu.kpark.R

class TrackerNotificationBuilder(tracker: ParkingTracker) {
    private val context: Context = tracker.applicationContext
    private val notificationManager: NotificationManager =
        context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel()
    }

    @TargetApi(26)
    private fun createChannel() {
        val name = context.getString(R.string.notification_channel)
        val description = context.getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val mChannel = NotificationChannel(name, name, importance)
        mChannel.description = description
        mChannel.enableLights(true)
        mChannel.lightColor = Color.YELLOW
        notificationManager.createNotificationChannel(mChannel)
    }

    fun getStartedNotification(): Notification = getOngoingBuilder().build()

    fun notifyOngoing(id: Int = 1) = notificationManager.notify(id, getOngoingBuilder().build())

    private fun getOngoingBuilder(): NotificationCompat.Builder {
        val preferences = context.getSharedPreferences(ParkingTracker.PREFERENCES, Context.MODE_PRIVATE)
        val serviceState = preferences.getString(ParkingTracker.PREFERENCE_STATE, ParkingTracker.STOPPED)!!

        val notification = NotificationCompat.Builder(context,  context.getString(R.string.notification_channel))
            .setSmallIcon(R.mipmap.app_icon)
            .setColor(ContextCompat.getColor(context, R.color.jade))
            .setOngoing(true)

        when (serviceState) {
            ParkingTracker.START_PENDING, ParkingTracker.STOPPED -> {
                notification.setContentTitle(context.getString(R.string.pending_parking))
                notification.setContentText(context.getString(R.string.waiting_for_provider_response))
                notification.color = ContextCompat.getColor(context, R.color.flax)
            }
            ParkingTracker.STARTED -> {
                notification.setContentTitle(context.getString(R.string.ongoing_parking))
            }
            ParkingTracker.STOP_PENDING -> {
                notification.setContentTitle(context.getString(R.string.ongoing_parking))
                notification.setContentText(context.getString(R.string.stop_message_sent_waiting_for_response))
                notification.color = ContextCompat.getColor(context, R.color.flax)
            }
        }

        if (Permissions.hasBluetooth(context)) {
            val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
            if (!bluetoothManager.adapter.isEnabled && serviceState != ParkingTracker.STOP_PENDING) {
                notification.setContentText(context.getString(R.string.enable_bluetooth_notification_message))
                    .setColor(ContextCompat.getColor(context, R.color.flax))
                    .setColorized(true)
            }
        }

        return notification
    }

    fun notifyAdditional(additionalInfo: String, id: Int = 2) {
        val notification = NotificationCompat.Builder(context,  context.getString(R.string.notification_channel))
            .setSmallIcon(R.mipmap.app_icon)
            .setContentTitle(context.getString(R.string.parking_info))
            .setContentText(additionalInfo)
            .setColor(ContextCompat.getColor(context, R.color.flax))
            .setOngoing(false)
        notificationManager.notify(id, notification.build())
    }

    fun notifyStopped(additionalInfo: String = "", id: Int = 3) {
        val notification = NotificationCompat.Builder(context,  context.getString(R.string.notification_channel))
            .setSmallIcon(R.mipmap.app_icon)
            .setContentTitle(context.getString(R.string.parking_finished))
            .setContentText(additionalInfo)
            .setColor(ContextCompat.getColor(context, R.color.jasper))
            .setOngoing(false)
        notificationManager.notify(id, notification.build())
    }

    fun notifyError(additionalInfo: String, id: Int = 4) {
        val notification = NotificationCompat.Builder(context,  context.getString(R.string.notification_channel))
            .setSmallIcon(R.mipmap.app_icon)
            .setContentTitle(context.getString(R.string.warning))
            .setContentText(additionalInfo)
            .setColor(ContextCompat.getColor(context, R.color.jasper))
            .setColorized(true)
            .setOngoing(false)
        notificationManager.notify(id, notification.build())
    }
}