package com.bashworks.ugs.jobinfo.machine;

import com.bashworks.ugs.jobinfo.kinematics.MachineProfile;

import java.io.File;

/**
 * Event listener interface for machine state, streaming events, and parameter updates.
 */
public interface JobInfoListener {
    void onFileLoaded(File file);
    void onFileUnloaded();
    void onStreamStarted();
    void onStreamPaused();
    void onStreamResumed();
    void onStreamCompleted();
    void onCommandCompleted(int commandIndex, boolean isGenerated);
    void onFeedOverrideChanged(int overridePercent);
    void onMachineProfileChanged(MachineProfile profile);

    /**
     * Called when the connection to the machine controller changes.
     *
     * @param connected true if connected and responsive, false if disconnected
     */
    default void onConnectionStateChanged(boolean connected) {}
}

