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
    @Keep fun newProcess(cmd: Array<String>, env: Array<String>, workingDir: String): Pair<Int, String>

    companion object {
        const val DESCRIPTOR = "com.rk.taskmanager.TaskManagerService"
        const val TRANSACTION_newProcess = IBinder.FIRST_CALL_TRANSACTION

        val CREATOR = object : IInterfaceCreator<TaskManagerService> {
            override @Keep fun asInterface(binder: IBinder): TaskManagerService {
                return TaskManagerClient(binder)
            }
        }
    }
}
