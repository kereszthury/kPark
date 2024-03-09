package hu.kpark.trackingService

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import hu.kpark.data.ParkingData
import hu.kpark.data.ParkingDatabase
import hu.kpark.data.Vehicle
import hu.kpark.trackingService.smsProcessing.SmsProcessor
import hu.kpark.trackingService.smsProcessing.SmsProcessorFactory
import kotlin.concurrent.thread

class ParkingTracker : Service() {
    private lateinit var smsProcessor: SmsProcessor
    private lateinit var smsManager: SmsManager
    private lateinit var notifier: TrackerNotificationBuilder

    private lateinit var database: ParkingDatabase
    lateinit var parkingData: ParkingData
        private set
    private lateinit var vehicleData: Vehicle

    private lateinit var preferences: SharedPreferences

    // Registered broadcast receiver handling incoming sms and bluetooth events
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> handleBluetoothStateChange(intent)
                BluetoothDevice.ACTION_ACL_CONNECTED -> handleBluetoothDeviceConnect(intent)
                Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleSmsReceive(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        smsManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                applicationContext.getSystemService(SmsManager::class.java)
            else SmsManager.getDefault()

        notifier = TrackerNotificationBuilder(this)

        instance = this
        preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        // Register broadcast receiver
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        registerReceiver(broadcastReceiver, intentFilter)

        startForeground(1, notifier.getStartedNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        thread {
            database = ParkingDatabase.getDatabase(applicationContext)

            // If tracker was started with preferences, starts new parking, else loads the latest
            if (preferences.getString(PREFERENCE_STATE, STOPPED)!! == STOPPED) {
                intent?.extras?.let {
                    val phone = it.getString(START_PARAM_PHONE)!!
                    val plate = it.getString(START_PARAM_PLATE)!!
                    smsProcessor = SmsProcessorFactory.getProcessor(phone)
                    startParking(phone, plate)
                }
            } else {
                parkingData = database.parkingDataDao().getActive().first()
                vehicleData = database.vehicleDao().find(parkingData.vehiclePlate)!!
                smsProcessor = SmsProcessorFactory.getProcessor(parkingData.phoneNumber)
            }

            // Invokes the callback indicating that parking service started again
            startCallback?.invoke()
            startCallback = null
        }

        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Notifies the user to turn on bluetooth if it was turned off to automatically stop parking
    private fun handleBluetoothStateChange(intent: Intent) {
        when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
            BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_OFF -> {
                notifier.notifyOngoing()
            }
        }
    }

    // Sends stop message if the phone connected to the bluetooth device associated with the vehicle
    private fun handleBluetoothDeviceConnect(intent: Intent) {
        if (vehicleData.identifier == null) return

        val device =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

        if (vehicleData.identifier.equals(device?.address)) sendStopMessage()
    }

    // Handles the incoming sms with the sms processor and notifies the user of the changes
    private fun handleSmsReceive(intent: Intent) {
        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        var messageBody = ""
        for (message in smsMessages) {
            if (message.displayOriginatingAddress.equals(parkingData.phoneNumber)) {
                messageBody = messageBody.plus(message.messageBody)
            }
        }
        if (messageBody == "") return
        val response = smsProcessor.interpretMessage(applicationContext, messageBody)
        when (response.type) {
            SmsProcessor.ResponseType.Started -> {
                notifier.notifyOngoing()
                parkingStarted()
            }
            SmsProcessor.ResponseType.ManualStop,
            SmsProcessor.ResponseType.AutomaticStop -> {
                notifier.notifyStopped(response.info)
                parkingStopped()
            }
            SmsProcessor.ResponseType.NoActiveParking,
            SmsProcessor.ResponseType.NotEnoughBalance -> {
                notifier.notifyError(response.info)
                parkingStopped(cancelled = true)
            }
            SmsProcessor.ResponseType.StopForecast,
            SmsProcessor.ResponseType.ParkingStartsLater -> {
                notifier.notifyAdditional(response.info)
            }
            SmsProcessor.ResponseType.ParkingRequestDeleted -> {
                notifier.notifyStopped(response.info)
                parkingStopped(cancelled = true)
            }
            SmsProcessor.ResponseType.Unhandled -> {
                notifier.notifyError(response.info)
            }
        }
    }

    // Sends out the start message
    private fun startParking(phone: String, vehiclePlate: String) {
        vehicleData = database.vehicleDao().find(vehiclePlate)!!
        parkingData = ParkingData(
            null, vehicleData.plate, phone, false, System.currentTimeMillis()
        )
        database.parkingDataDao().insert(parkingData)

        smsManager.sendTextMessage(
            phone,
            null,
            smsProcessor.getStartMessage(vehicleData),
            null,
            null
        )

        preferences.edit().apply {
            putString(PREFERENCE_STATE, START_PENDING)
            apply()
        }
    }

    // Starts the parking, start confirmation received
    private fun parkingStarted() {
        preferences.edit().apply {
            putString(PREFERENCE_STATE, STARTED)
            apply()
        }
        sendBroadcast(Intent().setAction(STARTED))
    }

    // Sends out the stop message
    private fun sendStopMessage() {
        smsManager.sendTextMessage(
            parkingData.phoneNumber,
            null,
            "stop",
            null,
            null)
        preferences.edit().apply {
            putString(PREFERENCE_STATE, STOP_PENDING)
            apply()
        }
        notifier.notifyOngoing()
    }

    // Stops the parking service, stop confirmation received
    private fun parkingStopped(cancelled: Boolean = false) {
        thread {
            parkingData.endTime = System.currentTimeMillis()
            parkingData.cancelled = cancelled
            database.parkingDataDao().update(parkingData)
        }

        preferences.edit().apply {
            putString(PREFERENCE_STATE, STOPPED)
            apply()
        }

        // Stops the parking service itself
        instance = null
        sendBroadcast(Intent().setAction(STOPPED))
        applicationContext.stopService(Intent(applicationContext, ParkingTracker::class.java))
    }

    companion object {
        const val START_PENDING = "Kpark.ParkingPending"
        const val STARTED = "Kpark.ParkingStartConfirmed"
        const val STOP_PENDING = "Kpark.ParkingStopPending"
        const val STOPPED = "Kpark.ParkingStopConfirmed"

        const val PREFERENCES = "trackerPreferences"
        const val PREFERENCE_STATE = "activeParking"

        const val START_PARAM_PHONE = "phone"
        const val START_PARAM_PLATE = "plate"

        // The active parking tracker instance, null if service is not running
        var instance: ParkingTracker? = null
            private set

        // Called after the service started and data is loaded in
        var startCallback: (() -> Unit)? = null

        // Method used to manually stop the parking process (called by ongoing parking stop button)
        fun manualStop() = instance?.sendStopMessage()
    }
}