package dev.kern.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.kern.browser.FileScanner

/**
 * Tracks whether Kern currently holds storage / all-files access, re-checking on
 * resume so the value updates after the user returns from the system settings
 * page, and returns a request function that launches the right grant flow for
 * the API level (MANAGE_EXTERNAL_STORAGE on 30+, READ_EXTERNAL_STORAGE below).
 *
 * @return a pair of (granted, request); call request() to prompt when missing.
 */
@Composable
internal fun rememberStorageAccess(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(FileScanner.hasAccess(context)) }

    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { granted = FileScanner.hasAccess(context) }
    val readLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = FileScanner.hasAccess(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = FileScanner.hasAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val request: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { manageLauncher.launch(intent) }.onFailure {
                manageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            readLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    return granted to request
}
