package com.flopster101.siliconplayer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal data class StoragePermissionState(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit
)

@Composable
internal fun rememberStoragePermissionState(context: Context): StoragePermissionState {
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = checkStoragePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasPermission = checkStoragePermission(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val requestStoragePermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 30) {
            val launchedAllFilesSettings = openAllFilesAccessSettings(context)
            if (!launchedAllFilesSettings) {
                storagePermissionLauncher.launch(storageRuntimePermissionsForCurrentApi())
            }
        } else {
            storagePermissionLauncher.launch(storageRuntimePermissionsForCurrentApi())
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    return StoragePermissionState(
        hasPermission = hasPermission,
        requestPermission = requestStoragePermission
    )
}

@Composable
internal fun StoragePermissionRequiredScreen(onRequestPermission: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTvDevice = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    val isWatchDevice = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    }
    val showsSettingsFlow = isTvDevice && Build.VERSION.SDK_INT >= 30
    val permissionBodyText = if (showsSettingsFlow) {
        "Grant file access so Silicon Player can scan and play your audio library. " +
            "On some Android TV versions, this opens App settings instead of a popup."
    } else if (isWatchDevice) {
        "Grant storage access to scan and play audio files."
    } else {
        "Grant file access so Silicon Player can scan and play your audio library."
    }
    val permissionHintText = if (showsSettingsFlow) {
        "If App settings opens, go to Permissions and allow Files and media (or All files access)."
    } else {
        null
    }
    val permissionButtonLabel = if (showsSettingsFlow) "Open permission settings" else "Grant permission"

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val isLandscape = maxWidth > maxHeight
        val isCompactScreen = isWatchDevice || maxWidth < 320.dp || maxHeight < 340.dp
        val cardMaxWidth = if (isLandscape) {
            (maxWidth * 0.62f).coerceIn(380.dp, 680.dp)
        } else {
            maxWidth
        }
        val outerHorizontalPadding = when {
            isWatchDevice -> 12.dp
            isLandscape -> 32.dp
            else -> 20.dp
        }
        val outerVerticalPadding = when {
            isWatchDevice -> 16.dp
            else -> 24.dp
        }
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                modifier = if (isLandscape) {
                    Modifier.widthIn(max = cardMaxWidth)
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(
                            horizontal = if (isCompactScreen) 14.dp else 20.dp,
                            vertical = if (isCompactScreen) 16.dp else 24.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val iconContainerSize = if (isCompactScreen) 48.dp else 84.dp
                    val iconSize = if (isCompactScreen) 26.dp else 42.dp
                    Box(
                        modifier = Modifier
                            .size(iconContainerSize)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(if (isCompactScreen) 8.dp else 18.dp))
                    Text(
                        text = "Storage access required",
                        style = if (isCompactScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(if (isCompactScreen) 4.dp else 8.dp))
                    Text(
                        text = permissionBodyText,
                        style = if (isCompactScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (permissionHintText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = permissionHintText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(if (isCompactScreen) 12.dp else 20.dp))
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = permissionButtonLabel,
                            style = if (isCompactScreen) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager() || hasRuntimeStorageReadPermission(context)
    } else {
        hasRuntimeStorageReadPermission(context)
    }
}

private fun storageRuntimePermissionsForCurrentApi(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

private fun hasRuntimeStorageReadPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        val hasRead = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasRead || hasWrite
    }
}

private fun openAllFilesAccessSettings(context: Context): Boolean {
    val packageUri = Uri.parse("package:${context.packageName}")
    val fallbackIntents = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = packageUri
        },
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = packageUri
        }
    )
    for (intent in fallbackIntents) {
        if (tryStartSettingsIntent(context, intent)) {
            return true
        }
    }
    return false
}

private fun tryStartSettingsIntent(context: Context, intent: Intent): Boolean {
    val launchIntent = Intent(intent).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(launchIntent)
        true
    } catch (_: Exception) {
        false
    }
}
