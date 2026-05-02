package com.rk.taskmanager.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.bridge.bridge
import com.rk.taskmanager.MainActivity
import kotlinx.coroutines.launch

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
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color
)

private val features = listOf(
    ProFeature(
        title = "Battery Stats",
        description = "View charge level, status, and battery health at a glance",
        icon = Icons.Outlined.BatteryChargingFull,
        iconTint = Green700,
        iconBackground = Green50
    ),
    ProFeature(
        title = "Network Monitor",
        description = "Track data usage and speeds per process in real time",
        icon = Icons.Outlined.NetworkCheck,
        iconTint = Blue700,
        iconBackground = Blue50
    ),
    ProFeature(
        title = "Disk Monitor",
        description = "Monitor read and write activity across storage in real time",
        icon = Icons.Outlined.Storage,
        iconTint = Purple700,
        iconBackground = Purple50
    ),
//    ProFeature(
//        title = "Process Tree",
//        description = "Explore parent–child process relationships in a hierarchy view",
//        icon = Icons.Outlined.AccountTree,
//        iconTint = Teal700,
//        iconBackground = Teal50
//    ),
)


// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProVersion(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    var price by remember { mutableStateOf<String?>(null) }
    val isPro by remember{ (bridge?.isPro() ?: mutableStateOf(false)) }

    LaunchedEffect(Unit) {
        price = bridge?.getProVersionPrice()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Upgrade to Pro",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { activity?.onBackPressed() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
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
                text = "WHAT YOU GET",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Feature rows ──────────────────────────────────────────────
            features.forEach { feature ->
                FeatureRow(feature = feature)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Purchase card or Unlocked card ────────────────────────────
            if (isPro) {
                UnlockedCard()
            } else {
                PurchaseCard(
                    price = price,
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
                    text = "Secure payment via Google Play",
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
                text = "TASK MANAGER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = "Pro Version",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Everything you need to understand your device, deeply.",
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
                    text = feature.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = feature.description,
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
                Text(
                    text = "One-time purchase",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

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
                text = "Pay once, permanent pro access.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onPurchase,
                enabled = enabled && price != null,
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
                    text = "Upgrade Now",
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
                    text = "Pro Unlocked",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Thank you for supporting Task Manager!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
