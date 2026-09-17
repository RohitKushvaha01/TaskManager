package com.rk.taskmanager.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.bridge.bridge
import com.rk.taskmanager.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import com.rk.commons.strings
import kotlin.time.Duration.Companion.milliseconds

// ── Data ─────────────────────────────────────────────────────────────────────

private data class ProFeature(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    val container: Color,
    val content: Color
)

@Composable
private fun getFeatures(): List<ProFeature> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        ProFeature(
            titleRes = strings.battery_stats,
            descriptionRes = strings.battery_stats_desc,
            icon = Icons.Outlined.BatteryChargingFull,
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        ),
        ProFeature(
            titleRes = strings.network_monitor,
            descriptionRes = strings.network_monitor_desc,
            icon = Icons.Outlined.NetworkCheck,
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer
        ),
        ProFeature(
            titleRes = strings.process_pin,
            descriptionRes = strings.process_pin_desc,
            icon = Icons.Outlined.PushPin,
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer
        ),
        ProFeature(
            titleRes = strings.usage_notif,
            descriptionRes = strings.usage_notif_Desc,
            icon = Icons.Outlined.Notifications,
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        ),
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProVersion(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    var price by remember { mutableStateOf<String?>(null) }

    val proState = remember { bridge?.isPro() } ?: remember { mutableStateOf(false) }
    val isPro by proState

    val pendingState = remember { bridge?.isPending() } ?: remember { mutableStateOf(false) }
    val isPending by pendingState
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        price = bridge?.getProVersionPrice()
    }

    // Poll for status updates when purchase is pending
    LaunchedEffect(isPending || isPro.not()) {
        while (isActive && (isPending || isPro.not())) {
            delay(3000.milliseconds)
            bridge?.updatePurchaseStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(strings.upgrade_to_pro),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "mailto:".toUri()
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("kushvahar173+taskmanager@gmail.com"))
                                    putExtra(Intent.EXTRA_SUBJECT, "Help Needed")
                                    putExtra(Intent.EXTRA_TEXT, "Describe your issue here...")
                                }

                                context.startActivity(intent)
                            }.onFailure {
                                it.printStackTrace()
                                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                            }

                        }
                    ) {
                        Text(stringResource(strings.help), fontSize = 13.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { activity?.onBackPressed() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(strings.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            HeroBanner()

            // ── Feature list ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(strings.what_you_get),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        getFeatures().forEach { feature ->
                            FeatureRow(feature = feature)
                        }
                    }
                }
            }

            // ── Purchase card or Unlocked card ────────────────────────────
            if (isPro) {
                UnlockedCard()
            } else {
                PurchaseCard(
                    price = price,
                    isPending = isPending,
                    enabled = activity != null && bridge != null,
                    onPurchase = {
                        MainActivity.scope!!.launch {
                            activity?.let { bridge?.launchPurchase(it) }
                        }
                    }
                )
            }

            // ── Footer note ───────────────────────────────────────────────
            if (!isPro) {
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(strings.secure_payment),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ── Hero Banner ───────────────────────────────────────────────────────────────

@Composable
private fun HeroBanner() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(scheme.primary, scheme.tertiary)
                )
            )
            .padding(24.dp)
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 36.dp, y = (-36).dp)
                .clip(CircleShape)
                .background(scheme.onPrimary.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-8).dp, y = 28.dp)
                .clip(CircleShape)
                .background(scheme.onPrimary.copy(alpha = 0.08f))
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(strings.task_manager_pro_uppercase),
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                color = scheme.onPrimary.copy(alpha = 0.85f)
            )
            Text(
                text = stringResource(strings.pro_version_tagline),
                fontSize = 13.sp,
                color = scheme.onPrimary.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}

// ── Feature Row ───────────────────────────────────────────────────────────────

@Composable
private fun FeatureRow(feature: ProFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon pill
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(feature.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = feature.content,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(feature.titleRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(feature.descriptionRes),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// ── Purchase Card ─────────────────────────────────────────────────────────────

@Composable
private fun PurchaseCard(
    price: String?,
    isPending: Boolean,
    enabled: Boolean,
    onPurchase: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(strings.one_time_purchase),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = stringResource(strings.permanent_pro_access),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant
                    )
                    if (isPending) {
                        Text(
                            text = stringResource(strings.purchase_in_progress),
                            fontSize = 12.sp,
                            color = scheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (price != null) {
                    Text(
                        text = price,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = scheme.primary
                    )
                }
            }

            Button(
                onClick = onPurchase,
                enabled = enabled && price != null && !isPending,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (isPending) stringResource(strings.processing) else stringResource(strings.upgrade_now),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Unlocked Card ─────────────────────────────────────────────────────────────

@Composable
private fun UnlockedCard() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(strings.pro_unlocked),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(strings.thanks_support),
                    fontSize = 12.sp,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}
