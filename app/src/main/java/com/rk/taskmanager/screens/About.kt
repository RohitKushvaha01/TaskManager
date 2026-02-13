package com.rk.taskmanager.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.taskmanager.BuildConfig
import com.rk.taskmanager.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun About(modifier: Modifier = Modifier) {
    val packageInfo = LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val context = LocalContext.current

    PreferenceLayout(label = "About", backArrowVisible = true) {
        PreferenceGroup(heading = "Developer") {
            SettingsToggle(
                label = "RohitKushvaha01",
                description = "Vie github profile",
                default = false,
                sideEffect = {
                    val url = "https://github.com/RohitKushvaha01"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                    context.startActivity(intent)
                },
                showSwitch = false,
                startWidget = {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(LocalContext.current)
                                .data("https://github.com/RohitKushvaha01.png")
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                        contentDescription = "GitHub Avatar",
                        modifier = Modifier.padding(start = 16.dp).size(26.dp).clip(CircleShape),
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
            )
        }

        PreferenceGroup(heading = "Build Info") {
            PreferenceTemplate(
                modifier =
                    Modifier.combinedClickable(
                        enabled = true,
                        onClick = {},
                        onLongClick = { copyToClipboard(context,versionName.toString()) },
                    ),
                title = {
                    Text(text = "Version", style = MaterialTheme.typography.titleMedium)
                },
                description = { Text(text = versionName.toString(), style = MaterialTheme.typography.titleSmall) },
            )

            PreferenceTemplate(
                modifier =
                    Modifier.combinedClickable(
                        enabled = true,
                        onClick = {},
                        onLongClick = { copyToClipboard(context,versionCode.toString()) },
                    ),
                title = {
                    Text(text = "Version Code", style = MaterialTheme.typography.titleMedium)
                },
                description = { Text(text = versionCode.toString(), style = MaterialTheme.typography.titleSmall) },
            )

            PreferenceTemplate(
                modifier =
                    Modifier.combinedClickable(
                        enabled = true,
                        onClick = {},
                        onLongClick = { copyToClipboard(context,BuildConfig.GIT_SHORT_COMMIT_HASH) },
                    ),
                title = {
                    Text(text = "Git Commit hash", style = MaterialTheme.typography.titleMedium)
                },
                description = {
                    Text(text = BuildConfig.GIT_SHORT_COMMIT_HASH, style = MaterialTheme.typography.titleSmall)
                },
            )
        }
    }
}


private fun copyToClipboard(context: Context,text: String, showToast: Boolean = true) {
    copyToClipboard(context,label = "Xed-Editor", text, showToast = showToast)
}


private fun copyToClipboard(context: Context,label: String, text: String, showToast: Boolean = true) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    if (showToast) {
        Toast.makeText(context, "Copied on clipboard", Toast.LENGTH_SHORT).show()
    }
}
