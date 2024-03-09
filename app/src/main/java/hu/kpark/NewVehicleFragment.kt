package hu.kpark

import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import hu.kpark.data.ParkingDatabase
import hu.kpark.data.Vehicle
import hu.kpark.databinding.FragmentNewVehicleBinding
import kotlin.concurrent.thread

class NewVehicleFragment : DialogFragment() {
    private lateinit var binding: FragmentNewVehicleBinding
    private val permissions = Permissions(this)

    // List of device names and mac addresses
    private var deviceData = mutableListOf<Pair<String, String>>()

    // Prompt for turning bluetooth on if you want to turn on identification
    private val bluetoothEnableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        {
            val bluetoothAdapter =
                requireContext().getSystemService(BluetoothManager::class.java).adapter

            if (bluetoothAdapter.isEnabled) turnOnBluetoothIdentification()
            else turnOffBluetoothIdentification()
        }

    override fun onCreateView(i: LayoutInflater, v: ViewGroup?, b: Bundle?): View = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentNewVehicleBinding.inflate(layoutInflater)

        fillVehicleTypes()

        binding.confirmButton.setOnClickListener {
            saveVehicle()
            binding.confirmButton.isEnabled = false
        }

        binding.identificationSpinner.visibility = View.GONE
        binding.identificationToggle.setOnClickListener {
            if (binding.identificationToggle.isChecked) turnOnBluetoothIdentification()
            else turnOffBluetoothIdentification()
        }

        return AlertDialog.Builder(requireContext()).setView(binding.root).create()
    }

    private fun saveVehicle() {
        thread {
            // Check if vehicle plate is valid
            val plate = binding.plateField.text.toString()
            if (!Vehicle.isPlateValid(plate)) {
                requireActivity().runOnUiThread {
                    binding.plateField.requestFocus()
                    ErrorDisplay.showMessage(
                        getString(R.string.plate_format_invalid), requireContext(), requireView()
                    )
                    binding.confirmButton.isEnabled = true
                }
                return@thread
            }

            val database = ParkingDatabase.getDatabase(requireActivity())

            // Check if vehicle plate is unique
            if (database.vehicleDao().find(plate.lowercase()) != null) {
                requireActivity().runOnUiThread {
                    ErrorDisplay.showMessage(
                        getString(R.string.plate_already_exists), requireContext(), requireView()
                    )
                    binding.confirmButton.isEnabled = true
                }
                return@thread
            }

            val btAddress = binding.identificationSpinner.selectedItem?.let {
                getBluetoothMacAddress(it.toString())
            }

            // Save new vehicle to database
            database.vehicleDao().insert(
                Vehicle(
                    null,
                    plate.lowercase(),
                    btAddress,
                    Vehicle.VehicleType.getByOrdinal(binding.typeSpinner.selectedItemPosition)
                        ?: Vehicle.VehicleType.Car
                )
            )

            requireActivity().runOnUiThread {
                findNavController().navigate(R.id.action_newVehicleFragment_to_newParkingFragment)
            }
        }
    }

    private fun getBluetoothMacAddress(selectedName: String): String? {
        return deviceData.find { (name, _) -> name == selectedName }?.second
    }

    // Fills the vehicle type spinner with the vehicle type enum values
    private fun fillVehicleTypes() {
        val displayArray = mutableListOf<String>()
        Vehicle.VehicleType.values().forEach {
            displayArray.add(getString(it.stringResource))
        }

        binding.typeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            displayArray
        )
        binding.typeSpinner.setPopupBackgroundResource(R.color.blue)
    }

    private fun turnOnBluetoothIdentification() {
        if (!Permissions.hasBluetooth(requireContext())) {
            requestBluetoothPermission()
            return
        }

        val devices = getBluetoothDevices()
        if (devices != null) {
            fillDeviceData(devices)
            fillDeviceNames()
            binding.identificationToggle.isChecked = true
            binding.identificationSpinner.visibility = View.VISIBLE
        } else {
            turnOffBluetoothIdentification()
        }
    }

    private fun turnOffBluetoothIdentification() {
        deviceData = mutableListOf()
        binding.identificationToggle.isChecked = false
        binding.identificationSpinner.visibility = View.GONE
    }

    @SuppressLint("MissingPermission")
    private fun getBluetoothDevices(): Set<BluetoothDevice>? {
        val bluetoothAdapter =
            requireContext().getSystemService(BluetoothManager::class.java).adapter ?: return null

        return if (!bluetoothAdapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(intent)
            null
        } else {
            bluetoothAdapter.bondedDevices
        }
    }

    // Fills the device data list with the device name and mac addresses
    @SuppressLint("MissingPermission")
    private fun fillDeviceData(devices: Set<BluetoothDevice>) {
        devices.forEach { device ->
            deviceData.add(Pair(device.name, device.address))
        }
    }

    // Fills the device identification list with the names of the devices
    private fun fillDeviceNames() {
        val deviceNames = mutableListOf<String>()
        deviceData.forEach { (name, _) ->
            deviceNames.add(name)
        }

        binding.identificationSpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, deviceNames
        )
        binding.identificationSpinner.setPopupBackgroundResource(R.color.blue)
    }

    // Displays the bluetooth permission dialog. If permission is given, turns on identification
    private fun requestBluetoothPermission() {
        if (Permissions.hasBluetooth(requireContext())) return

        findNavController().navigate(
            R.id.action_newVehicleFragment_to_alertDialogFragment,
            Bundle().apply {
                putString(
                    AlertDialogFragment.DISPLAY_MESSAGE,
                    getString(R.string.bluetooth_permission_request)
                )
                putString(
                    AlertDialogFragment.POSITIVE_BUTTON,
                    getString(R.string.ok)
                )
            }
        )
        AlertDialogFragment.callback = { _ ->
            permissions.requestPermission(Permissions.bluetoothPermissionString) { allowed ->
                if (allowed) turnOnBluetoothIdentification()
                else turnOffBluetoothIdentification()
            }
        }
    }
}