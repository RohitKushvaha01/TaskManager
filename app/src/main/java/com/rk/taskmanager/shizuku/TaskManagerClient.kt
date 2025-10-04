package com.rk.taskmanager.shizuku

import android.os.IBinder
import android.os.Parcel
import androidx.annotation.Keep

@Keep class TaskManagerClient(private val binder: IBinder) : TaskManagerService {

    @Keep
    override fun newProcess(
        cmd: Array<String>,
        env: Array<String>,
        workingDir: String
    ): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(TaskManagerService.DESCRIPTOR)

            // Write parameters to parcel
            data.writeStringArray(cmd)
            data.writeStringArray(env)
            data.writeString(workingDir)

            // Make the remote call
            binder.transact(TaskManagerService.TRANSACTION_newProcess, data, reply, 0)

            // Read response
            reply.readException()
            return reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }


    @Keep
    override fun asBinder(): IBinder {
        return binder
    }
}