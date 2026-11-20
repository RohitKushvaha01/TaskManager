-keepnames class  com.rk.commons.settings.Settings {
    *;
}

# Shizuku instantiates DaemonUserService by class name in a separate process and
# calls its Context constructor through reflection.
-keep class com.rk.taskmanager.daemon.DaemonUserService { *; }

# AIDL contract used across the app/user-service process boundary.
-keep class com.rk.taskmanager.daemon.IDaemonUserService { *; }
-keep class com.rk.taskmanager.daemon.IDaemonUserService$Stub { *; }
-keep class com.rk.taskmanager.daemon.IDaemonUserService$Stub$Proxy { *; }
-keep class com.rk.taskmanager.daemon.IDaemonUserService$Default { *; }
-keep class com.rk.taskmanager.daemon.IDaemonUserService$_Parcel { *; }