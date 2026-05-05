package com.rk.taskmanager.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.TextStyle
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

// ── Palette ──────────────────────────────────────────────────────────────────

private val Purple50  = Color(0xFFEEEDFE)
private val Purple600 = Color(0xFF534AB7)
private val Purple700 = Color(0xFF4840A0)
private val Purple300 = Color(0xFF7F77DD)
private val Purple200 = Color(0xFFAFA9EC)

private val Teal50    = Color(0xFFE1F5EE)
private val Teal700   = Color(0xFF0F6E56)

private val Blue50    = Color(0xFFE6F1FB)
private val Blue700   = Color(0xFF185FA5)

private val Amber50   = Color(0xFFFAEEDA)
private val Amber700  = Color(0xFF854F0B)

private val Coral50   = Color(0xFFFAECE7)
private val Coral700  = Color(0xFF993C1D)

private val Green50  = Color(0xFFEAF3DE)
private val Green700 = Color(0xFF3B6D11)

// ── Data ─────────────────────────────────────────────────────────────────────

private data class ProFeature(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color
)

@Composable
private fun getFeatures() = listOf(
    ProFeature(
        titleRes = strings.battery_stats,
        descriptionRes = strings.battery_stats_desc,
        icon = Icons.Outlined.BatteryChargingFull,
        iconTint = Green700,
        iconBackground = Green50
    ),
    ProFeature(
        titleRes = strings.network_monitor,
        descriptionRes = strings.network_monitor_desc,
        icon = Icons.Outlined.NetworkCheck,
        iconTint = Blue700,
        iconBackground = Blue50
    ),
    ProFeature(
        titleRes = strings.disk_monitor,
        descriptionRes = strings.disk_monitor_desc,
        icon = Icons.Outlined.Storage,
        iconTint = Purple700,
        iconBackground = Purple50
    ),
    ProFeature(
        titleRes = strings.process_pin,
        descriptionRes = strings.process_pin_desc,
        icon = Icons.Outlined.PushPin,
        iconTint = Teal700,
        iconBackground = Teal50
    ),
)


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
            delay(3000)
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
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = "mailto:".toUri()
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("kushvahar173+taskmanager@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "Help Needed")
                                putExtra(Intent.EXTRA_TEXT, "Describe your issue here...")
                            }

                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),

                    ) {
                        Text(stringResource(strings.help), fontSize = 12.sp)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Hero banner ───────────────────────────────────────────────
            HeroBanner()

            Spacer(modifier = Modifier.height(4.dp))

            // ── Section label ─────────────────────────────────────────────
            Text(
                text = stringResource(strings.what_you_get),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Feature rows ──────────────────────────────────────────────
            getFeatures().forEach { feature ->
                FeatureRow(feature = feature)
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                Text(
                    text = stringResource(strings.secure_payment),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ── Hero Banner ───────────────────────────────────────────────────────────────

@Composable
private fun HeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Purple600, Purple300, Purple200)
                )
            )
            .padding(24.dp)
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-20).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(70.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-30).dp, y = 20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(strings.task_manager_uppercase),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = stringResource(strings.pro_version),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(strings.pro_version_tagline),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}

// ── Feature Row ───────────────────────────────────────────────────────────────

@Composable
private fun FeatureRow(feature: ProFeature) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon pill
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(feature.iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = null,
                    tint = feature.iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(feature.titleRes),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
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
}

// ── Purchase Card ─────────────────────────────────────────────────────────────

@Composable
private fun PurchaseCard(
    price: String?,
    isPending: Boolean,
    enabled: Boolean,
    onPurchase: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
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
                Column {
                    Text(
                        text = stringResource(strings.one_time_purchase),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isPending) {
                        Text(
                            text = stringResource(strings.purchase_in_progress),
                            fontSize = 12.sp,
                            color = Amber700,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (price != null) {
                    Text(
                        text = price,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Purple600
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Purple600
                    )
                }
            }

            Text(
                text = stringResource(strings.permanent_pro_access),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onPurchase,
                enabled = enabled && price != null && !isPending,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Purple600,
                    contentColor = Color.White,
                    disabledContainerColor = Purple200,
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isPending) stringResource(strings.processing) else stringResource(strings.upgrade_now),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Unlocked Card ─────────────────────────────────────────────────────────────

@Composable
private fun UnlockedCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
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
                    .background(Teal50),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Teal700,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = stringResource(strings.pro_unlocked),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(strings.thanks_support),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
