package hu.kpark

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

object ErrorDisplay {
    // Displays an error message in the bottom of the screen
    fun showMessage(message: String, context: Context, view: View) {
        Snackbar.make(
            context, view, message, Snackbar.LENGTH_LONG
        ).setBackgroundTint(
            ContextCompat.getColor(context, R.color.blue)
        ).setTextColor(
            ContextCompat.getColor(context, R.color.white)
        ).show()
    }
}