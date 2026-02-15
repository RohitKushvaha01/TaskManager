package com.rk.taskmanager.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.taskmanager.R

@Composable
fun SupportDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onSupportClick: () -> Unit
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enjoying the App? ❤️",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "You've stopped ${Settings.kills} apps using this tool.\n\n" +
                        "It's free, open-source, and ad-free. " +
                        "If it’s been helpful, consider supporting its development."
            )
        },
        confirmButton = {
            TextButton(onClick = onSupportClick) {
                Text("Support")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}

@Composable
fun SupportSettingsScreen(modifier: Modifier = Modifier) {
    PreferenceLayout(label = "Support", backArrowVisible = true) {
        val context = LocalContext.current

        PreferenceGroup {
            SettingsToggle(
                label = "GitHub Sponsors",
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(R.drawable.github),
                        contentDescription = null,
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                sideEffect = {
                    val url = "https://github.com/sponsors/RohitKushvaha01"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = url.toUri() }
                    context.startActivity(intent)
                },
            )
            SettingsToggle(
                label = "Buy Me a Coffee",
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(R.drawable.coffee),
                        contentDescription = null,
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                sideEffect = {
                    val url = "https://buymeacoffee.com/rohitkushvaha01"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                    context.startActivity(intent)
                },
            )
            val upiAvailable = remember { isUPISupported(context) }
            if (upiAvailable) {
                SettingsToggle(
                    label = "UPI",
                    isEnabled = true,
                    showSwitch = false,
                    default = false,
                    startWidget = {
                        Icon(
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            painter = painterResource(R.drawable.upi_pay),
                            contentDescription = null,
                        )
                    },
                    endWidget = {
                        Icon(
                            modifier = Modifier.padding(16.dp),
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    sideEffect = {
                        val uri =
                            "upi://pay"
                                .toUri()
                                .buildUpon()
                                .appendQueryParameter("pa", "rohitkushvaha01@axl")
                                .appendQueryParameter("pn", "Rohit Kushwaha")
                                .appendQueryParameter("tn", "Xed-Editor")
                                .appendQueryParameter("cu", "INR")
                                .build()
                        val intent = Intent(Intent.ACTION_VIEW).apply { data = uri }

                        val chooser = Intent.createChooser(intent, "Use")
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(chooser)
                        } else {
                            Toast.makeText(context, "No upi app available", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}



private fun isUPISupported(context: Context): Boolean {
    // 1. Check if the user's region is India (Most reliable indicator for UPI)
    val currentLocale = context.resources.configuration.locales[0]
    val isIndia = currentLocale.country.equals("IN", ignoreCase = true)

    // 2. Check if there is at least one app capable of handling a UPI URI
    val uri = "upi://pay".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val packageManager = context.packageManager

    // Check if any app can resolve this intent
    val canHandleUPI =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
                .isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        }

    return isIndia || canHandleUPI
}