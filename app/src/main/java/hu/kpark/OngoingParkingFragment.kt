package hu.kpark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.navigation.fragment.findNavController
import hu.kpark.data.Vehicle
import hu.kpark.databinding.FragmentOngoingParkingBinding
import hu.kpark.trackingService.ParkingTracker

class OngoingParkingFragment : Fragment() {
    private lateinit var binding: FragmentOngoingParkingBinding

    // Registered broadcast receiver for navigating back to new parking if the parking was stopped
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ParkingTracker.STOPPED) {
                findNavController().navigate(R.id.newParkingFragment)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentOngoingParkingBinding.inflate(layoutInflater, container, false)

        // Disable going back to previous fragments, application closes instead
        requireActivity().onBackPressedDispatcher.addCallback(this) {}

        // Load in UI parameters and text
        // If service didn't start (after restart) before fragment load, UI waits for service start
        if (ParkingTracker.instance == null) {
            ParkingTracker.startCallback = { requireActivity().runOnUiThread { fillUI() } }
            val intent = Intent(requireActivity(), ParkingTracker::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireActivity().startForegroundService(intent)
            } else {
                requireActivity().startService(intent)
            }
        }
        else fillUI()

        // Register broadcast receiver
        val intentFilter = IntentFilter().apply { addAction(ParkingTracker.STOPPED) }
        ContextCompat.registerReceiver(
            requireContext(),
            broadcastReceiver,
            intentFilter,
            RECEIVER_NOT_EXPORTED
        )

        // Enable loading overlay if parking stop is pending and waiting for confirmation
        requireContext().getSharedPreferences(ParkingTracker.PREFERENCES, Context.MODE_PRIVATE)
            .let {
                if (it.getString(
                        ParkingTracker.PREFERENCE_STATE, ParkingTracker.STARTED
                    )!! == ParkingTracker.STOP_PENDING
                ) enableLoading()
            }

        return binding.root
    }

    override fun onDestroy() {
        requireActivity().unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    // Fills UI with ongoing parking details
    private fun fillUI() {
        binding.stopParking.setOnClickListener {
            binding.stopParking.isEnabled = false
            showStopConfirmationDialog()
        }

        binding.vehicleText.text =
            getString(
                R.string.ongoing_plate_text,
                Vehicle.getPlateDisplayFormat(ParkingTracker.instance!!.parkingData.vehiclePlate)
            )
        binding.zoneText.text =
            getString(
                R.string.ongoing_zone_text,
                ParkingTracker.instance?.parkingData?.phoneNumber?.takeLast(4)
            )
    }

    // Displays the stop parking dialog. If confirmed, stops parking
    private fun showStopConfirmationDialog() {
        findNavController().navigate(
            R.id.action_ongoingParkingFragment_to_alertDialogFragment,
            Bundle().apply {
                putString(
                    AlertDialogFragment.DISPLAY_MESSAGE,
                    getString(R.string.confirm_end_parking)
                )
                putString(AlertDialogFragment.POSITIVE_BUTTON, getString(R.string.ok))
                putString(AlertDialogFragment.NEGATIVE_BUTTON, getString(R.string.cancel))
            })
        AlertDialogFragment.callback = this::stopParkingConfirmed
    }

    // Stops the parking service manually
    private fun stopParkingConfirmed(response: AlertDialogFragment.Companion.AlertResponse) {
        if (response != AlertDialogFragment.Companion.AlertResponse.Confirmed) {
            binding.stopParking.isEnabled = true
            return
        }

        ParkingTracker.manualStop()
        enableLoading()
    }

    // Displays the loading overlay
    private fun enableLoading() {
        binding.inputBlocker.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
    }
}