package hu.kpark

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class Permissions(fragment: Fragment) {
    private lateinit var callback: ((result: Boolean) -> Unit)

    // Launches the permission request
    private val permissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            callback.invoke(allowed)
        }

    // Requests given permission from the user, if permission is granted calls the callback
    fun requestPermission(permission: String, callback: (result: Boolean) -> Unit) {
        this.callback = callback
        permissionLauncher.launch(permission)
    }

    // Contains methods for checking application permissions
    companion object {
        fun hasBluetooth(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                bluetoothPermissionString
            ) == PackageManager.PERMISSION_GRANTED
        }

        val bluetoothPermissionString: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT
            else Manifest.permission.BLUETOOTH

        fun hasSms(context: Context): Boolean = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        fun hasLocation(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}