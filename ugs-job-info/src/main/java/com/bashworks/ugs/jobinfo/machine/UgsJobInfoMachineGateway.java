package com.bashworks.ugs.jobinfo.machine;

import com.bashworks.ugs.jobinfo.kinematics.MachineProfile;
import com.willwinder.universalgcodesender.IController;
import com.willwinder.universalgcodesender.firmware.FirmwareSetting;
import com.willwinder.universalgcodesender.firmware.IFirmwareSettings;
import com.willwinder.universalgcodesender.listeners.ControllerState;
import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.listeners.OverridePercents;
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.UGSEvent;
import com.willwinder.universalgcodesender.model.events.CommandEvent;
import com.willwinder.universalgcodesender.model.events.CommandEventType;
import com.willwinder.universalgcodesender.model.events.ControllerStateEvent;
import com.willwinder.universalgcodesender.model.events.ControllerStatusEvent;
import com.willwinder.universalgcodesender.model.events.FileState;
import com.willwinder.universalgcodesender.model.events.FileStateEvent;
import com.willwinder.universalgcodesender.model.events.FirmwareSettingEvent;
import com.willwinder.universalgcodesender.model.events.StreamEvent;
import com.willwinder.universalgcodesender.types.GcodeCommand;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * Live UGS Gateway integrating with UGS BackendAPI, firmware settings ($110-$122),
 * streaming events, and live status reports.
 */
public final class UgsJobInfoMachineGateway implements JobInfoMachineGateway, UGSEventListener {

    private final BackendAPI backend;
    private final List<JobInfoListener> listeners = new CopyOnWriteArrayList<>();
    private final Preferences preferences;
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ugs-jobinfo-debounce");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingRefreshTask;

    private MachineProfile machineProfile;
    private int currentFeedOverride = 100;

    public UgsJobInfoMachineGateway(BackendAPI backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.preferences = initPreferences();
        this.machineProfile = loadCachedProfile();
        if (backend.isConnected()) {
            onMachineConnected();
        }
    }

    private static Preferences initPreferences() {
        try {
            Class<?> nbPrefClass = Class.forName("org.openide.util.NbPreferences");
            return (Preferences) nbPrefClass.getMethod("forModule", Class.class).invoke(null, UgsJobInfoMachineGateway.class);
        } catch (Throwable fallback) {
            try {
                return Preferences.userNodeForPackage(UgsJobInfoMachineGateway.class);
            } catch (Throwable noRegistry) {
                return null;
            }
        }
    }

    private MachineProfile loadCachedProfile() {
        if (preferences == null) {
            return MachineProfile.standardRouter();
        }
        try {
            double ax = preferences.getDouble("accel_x", 500.0);
            double ay = preferences.getDouble("accel_y", 500.0);
            double az = preferences.getDouble("accel_z", 150.0);
            double vx = preferences.getDouble("max_rate_x", 5000.0);
            double vy = preferences.getDouble("max_rate_y", 5000.0);
            double vz = preferences.getDouble("max_rate_z", 1200.0);
            double dev = preferences.getDouble("junction_dev", 0.010);
            double arc = preferences.getDouble("arc_tol", 0.002);
            double spin = preferences.getDouble("spindle_delay", 1.5);
            String name = preferences.get("profile_name", "Cached CNC Profile");

            return new MachineProfile(name, ax, ay, az, vx, vy, vz, dev, arc, spin);
        } catch (Throwable fallback) {
            return MachineProfile.standardRouter();
        }
    }

    private void saveCachedProfile(MachineProfile p) {
        if (preferences == null || p == null) {
            return;
        }
        try {
            preferences.putDouble("accel_x", p.getAccelX());
            preferences.putDouble("accel_y", p.getAccelY());
            preferences.putDouble("accel_z", p.getAccelZ());
            preferences.putDouble("max_rate_x", p.getMaxRateX());
            preferences.putDouble("max_rate_y", p.getMaxRateY());
            preferences.putDouble("max_rate_z", p.getMaxRateZ());
            preferences.putDouble("junction_dev", p.getJunctionDeviation());
            preferences.putDouble("arc_tol", p.getArcTolerance());
            preferences.putDouble("spindle_delay", p.getSpindleSpinUpDelaySeconds());
            preferences.put("profile_name", p.getName());
        } catch (Throwable ignored) {
        }
    }


