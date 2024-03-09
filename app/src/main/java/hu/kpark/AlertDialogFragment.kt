package hu.kpark

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import hu.kpark.databinding.FragmentDialogAlertBinding

class AlertDialogFragment : DialogFragment() {
    private lateinit var binding: FragmentDialogAlertBinding

    private var displayMessage: String? = null
    private var positiveButtonMessage: String? = null
    private var negativeButtonMessage: String? = null

    private var response: AlertResponse = AlertResponse.Ignored

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            displayMessage = it.getString(DISPLAY_MESSAGE)
            positiveButtonMessage = it.getString(POSITIVE_BUTTON)
            negativeButtonMessage = it.getString(NEGATIVE_BUTTON)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentDialogAlertBinding.inflate(layoutInflater)
        response = AlertResponse.Ignored

        binding.message.text = displayMessage ?: ""

        setUpPositiveButton()
        setUpNegativeButton()

        return AlertDialog.Builder(requireContext()).setView(binding.root).create()
    }

    override fun onCreateView(i: LayoutInflater, v: ViewGroup?, b: Bundle?): View = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        callback?.invoke(response)
        callback = null
    }

    private fun setUpPositiveButton() {
        if (positiveButtonMessage == null) binding.positiveButton.visibility = View.GONE
        else {
            binding.positiveButton.text = positiveButtonMessage
            binding.positiveButton.setOnClickListener {
                response = AlertResponse.Confirmed
                findNavController().popBackStack()
            }
        }
    }

    private fun setUpNegativeButton() {
        if (negativeButtonMessage == null) binding.negativeButton.visibility = View.GONE
        else {
            binding.negativeButton.text = negativeButtonMessage
            binding.negativeButton.setOnClickListener {
                response = AlertResponse.Cancelled
                findNavController().popBackStack()
            }
        }
    }

    companion object {
        const val DISPLAY_MESSAGE = "displayMessage"
        const val POSITIVE_BUTTON = "positiveButton"
        const val NEGATIVE_BUTTON = "negativeButton"

        enum class AlertResponse {
            Confirmed, Cancelled, Ignored
        }

        // Callback will be called once if the dialog is closed, then set to null
        var callback: ((AlertResponse) -> Unit)? = null
    }
}