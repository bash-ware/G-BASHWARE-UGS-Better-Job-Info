package com.bashworks.ugs.jobinfo.machine;

import com.bashworks.ugs.jobinfo.kinematics.MachineProfile;

import java.io.File;

/**
 * Interface abstracting UGS BackendAPI and controller hardware for clean testability.
 */
public interface JobInfoMachineGateway {
    boolean isConnected();
    boolean isSendingFile();
    boolean isPaused();
    boolean isIdle();

    File getLoadedGcodeFile();
    int getCompletedRows();
    int getTotalRows();
    int getActiveFeedOverridePercent();

    MachineProfile getLiveMachineProfile();
    void requestSettingsRefresh();

    void addListener(JobInfoListener listener);
    void removeListener(JobInfoListener listener);
}
