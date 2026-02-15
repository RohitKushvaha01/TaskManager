package com.rk.taskmanager.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.category.PreferenceCategory
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.strings
import kotlinx.coroutines.DelicateCoroutinesApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path


@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    DelicateCoroutinesApi::class
)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, navController: NavController) {

    PreferenceLayout(
        modifier = modifier,
        label = stringResource(strings.settings),
    ) {


        PreferenceCategory(
            label = "Daemon",
            description = "TaskManagerD settings",
            startWidget = {
                Icon(imageVector = Icons.Outlined.Android,null)
            },
            onNavigate = {
                navController.navigate(SettingsRoutes.Daemon.route)
                         },
        )
        PreferenceCategory(
            label = "Graph",
            description = "cpu,ram,swap graph settings",
            startWidget = {
                Icon(imageVector = Icons.Outlined.MonitorHeart,null)
            },
            onNavigate = {
                navController.navigate(SettingsRoutes.Graphs.route)
            },
        )


        PreferenceCategory(
            label = "Processes",
            description = "settings for processes screen",
            startWidget = {
                Icon(imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,null)
            },
            onNavigate = {
                navController.navigate(SettingsRoutes.Procs.route)
            },
        )

        PreferenceCategory(
            label = "Themes",
            description = "look and feel",
            startWidget = {
                Icon(imageVector = Icons.Outlined.ColorLens,null)
            },
            onNavigate = {
                navController.navigate(SettingsRoutes.Themes.route)
            },
        )

        PreferenceCategory(
            label = "Support",
            description = "support development",
            startWidget = {
                Icon(imageVector = FeatherHeart,null)
            },
            onNavigate = {
                navController.navigate(SettingsRoutes.Support.route)
            },
        )

        PreferenceCategory(
            label = "About",
            description = "Application info",
            startWidget = {
                Icon(imageVector = Icons.Outlined.Info,null)
            },
            onNavigate = {
                navController.navigate(SettingsRoutes.About.route)
            },
        )

    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableCard(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    description: String? = null,
    onClick: () -> Unit,
    isEnaled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    PreferenceTemplate(
        modifier = modifier.combinedClickable(
            enabled = isEnaled,
            indication = ripple(),
            interactionSource = interactionSource,
            onClick = onClick
        ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(fontWeight = FontWeight.Bold, text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        description = { description?.let { Text(it) } },
        enabled = isEnaled,
        applyPaddings = false,
        endWidget = null,
        startWidget = {
            RadioButton(selected = selected, onClick = onClick, modifier = Modifier.padding(start = 8.dp))
        }
    )
}


val FeatherHeart: ImageVector
    get() {
        if (_FeatherHeart != null) return _FeatherHeart!!

        _FeatherHeart = ImageVector.Builder(
            name = "heart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.84f, 4.61f)
                arcToRelative(5.5f, 5.5f, 0f, false, false, -7.78f, 0f)
                lineTo(12f, 5.67f)
                lineToRelative(-1.06f, -1.06f)
                arcToRelative(5.5f, 5.5f, 0f, false, false, -7.78f, 7.78f)
                lineToRelative(1.06f, 1.06f)
                lineTo(12f, 21.23f)
                lineToRelative(7.78f, -7.78f)
                lineToRelative(1.06f, -1.06f)
                arcToRelative(5.5f, 5.5f, 0f, false, false, 0f, -7.78f)
                close()
            }
        }.build()

        return _FeatherHeart!!
    }

private var _FeatherHeart: ImageVector? = null