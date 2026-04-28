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

    private var cachedSoc: String? = null
    private var cachedAbi: String? = null
    private var cachedCores: Int? = null
    private var cachedArch: String? = null
    private var cachedClustersTemplate: Map<String, List<File>>? = null

    fun read(): CpuInfo {
        val cpuDirs = if (cachedCores == null) {
            File("/sys/devices/system/cpu/")
                .listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }
                ?.sortedBy { it.name }
                ?: emptyList()
        } else {
            emptyList()
        }

        if (cachedCores == null) {
            cachedCores = cpuDirs.size
            val clusters = mutableMapOf<String, MutableList<File>>()
            cpuDirs.forEach { cpu ->
                val clusterId = readFile("${cpu.path}/topology/physical_package_id") ?: "0"
                clusters.getOrPut(clusterId) { mutableListOf() }.add(cpu)
            }
            cachedClustersTemplate = clusters
        }

        if (cachedSoc == null) cachedSoc = Build.HARDWARE ?: "Unknown"
        if (cachedAbi == null) cachedAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
        if (cachedArch == null) cachedArch = System.getProperty("os.arch") ?: "Unknown"

        val clusterInfo = cachedClustersTemplate?.map { (id, cores) ->
            val cpu = cores.first()

            fun freq(vararg paths: String): String? {
                for (path in paths) {
                    val value = readFile(path)?.toLongOrNull()

                    if (value != null && value >= 10000) {
                        return value.toFreqString()
                    }
                }
                return "---"
            }

            CpuCluster(
                name = "Cluster $id",
                cores = cores.size,

                minFreq = freq(
                    "${cpu.path}/cpufreq/scaling_min_freq",
                    "${cpu.path}/cpufreq/cpuinfo_min_freq"
                ),

                maxFreq = freq(
                    "${cpu.path}/cpufreq/scaling_max_freq",
                    "${cpu.path}/cpufreq/cpuinfo_max_freq"
                ),

                currentFreq = freq(
                    "${cpu.path}/cpufreq/scaling_cur_freq",
                    "${cpu.path}/cpufreq/cpuinfo_cur_freq"
                )
            )
        } ?: emptyList()

        return CpuInfo(
            soc = cachedSoc!!,
            abi = cachedAbi!!,
            cores = cachedCores!!,
            arch = cachedArch!!,
            clusters = clusterInfo,
            governor = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        )
    }

    fun getUptimeFormatted(): String {
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

    private fun Long.toFreqString(): String {
        return when {
            this >= 1_000_000 ->
                String.format(Locale.ENGLISH, "%.2f GHz", this / 1_000_000.0)

            else ->
                "${this / 1000} MHz"
        }
    }
}