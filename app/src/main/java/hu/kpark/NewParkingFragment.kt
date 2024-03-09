package hu.kpark

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import hu.kpark.data.ParkingDatabase
import hu.kpark.data.Vehicle
import hu.kpark.data.ZoneData
import hu.kpark.databinding.FragmentNewParkingBinding
import hu.kpark.databinding.VehicleListItemBinding
import hu.kpark.trackingService.ParkingTracker
import kotlin.concurrent.thread

class NewParkingFragment : DialogFragment() {
    private lateinit var binding: FragmentNewParkingBinding
    private lateinit var preferences: SharedPreferences
    private lateinit var database: ParkingDatabase
    private lateinit var activity: FragmentActivity
    private var vehicles = listOf<Vehicle>()
    private var lastSelectedVehiclePlate: String? = null
    private var selectedVehicle: Vehicle? = null

    private val permissions = Permissions(this)

    private lateinit var locationClient: FusedLocationProviderClient
    private var suggestedZone: String? = null
    private var startLocation: Location? = null

    // Navigating to ongoing parking if parking started or turning off loading if cancelled
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ParkingTracker.STARTED) {
                findNavController().navigate(R.id.ongoingParkingFragment)
            } else if (intent.action == ParkingTracker.STOPPED) {
                makeLoading(View.GONE)
                binding.startParking.isEnabled = true
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        activity = requireActivity()
        binding = FragmentNewParkingBinding.inflate(inflater, container, false)
        database = ParkingDatabase.getDatabase(activity)
        thread { database.parkingDataDao().deleteOldData() }

        // Register the broadcast receiver
        val intentFilter = IntentFilter().apply {
            addAction(ParkingTracker.STARTED)
            addAction(ParkingTracker.STOPPED)
        }
        ContextCompat.registerReceiver(
            requireContext(), broadcastReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Gets the state of the parking tracker service from the tracker preferences
        requireContext().getSharedPreferences(ParkingTracker.PREFERENCES, MODE_PRIVATE)
            .let {
                val state = it.getString(ParkingTracker.PREFERENCE_STATE, ParkingTracker.STOPPED)!!
                // If parking is not confirmed, enables the loading overlay
                if (state == ParkingTracker.START_PENDING) makeLoading(View.VISIBLE)
                // If parking tracker is active, goes to ongoing parking fragment
                else if (state != ParkingTracker.STOPPED) {
                    findNavController().navigate(R.id.ongoingParkingFragment)
                    return binding.root
                }
            }

        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        loadPreferences()
        loadVehicles()

        // If location is turned on and permission is given, locate zone on start
        if (Permissions.hasLocation(requireContext()) && isLocationEnabled()) {
            binding.locationDetector.isChecked = true
            locateZone()
        }
        binding.locationDetector.setOnClickListener {
            if (binding.locationDetector.isChecked) locateZone()
        }

        binding.startParking.setOnClickListener {
            binding.startParking.isEnabled = false
            if (Permissions.hasSms(requireContext())) {
                startParking()
            } else requestSmsPermission()
        }

        // Prevent going back to ongoingParkingFragment after finished parking
        activity.onBackPressedDispatcher.addCallback(this) { activity.finish() }

        return binding.root
    }

    override fun onDestroy() {
        requireActivity().unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    // Loads in the vehicles from the database to the vehicle list
    private fun loadVehicles() {
        thread {
            // Fill up the vehicle list, clear the views
            vehicles = database.vehicleDao().getAll()
            val vehicleRows = mutableListOf<VehicleListItemBinding>()

            // Select the vehicle from last time
            if (selectedVehicle == null && vehicles.isNotEmpty()) {
                selectedVehicle = vehicles.find { v -> v.plate == lastSelectedVehiclePlate }
            }

            activity.runOnUiThread {
                // Places the selected vehicle on top and highlights it
                selectedVehicle?.let { vehicle ->
                    val vehicleBinding = getVehicleListItem(vehicle)
                    vehicleBinding.selector.isEnabled = false
                    vehicleBinding.background.strokeColor =
                        ContextCompat.getColor(requireContext(), R.color.powderBlue)
                    vehicleRows.add(vehicleBinding)
                }

                // Places the rest of the vehicles after each other
                vehicles.forEach { vehicle ->
                    if (vehicle != selectedVehicle) {
                        vehicleRows.add(getVehicleListItem(vehicle))
                    }
                }

                // Creates the add new vehicle option
                val newVehicleBinding = VehicleListItemBinding.inflate(layoutInflater)
                newVehicleBinding.image.setImageResource(R.drawable.add_circle)
                newVehicleBinding.textView.text = getString(R.string.new_vehicle)
                newVehicleBinding.deleteButton.visibility = View.GONE
                newVehicleBinding.selector.setOnClickListener { newVehicle() }

                // Removes the old views and places in the new ones
                binding.vehicleList.removeAllViews()
                vehicleRows.forEach { row ->
                    binding.vehicleList.addView(row.root)
                }
                binding.vehicleList.addView(newVehicleBinding.root)
            }

            // Brings the list to top
            binding.vehicleListScroller.scrollTo(0, 0)
        }
    }

    // Creates the view for the vehicle list element
    private fun getVehicleListItem(vehicle: Vehicle): VehicleListItemBinding {
        val vehicleBinding = VehicleListItemBinding.inflate(layoutInflater)

        vehicleBinding.textView.text = Vehicle.getPlateDisplayFormat(vehicle.plate)
        vehicleBinding.image.setImageResource(Vehicle.VehicleType.getIcon(vehicle.type))
        vehicleBinding.selector.setOnClickListener {
            selectedVehicle = vehicle
            loadVehicles()
        }
        vehicleBinding.deleteButton.setOnClickListener { removeVehicle(vehicle) }

        return vehicleBinding
    }

    // Opens the new vehicle dialog
    private fun newVehicle() {
        savePreferences()
        try {
            findNavController().navigate(R.id.action_newParkingFragment_to_newVehicleFragment)
        } catch (_: Exception) {
        }
    }

    // Removes the selected vehicle from the database and refreshes the vehicle list
    private fun removeVehicle(vehicle: Vehicle) {
        try {
            findNavController().navigate(
                R.id.action_newParkingFragment_to_alertDialogFragment,
                Bundle().apply {
                    putString(
                        AlertDialogFragment.DISPLAY_MESSAGE,
                        getString(R.string.delete_vehicle)
                    )
                    putString(AlertDialogFragment.POSITIVE_BUTTON, getString(R.string.ok))
                    putString(AlertDialogFragment.NEGATIVE_BUTTON, getString(R.string.cancel))
                }
            )
            AlertDialogFragment.callback = { result ->
                if (result == AlertDialogFragment.Companion.AlertResponse.Confirmed) {
                    thread {
                        database.vehicleDao().deleteItem(vehicle)
                        if (selectedVehicle == vehicle) selectedVehicle = null
                        loadVehicles()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // Displays a prompt to get sms view and receive permission. If allowed, starts parking
    private fun requestSmsPermission() {
        findNavController().navigate(
            R.id.action_newParkingFragment_to_alertDialogFragment,
            Bundle().apply {
                putString(
                    AlertDialogFragment.DISPLAY_MESSAGE,
                    getString(R.string.sms_permission_request)
                )
                putString(AlertDialogFragment.POSITIVE_BUTTON, getString(R.string.ok))
            }
        )
        AlertDialogFragment.callback = { _ ->
            permissions.requestPermission(Manifest.permission.RECEIVE_SMS) { allowed ->
                if (allowed) {
                    permissions.requestPermission(Manifest.permission.SEND_SMS) { enable ->
                        if (enable) startParking()
                        else binding.startParking.isEnabled = true
                    }
                } else binding.startParking.isEnabled = true
            }
        }
    }

    // Loads the fragment preferences from the shared preferences
    private fun loadPreferences() {
        preferences = activity.getPreferences(MODE_PRIVATE)
        binding.prefixField.setText(preferences.getString("prefix", ""))
        binding.zoneField.setText(preferences.getString("zone", ""))
        lastSelectedVehiclePlate = preferences.getString("plate", null)
    }

    // Saves the fragment preferences into the shared preferences
    private fun savePreferences() {
        preferences.edit().apply {
            putString("prefix", binding.prefixField.text.toString())
            putString("zone", binding.zoneField.text.toString())
            putString("plate", selectedVehicle?.plate)
            apply()
        }
    }

    // Displays the start parking confirmation dialog. If confirmed, starts the parking process.
    private fun startParking() {
        if (!passedRequirements()) {
            binding.startParking.isEnabled = true
            return
        }

        savePreferences()

        findNavController().navigate(
            R.id.action_newParkingFragment_to_alertDialogFragment,
            Bundle().apply {
                putString(
                    AlertDialogFragment.DISPLAY_MESSAGE,
                    getString(
                        R.string.confirm_details,
                        Vehicle.getPlateDisplayFormat(selectedVehicle!!.plate),
                        binding.zoneField.text
                    )
                )
                putString(AlertDialogFragment.NEGATIVE_BUTTON, getString(R.string.cancel))
                putString(AlertDialogFragment.POSITIVE_BUTTON, getString(R.string.ok))
            }
        )
        AlertDialogFragment.callback = this::parkingConfirmed
    }

    // Returns true if all requirements are met to start parking
    private fun passedRequirements(): Boolean {
        return if (binding.prefixField.text.length != 5) {
            binding.prefixField.requestFocus()
            ErrorDisplay.showMessage(getString(R.string.define_mobile), activity, requireView())
            false
        } else if (binding.zoneField.text.length != 4) {
            binding.zoneField.requestFocus()
            ErrorDisplay.showMessage(getString(R.string.define_zone), activity, requireView())
            false
        } else if (selectedVehicle == null) {
            ErrorDisplay.showMessage(getString(R.string.select_vehicle), activity, requireView())
            false
        } else {
            true
        }
    }

    // Starts the parking process
    private fun parkingConfirmed(response: AlertDialogFragment.Companion.AlertResponse) {
        if (response != AlertDialogFragment.Companion.AlertResponse.Confirmed) {
            binding.startParking.isEnabled = true
            return
        }

        // If app has bluetooth permission, but it is turned off, requests user to turn on bluetooth
        if (Permissions.hasBluetooth(requireContext())) {
            val bluetoothManager = requireContext().getSystemService(BluetoothManager::class.java)
            if (!bluetoothManager.adapter.isEnabled) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }

        saveLocationEntry()
        startParkingTracker()
        makeLoading(View.VISIBLE)
    }

    // Starts the parking tracker service with the given prefix, zone and vehicle
    private fun startParkingTracker() {
        val intent = Intent(requireActivity(), ParkingTracker::class.java)
        intent.putExtra(
            ParkingTracker.START_PARAM_PHONE,
            "${getString(R.string.countryCode)}${binding.prefixField.text}${binding.zoneField.text}"
        )
        intent.putExtra(ParkingTracker.START_PARAM_PLATE, selectedVehicle!!.plate)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(intent)
        } else {
            requireActivity().startService(intent)
        }
    }

    // Enables or disables the loading overlay
    private fun makeLoading(visibility: Int) {
        binding.inputBlocker.visibility = visibility
        binding.progressBar.visibility = visibility
    }

    // Gets the nearest saved parking zone based on location and previously saved data
    @SuppressLint("MissingPermission")
    private fun locateZone() {
        if (!Permissions.hasLocation(requireContext())) {
            sendLocationAccessRequest()
            binding.locationDetector.isChecked = false
            return
        } else if (!isLocationEnabled()) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            binding.locationDetector.isChecked = false
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply {
            setMinUpdateDistanceMeters(5F)
            setMinUpdateIntervalMillis(2000)
        }.build()

        // Inform the user that location check is in progress
        binding.locationProgressBar.visibility = View.VISIBLE
        LocationServices.getFusedLocationProviderClient(requireActivity())
            .requestLocationUpdates(locationRequest, object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    LocationServices.getFusedLocationProviderClient(requireActivity())
                        .removeLocationUpdates(this)
                    // If a valid location is received, find nearest zone
                    if (locationResult.locations.size > 0) {
                        startLocation = locationResult.locations[locationResult.locations.size - 1]
                        val latitude = startLocation!!.latitude
                        val longitude = startLocation!!.longitude
                        thread {
                            // Fill in the zone edit text if a location is found (locations not empty)
                            database.zoneDataDao().find(latitude, longitude)?.let {
                                requireActivity().runOnUiThread {
                                    binding.zoneField.setText(it)
                                }
                                suggestedZone = it
                            }
                        }
                        binding.locationProgressBar.visibility = View.GONE
                    }
                }
            }, Looper.getMainLooper())
    }

    // Displays the location permission dialog. If allowed, turns on automatic zone location
    private fun sendLocationAccessRequest() {
        findNavController().navigate(
            R.id.action_newParkingFragment_to_alertDialogFragment,
            Bundle().apply {
                putString(
                    AlertDialogFragment.DISPLAY_MESSAGE,
                    getString(R.string.location_permission_request)
                )
                putString(AlertDialogFragment.POSITIVE_BUTTON, getString(R.string.ok))
            }
        )
        // Request both fine and coarse location permissions
        AlertDialogFragment.callback = { _ ->
            permissions.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION) { allow ->
                if (allow) {
                    permissions.requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION) {
                        if (it) {
                            binding.locationDetector.isChecked = true
                            locateZone()
                        }
                    }
                }
            }
        }
    }

    // Returns true if the location service is turned on
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            requireContext().getSystemService(LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    // Saves the new location into the database if the suggested zone was different from the actual
    private fun saveLocationEntry() {
        if (!binding.locationDetector.isChecked) return
        if (!binding.zoneField.equals(suggestedZone)) {
            startLocation?.let {
                thread {
                    database.zoneDataDao().insert(
                        ZoneData(
                            null,
                            binding.zoneField.text.toString(),
                            it.latitude,
                            it.longitude
                        )
                    )
                }
            }
        }
    }
}