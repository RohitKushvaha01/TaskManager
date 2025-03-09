package com.rk.taskmanager.shizuku

import android.os.Parcel
import android.os.Parcelable

data class Proc(
    val name: String,
    val pid: Int,
    val uid: Int,
    val cpuUsage: Float,
    val parentPid: Int,
    val isForeground: Boolean,
    val memoryUsageKb: Long,
    val cmdLine: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readFloat(),
        parcel.readInt(),
        parcel.readByte() != 0.toByte(),
        parcel.readLong(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(pid)
        parcel.writeInt(uid)
        parcel.writeFloat(cpuUsage)
        parcel.writeInt(parentPid)
        parcel.writeByte(if (isForeground) 1 else 0)
        parcel.writeLong(memoryUsageKb)
        parcel.writeString(cmdLine)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Proc){return false}
        return other.pid == pid
    }

    override fun toString(): String {
        return cmdLine
    }

    companion object CREATOR : Parcelable.Creator<Proc> {
        override fun createFromParcel(parcel: Parcel): Proc {
            return Proc(parcel)
        }

        override fun newArray(size: Int): Array<Proc?> {
            return arrayOfNulls(size)
        }
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + pid
        result = 31 * result + uid
        result = 31 * result + cpuUsage.hashCode()
        result = 31 * result + parentPid
        result = 31 * result + isForeground.hashCode()
        result = 31 * result + memoryUsageKb.hashCode()
        result = 31 * result + cmdLine.hashCode()
        return result
    }
}
