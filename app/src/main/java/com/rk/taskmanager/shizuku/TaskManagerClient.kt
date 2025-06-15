package com.rk.taskmanager.shizuku

import android.os.IBinder
import android.os.Parcel
import androidx.annotation.Keep

@Keep class TaskManagerClient(private val binder: IBinder) : TaskManagerService {
    override @Keep fun listPs(): List<Proc> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(TaskManagerService.DESCRIPTOR)
            binder.transact(TaskManagerService.TRANSACTION_listPs, data, reply, 0)
            reply.readException()
            return mutableListOf<Proc>().apply {
                reply.readTypedList(this, Proc.CREATOR)
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }


    override @Keep fun getCpuUsage(): Byte {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TaskManagerService.DESCRIPTOR)
            binder.transact(TaskManagerService.TRANSACTION_getCpuUsage, data, reply, 0)
            reply.readException()
            reply.readByte()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }


    @Keep
    override fun killPid(pid: Int, signal: Int): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TaskManagerService.DESCRIPTOR)
            data.writeInt(pid)
            data.writeInt(signal)
            println("dndndkd")
            binder.transact(TaskManagerService.TRANSACTION_killPid, data, reply, IBinder.FLAG_ONEWAY)
            println("transation done")
            reply.readException()
            reply.readBoolean()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }


    override @Keep fun asBinder(): IBinder {
        return binder
    }
}