package com.bashworks.ugs.jobinfo.machine;

import com.bashworks.ugs.jobinfo.kinematics.MachineProfile;

import javax.swing.Timer;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock machine gateway for standalone UI development, unit tests, and instant preview.
 */
public class SimulatedJobInfoMachineGateway implements JobInfoMachineGateway {

    private final List<JobInfoListener> listeners = new CopyOnWriteArrayList<>();
    private MachineProfile machineProfile = MachineProfile.standardRouter();

    private boolean connected = true;
    private boolean sendingFile = false;
    private boolean paused = false;

    private File loadedFile = null;
    private int totalRows = 5000;
    private int completedRows = 0;
    private int feedOverridePercent = 100;

    private Timer simulationTimer;

    public SimulatedJobInfoMachineGateway() {
        // Ticker to simulate machine streaming lines
        simulationTimer = new Timer(50, e -> {
            if (sendingFile && !paused && completedRows < totalRows) {
                // Advance 2-5 lines per tick
                int step = (int) Math.max(1, Math.round(3.0 * (feedOverridePercent / 100.0)));
                completedRows = Math.min(totalRows, completedRows + step);

                for (JobInfoListener listener : listeners) {
                    listener.onCommandCompleted(completedRows, false);
                }

                if (completedRows >= totalRows) {
                    stopStreaming();
                }
            }
        });
    }

    public void simulateLoadFile(File file, int lines) {
        this.loadedFile = file;
        this.totalRows = lines;
        this.completedRows = 0;
        for (JobInfoListener listener : listeners) {
            listener.onFileLoaded(file);
        }
    }

    public void startStreaming() {
        if (!sendingFile) {
            sendingFile = true;
            paused = false;
            completedRows = 0;
            simulationTimer.start();
            for (JobInfoListener listener : listeners) {
                listener.onStreamStarted();
            }
        }
    }

    public void pauseStreaming() {
        if (sendingFile && !paused) {
            paused = true;
            for (JobInfoListener listener : listeners) {
                listener.onStreamPaused();
            }
        }
    }

    public void resumeStreaming() {
        if (sendingFile && paused) {
            paused = false;
            for (JobInfoListener listener : listeners) {
                listener.onStreamResumed();
            }
        }
    }

    public void stopStreaming() {
        if (sendingFile) {
            sendingFile = false;
            paused = false;
            simulationTimer.stop();
            for (JobInfoListener listener : listeners) {
                listener.onStreamCompleted();
            }
        }
    }

    public void setFeedOverride(int percent) {
        this.feedOverridePercent = Math.max(10, Math.min(200, percent));
        for (JobInfoListener listener : listeners) {
            listener.onFeedOverrideChanged(this.feedOverridePercent);
        }
    }

    public void setProfile(MachineProfile profile) {
        this.machineProfile = profile;
        for (JobInfoListener listener : listeners) {
            listener.onMachineProfileChanged(profile);
        }
    }

    @Override
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    @Override
    public boolean isSendingFile() { return sendingFile; }

    @Override
    public boolean isPaused() { return paused; }

    @Override
    public boolean isIdle() { return connected && !sendingFile; }

    @Override
    public File getLoadedGcodeFile() { return loadedFile; }

    @Override
    public int getCompletedRows() { return completedRows; }

    @Override
    public int getTotalRows() { return totalRows; }

    @Override
    public int getActiveFeedOverridePercent() { return feedOverridePercent; }

    @Override
    public MachineProfile getLiveMachineProfile() { return machineProfile; }

    @Override
    public void requestSettingsRefresh() {
        for (JobInfoListener listener : listeners) {
            listener.onMachineProfileChanged(machineProfile);
        }
    }

    @Override
    public void addListener(JobInfoListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(JobInfoListener listener) {
        listeners.remove(listener);
    }
}
