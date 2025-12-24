package com.rk.taskmanager.screens.cpu

import android.os.Build
import android.os.SystemClock
import java.io.File
import java.util.Locale

object CpuInfoReader {

    data class CpuInfo(
        val soc: String,
        val abi: String,
        val cores: Int,
        val arch: String,
        val clusters: List<CpuCluster>,
        val governor: String?
    )

    data class CpuCluster(
        val name: String,
        val cores: Int,
        val minFreq: String?,
        val maxFreq: String?,
        val currentFreq: String?
    )

    fun read(): CpuInfo {
        val cpuDirs = File("/sys/devices/system/cpu/")
            .listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }
            ?.sortedBy { it.name }
            ?: emptyList()

        val clusters = mutableMapOf<String, MutableList<File>>()

        cpuDirs.forEach { cpu ->
            val clusterId = readFile("${cpu.path}/topology/physical_package_id") ?: "0"
            clusters.getOrPut(clusterId) { mutableListOf() }.add(cpu)
        }

        val clusterInfo = clusters.map { (id, cores) ->
            val cpu = cores.first()
            CpuCluster(
                name = "Cluster $id",
                cores = cores.size,
                minFreq = readFile("${cpu.path}/cpufreq/cpuinfo_min_freq")?.toMHz(),
                maxFreq = readFile("${cpu.path}/cpufreq/cpuinfo_max_freq")?.toMHz(),
                currentFreq = readFile("${cpu.path}/cpufreq/scaling_cur_freq")?.toMHz()
            )
        }

        return CpuInfo(
            soc = Build.HARDWARE ?: "Unknown",
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
            cores = cpuDirs.size,
            arch = System.getProperty("os.arch") ?: "Unknown",
            clusters = clusterInfo,
            governor = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        )
    }

    fun getCpuTemperatureCelsius(): String {
        val sensorPaths = arrayOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",
            "/sys/class/hwmon/hwmon0/device/temp1_input",
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature",
            "/sys/devices/platform/s5p-tmu/temperature",
            "/sys/devices/platform/s5p-tmu/curr_temp"
        )

        for (path in sensorPaths) {
            try {
                val file = File(path)
                if (!file.exists()) continue

                val raw = file.readText().trim().toInt()

                // Most Android/Linux sensors report millidegrees
                return (if (raw > 1000) raw / 1000 else raw).toString()
            } catch (_: Exception) {
                // Ignore and try next sensor
            }
        }

        return "N/A"
    }

    fun getUptimeFormatted(): String {
        // SystemClock.elapsedRealtime() returns milliseconds since boot
        val uptimeMillis = SystemClock.elapsedRealtime()
        val uptimeSeconds = uptimeMillis / 1000

        val days = uptimeSeconds / 86400
        val hours = (uptimeSeconds % 86400) / 3600
        val minutes = (uptimeSeconds % 3600) / 60

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }


    private fun readFile(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()

    private fun String.toMHz(): String {
        val khz = toLongOrNull() ?: return this
        return when {
            khz >= 1000000 -> String.format(Locale.ENGLISH,"%.2f GHz", khz / 1000000.0)
            khz >= 1000 -> "${khz / 1000} MHz"
            else -> "$khz kHz"
        }
    }
}