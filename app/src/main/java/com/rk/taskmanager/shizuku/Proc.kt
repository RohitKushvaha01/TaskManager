package com.rk.taskmanager.shizuku

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep

@Keep
data class Proc(
    val name: String,
    var nice: Int,
    val pid: Int,
    val uid: Int,
    val cpuUsage: Float,
    val parentPid: Int,
    val isForeground: Boolean,
    val memoryUsageKb: Long,
    val cmdLine: String,
    val state: String,
    val threads: Int,
    val startTime: Long,
    val elapsedTime: Float,
    val residentSetSizeKb: Long,
    val virtualMemoryKb: Long,
    val cgroup: String,
    val executablePath: String
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(nice)
        parcel.writeInt(pid)
        parcel.writeInt(uid)
        parcel.writeFloat(cpuUsage)
        parcel.writeInt(parentPid)
        parcel.writeByte(if (isForeground) 1 else 0)
        parcel.writeLong(memoryUsageKb)
        parcel.writeString(cmdLine)
        parcel.writeString(state)
        parcel.writeInt(threads)
        parcel.writeLong(startTime)
        parcel.writeFloat(elapsedTime)
        parcel.writeLong(residentSetSizeKb)
        parcel.writeLong(virtualMemoryKb)
        parcel.writeString(cgroup)
        parcel.writeString(executablePath)
    }

    companion object CREATOR : Parcelable.Creator<Proc> {
        override fun createFromParcel(parcel: Parcel): Proc {
            return Proc(
                parcel.readString()!!,
                parcel.readInt(),
                parcel.readInt(),
                parcel.readInt(),
                parcel.readFloat(),
                parcel.readInt(),
                parcel.readByte() != 0.toByte(),
                parcel.readLong(),
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readInt(),
                parcel.readLong(),
                parcel.readFloat(),
                parcel.readLong(),
                parcel.readLong(),
                parcel.readString()!!,
                parcel.readString()!!
            )
        }

        override fun newArray(size: Int): Array<Proc?> = arrayOfNulls(size)
    }
}
