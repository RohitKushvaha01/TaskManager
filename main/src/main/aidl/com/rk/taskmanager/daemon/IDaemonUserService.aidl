package com.rk.taskmanager.daemon;

import android.os.ParcelFileDescriptor;

/**
 * Binder exposed by the daemon UserService that Shizuku starts as shell/root.
 *
 * The app creates the stdio pipes and passes the service-owned ends in, so EOF
 * propagates in both directions and the daemon is not orphaned if the app dies.
 */
interface IDaemonUserService {

    /**
     * Starts the daemon binary and relays its stdio over the supplied pipes.
     *
     * @param daemonPath   absolute path to the daemon binary
     * @param workingDir   working directory for the daemon
     * @param stdinRead    read end of the pipe the app writes commands to
     * @param stdoutWrite  write end of the pipe the app reads output from
     */
    void start(String daemonPath, String workingDir,
               in ParcelFileDescriptor stdinRead,
               in ParcelFileDescriptor stdoutWrite) = 1;

    /** Reserved destroy transaction defined by Shizuku. */
    void destroy() = 16777114;
}
