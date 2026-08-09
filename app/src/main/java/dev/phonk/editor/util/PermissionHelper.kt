package dev.phonk.editor.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Small permission helper. The app only needs READ_EXTERNAL_STORAGE on
 * API <= 28 for picking old media; on 29+ pickers use SAF and rendering writes
 * through MediaStore, so no WRITE permission is requested anywhere.
 */
object PermissionHelper {

    fun hasReadStorage(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    fun requestReadStorage(activity: Activity, requestCode: Int) {
        if (!hasReadStorage(activity)) {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                ),
                requestCode,
            )
        }
    }
}