    private synchronized void scheduleDebouncedProfileRefresh(long delayMs) {
        if (pendingRefreshTask != null && !pendingRefreshTask.isDone()) {
            pendingRefreshTask.cancel(false);
        }
        pendingRefreshTask = debounceExecutor.schedule(this::refreshMachineProfileFromController, delayMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void refreshMachineProfileFromController() {
        if (!backend.isConnected() || backend.getController() == null) {
            return;
        }

        IFirmwareSettings fw = backend.getController().getFirmwareSettings();
        if (fw == null) {
            return;
        }

        double ax = parseSettingDouble(fw, "$120", machineProfile.getAccelX());
        double ay = parseSettingDouble(fw, "$121", machineProfile.getAccelY());
        double az = parseSettingDouble(fw, "$122", machineProfile.getAccelZ());

        double vx = parseSettingDouble(fw, "$110", machineProfile.getMaxRateX());
        double vy = parseSettingDouble(fw, "$111", machineProfile.getMaxRateY());
        double vz = parseSettingDouble(fw, "$112", machineProfile.getMaxRateZ());

        double dev = parseSettingDouble(fw, "$11", machineProfile.getJunctionDeviation());
        double arc = parseSettingDouble(fw, "$12", machineProfile.getArcTolerance());

        String version = backend.getController().getFirmwareVersion();
        String fwName = (version != null && !version.isBlank())
                ? "Connected (" + version + ")"
                : "Connected Controller";

        MachineProfile newProfile = new MachineProfile(fwName, ax, ay, az, vx, vy, vz, dev, arc, machineProfile.getSpindleSpinUpDelaySeconds());
        if (!newProfile.equals(this.machineProfile)) {
            this.machineProfile = newProfile;
            saveCachedProfile(this.machineProfile);

            for (JobInfoListener listener : listeners) {
                listener.onMachineProfileChanged(this.machineProfile);
            }
        }
    }

    private void onMachineConnected() {
        for (JobInfoListener l : listeners) {
            l.onConnectionStateChanged(true);
        }

        // If settings aren't yet populated in the controller, proactively query with $$
        if (backend.getController() != null) {
            IFirmwareSettings fw = backend.getController().getFirmwareSettings();
            boolean hasRates = (fw != null && fw.getSetting("$110").isPresent());
            if (!hasRates && backend.isIdle()) {
                try {
                    backend.sendGcodeCommand("$$");
                } catch (Exception ignored) {
                }
            }
        }

        scheduleDebouncedProfileRefresh(300);
    }

    private void onMachineDisconnected() {
        String discName = "Disconnected (Cached)";
        this.machineProfile = new MachineProfile(
                discName,
                machineProfile.getAccelX(), machineProfile.getAccelY(), machineProfile.getAccelZ(),
                machineProfile.getMaxRateX(), machineProfile.getMaxRateY(), machineProfile.getMaxRateZ(),
                machineProfile.getJunctionDeviation(), machineProfile.getArcTolerance(),
                machineProfile.getSpindleSpinUpDelaySeconds()
        );
        for (JobInfoListener l : listeners) {
            l.onConnectionStateChanged(false);
            l.onMachineProfileChanged(this.machineProfile);
        }
    }


    private double parseSettingDouble(IFirmwareSettings fw, String key, double fallback) {
        try {
            return fw.getSetting(key)
                    .map(FirmwareSetting::getValue)
                    .map(Double::parseDouble)
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public boolean isConnected() {
        return backend.isConnected();
    }

    @Override
    public boolean isSendingFile() {
        return backend.isSendingFile();
    }

    @Override
    public boolean isPaused() {
        return backend.isPaused();
    }

    @Override
    public boolean isIdle() {
        return backend.isIdle() && backend.getControllerState() == ControllerState.IDLE;
    }

    @Override
    public File getLoadedGcodeFile() {
        File proc = backend.getProcessedGcodeFile();
        return (proc != null && proc.exists()) ? proc : backend.getGcodeFile();
    }

    @Override
    public int getCompletedRows() {
        return (int) backend.getNumCompletedRows();
    }

    @Override
    public int getTotalRows() {
        return (int) backend.getNumRows();
    }

    @Override
    public int getActiveFeedOverridePercent() {
        return currentFeedOverride;
    }

    @Override
    public MachineProfile getLiveMachineProfile() {
        return machineProfile;
    }

    @Override
    public void requestSettingsRefresh() {
        if (backend.isConnected() && backend.isIdle()) {
            try {
                backend.sendGcodeCommand("$$");
            } catch (Exception ignored) {
            }
        }
        scheduleDebouncedProfileRefresh(250);
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

    @Override
    public void UGSEvent(UGSEvent event) {
        if (event instanceof StreamEvent) {
            StreamEvent se = (StreamEvent) event;
            switch (se.getType()) {
                case STREAM_STARTED:
                    for (JobInfoListener l : listeners) l.onStreamStarted();
                    break;
                case STREAM_PAUSED:
                    for (JobInfoListener l : listeners) l.onStreamPaused();
                    break;
                case STREAM_RESUMED:
                    for (JobInfoListener l : listeners) l.onStreamResumed();
                    break;
                case STREAM_CANCELED:
                case STREAM_COMPLETE:
                    for (JobInfoListener l : listeners) l.onStreamCompleted();
                    break;
            }
        } else if (event instanceof FileStateEvent) {
            FileStateEvent fse = (FileStateEvent) event;
            if (fse.getFileState() == FileState.FILE_LOADED) {
                File f = getLoadedGcodeFile();
                for (JobInfoListener l : listeners) l.onFileLoaded(f);
            } else if (fse.getFileState() == FileState.FILE_UNLOADED) {
                for (JobInfoListener l : listeners) l.onFileUnloaded();
            } else if (fse.getFileState() == FileState.FILE_STREAM_COMPLETE) {
                for (JobInfoListener l : listeners) l.onStreamCompleted();
            }
        } else if (event instanceof CommandEvent) {
            CommandEvent ce = (CommandEvent) event;
            if (ce.getCommandEventType() == CommandEventType.COMMAND_COMPLETE && ce.getCommand() != null) {
                GcodeCommand cmd = ce.getCommand();
                for (JobInfoListener l : listeners) {
                    l.onCommandCompleted(cmd.getCommandNumber(), cmd.isGenerated());
                }
            }
        } else if (event instanceof ControllerStatusEvent) {
            ControllerStatusEvent cse = (ControllerStatusEvent) event;
            ControllerStatus status = cse.getStatus();
            if (status != null && status.getOverrides() != null) {
                OverridePercents ovr = status.getOverrides();
                if (ovr.feed() > 0 && ovr.feed() != currentFeedOverride) {
                    currentFeedOverride = ovr.feed();
                    for (JobInfoListener l : listeners) {
                        l.onFeedOverrideChanged(currentFeedOverride);
                    }
                }
            }
        } else if (event instanceof FirmwareSettingEvent) {
            // Controller streams 30+ settings sequentially upon connect; debounce to bundle updates
            scheduleDebouncedProfileRefresh(250);
        } else if (event instanceof ControllerStateEvent) {
            ControllerStateEvent cse = (ControllerStateEvent) event;
            ControllerState state = cse.getState();
            ControllerState prevState = cse.getPreviousState();

            if (state == ControllerState.HOLD || state == ControllerState.DOOR) {
                for (JobInfoListener l : listeners) l.onStreamPaused();
            } else if (state == ControllerState.RUN && isSendingFile()) {
                for (JobInfoListener l : listeners) l.onStreamResumed();
            }

            // Connection state transitions
            if (state != ControllerState.DISCONNECTED && state != ControllerState.CONNECTING) {
                if (prevState == ControllerState.DISCONNECTED || prevState == ControllerState.CONNECTING || prevState == ControllerState.UNKNOWN) {
                    onMachineConnected();
                }
            } else if (state == ControllerState.DISCONNECTED && prevState != ControllerState.DISCONNECTED) {
                onMachineDisconnected();
            }
        }
    }
}
