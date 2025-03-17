package com.rk.taskmanager.shizuku;

import androidx.annotation.Keep;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

@Keep
public class SP {
    @Keep
    public static int newProcess(String[] cmd,String[] env,String dir) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InterruptedException {
        Method method = Shizuku.class.getDeclaredMethod(
                "newProcess",
                String[].class, // Java String[]
                String[].class, // Java String[]
                String.class    // Java String
        );

        // Make it accessible
        method.setAccessible(true);


        // Call the method
        ShizukuRemoteProcess result = (ShizukuRemoteProcess) method.invoke(null, cmd, env, dir);

        // Wait for the process to finish
        assert result != null;
        result.waitFor();
        return result.exitValue();
    }
}
