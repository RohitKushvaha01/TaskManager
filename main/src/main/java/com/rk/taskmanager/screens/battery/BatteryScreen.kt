package com.rk.taskmanager.screens.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import com.rk.commons.ui.SectionHeader
import com.rk.commons.utils.formatTemperature
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.daemon.daemon_messages
import com.rk.taskmanager.daemon.isConnected
import com.rk.taskmanager.daemon.send_daemon_messages
import com.rk.taskmanager.settings.useImperialUnits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

// Local replacement for the pro-module battery screen while it is absent
// from this build; uses the root daemon with ACTION_BATTERY_CHANGED as fallback

private data class BatteryInfo(
    val capacity: Int = -1,
    val status: String = "",
    val health: String = "",
    val tempC: Float = -1f,
    val voltageMV: Long = -1,
    val currentMA: Long = 0,
    val cycles: Int = -1,
    val chargeType: String = "",
    val usbType: String = ""
)

private fun statusText(statusConst: Int): String = when (statusConst) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
    BatteryManager.BATTERY_STATUS_FULL -> "Full"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
    else -> "Unknown"
}

private fun readFallbackInfo(context: Context): BatteryInfo {
    val sticky: Intent =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryInfo()

    val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val capacity = if (level >= 0 && scale > 0) level * 100 / scale else -1
    val statusConst = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val temp = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
    val voltageMV = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

    return BatteryInfo(
        capacity = capacity,
        status = if (statusConst >= 0) statusText(statusConst) else "",
        tempC = if (temp >= 0) temp / 10f else -1f,
        voltageMV = if (voltageMV > 0) voltageMV.toLong() else -1
    )
}

@Composable
fun BatteryScreen(modifier: Modifier = Modifier) {
    var info by remember { mutableStateOf(BatteryInfo()) }

    LaunchedEffect(Unit) {
        info = withContext(Dispatchers.IO) { readFallbackInfo(TaskManager.requireContext()) }
        while (isActive) {
            if (isConnected) {
                send_daemon_messages.emit(JSONObject().apply { put("cmd", "BATTERY_PING") }.toString())
            } else {
                info = withContext(Dispatchers.IO) { readFallbackInfo(TaskManager.requireContext()) }
            }
            delay(3000)
        }
    }

    LaunchedEffect(Unit) {
        daemon_messages.collect { message ->
            try {
                val json = JSONObject(message)
                if (json.optString("type") == "BATTERY_INFO") {
                    val battery = json.optJSONObject("battery") ?: return@collect
                    val temp = battery.optInt("temp", -1)
                    val voltageUV = battery.optLong("voltageUV", -1)
                    info = BatteryInfo(
                        capacity = battery.optInt("capacity", -1),
                        status = battery.optString("status"),
                        health = battery.optString("health"),
                        tempC = if (temp >= 0) temp / 10f else -1f,
                        voltageMV = if (voltageUV > 0) voltageUV / 1000 else -1,
                        currentMA = battery.optLong("currentUA", 0) / 1000,
                        cycles = battery.optInt("cycles", -1),
                        chargeType = battery.optString("chargeType"),
                        usbType = battery.optString("usbType")
                    )
                }
            } catch (e: Exception) {}
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HorizontalDivider()

            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Battery")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(
                                label = "Level",
                                value = if (info.capacity >= 0) "${info.capacity}%" else "-",
                                highlighted = true
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(label = "Status", value = info.status.ifEmpty { "-" })
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(label = "Health", value = info.health.ifEmpty { "-" })
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(
                                label = "Temperature",
                                value = if (info.tempC >= 0f) {
                                    formatTemperature(info.tempC, useImperialUnits)
                                } else "-"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(
                                label = "Voltage",
                                value = if (info.voltageMV >= 0) "${info.voltageMV} mV" else "-"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(label = "Current", value = "${info.currentMA} mA")
                        }
                    }

                    // currentMA is signed (negative = charging); 0 = unknown (no
                    // current in the sticky-broadcast fallback) so no wattage then
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(
                                label = "Wattage",
                                value = if (info.voltageMV >= 0 && info.currentMA != 0L) {
                                    String.format(
                                        Locale.ENGLISH,
                                        "%.1f W",
                                        abs(info.voltageMV * info.currentMA) / 1_000_000.0
                                    )
                                } else "-"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(
                                label = "Charging",
                                value = listOfNotNull(
                                    info.chargeType.takeIf { it.isNotEmpty() && it != "Nop" },
                                    info.usbType.takeIf { it.isNotEmpty() && it != "Unknown" && it != "None" }
                                ).joinToString(" · ").ifEmpty { "-" }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem(
                                label = "Charge cycles",
                                value = if (info.cycles >= 0) "${info.cycles}" else "-"
                            )
                        }
                    }
                }
            }
        }
    }
}
