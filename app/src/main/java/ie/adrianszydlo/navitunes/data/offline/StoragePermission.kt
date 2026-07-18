package ie.adrianszydlo.navitunes.data.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Gatekeeper for writing offline downloads to the public Music/Navitunes folder.
 *
 * - API 30+ : MANAGE_EXTERNAL_STORAGE ("All files access"), granted via a system
 *             settings screen (not a normal runtime dialog).
 * - API ≤ 29: legacy WRITE_EXTERNAL_STORAGE, granted via the normal runtime
 *             permission dialog (paired with requestLegacyExternalStorage).
 */
object StoragePermission {

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    /** True if this device grants storage through the legacy runtime dialog. */
    val usesLegacyRuntimePermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    /** Settings intent that opens the All-Files-Access toggle for this app (API 30+). */
    fun allFilesAccessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
}
