package com.rk.taskmanager.daemon

import com.rk.commons.getString
import com.rk.taskmanager.shizuku.ShizukuShell
import com.rk.commons.strings

enum class DaemonResult(var message: String?) {
    OK(null),
    SHIZUKU_PERMISSION_DENIED(strings.shizuku_permission_denied.getString()),
    SHIZUKU_NOT_RUNNING(if (ShizukuShell.isShizukuInstalled()) strings.shizuku_not_running.getString() else strings.shizuku_not_installed.getString()),
    SU_FAILED(strings.su_not_in_path.getString()),
    UNKNOWN_ERROR(null),
    DAEMON_REFUSED(strings.daemon_not_started.getString()),
    DAEMON_ALREADY_BEING_STARTED(null),
    SKIPPED(null)
}