package com.rk.taskmanager.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.system.Os
import androidx.annotation.Keep
import java.io.File

@Keep
interface IInterfaceCreator<T : IInterface> {
    @Keep fun asInterface(binder: IBinder): T
}

@Keep
interface TaskManagerService : IInterface {
    @Keep fun listPs(): List<Proc>
    @Keep fun getCpuUsage(): Byte
    @Keep fun killPid(pid: Int, signal: Int): Boolean

    companion object {
        const val DESCRIPTOR = "com.rk.taskmanager.TaskManagerService"
        const val TRANSACTION_listPs = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_getCpuUsage = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_killPid = IBinder.FIRST_CALL_TRANSACTION + 2

        val CREATOR = object : IInterfaceCreator<TaskManagerService> {
            override @Keep fun asInterface(binder: IBinder): TaskManagerService {
                return TaskManagerClient(binder)
            }
        }
    }
}
