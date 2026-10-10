package com.bashworks.ugs.jobinfo.tracker;

import com.bashworks.ugs.jobinfo.kinematics.KinematicsSimulator;
import com.bashworks.ugs.jobinfo.kinematics.MachineProfile;
import com.bashworks.ugs.jobinfo.kinematics.SimulationResult;
import com.bashworks.ugs.jobinfo.machine.JobInfoListener;
import com.bashworks.ugs.jobinfo.machine.JobInfoMachineGateway;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * State machine managing job progress tracking, pause-aware stopwatch timing,
 * kinematics timeline mapping, dynamic feed override adjustments, and ETA calculations.
 */
public final class JobTracker implements JobInfoListener {

    private static final DateTimeFormatter ETA_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JobInfoMachineGateway gateway;
    private final List<Consumer<JobSnapshot>> snapshotListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UGS-JobInfo-Simulator");
        t.setDaemon(true);
        return t;
    });

    private final Timer tickTimer;

    // Kinematic model and simulation
    private volatile MachineProfile machineProfile;
    private volatile SimulationResult simulationResult = SimulationResult.empty();
    private volatile File currentFile = null;

    // Tracking state
    private volatile JobState state = JobState.IDLE;
    private volatile int feedOverridePercent = 100;
    private volatile int lastCompletedCommandIndex = 0;

    // Pause-aware wall-clock stopwatch
    private long runStartNanos = 0L;
    private long accumulatedElapsedNanos = 0L;
    private boolean isTiming = false;

    public JobTracker(JobInfoMachineGateway gateway) {
        this.gateway = gateway;
        this.machineProfile = gateway.getLiveMachineProfile();
        this.currentFile = gateway.getLoadedGcodeFile();
        this.state = gateway.isConnected()
                ? (currentFile != null ? JobState.READY : JobState.IDLE)
                : JobState.DISCONNECTED;
        this.gateway.addListener(this);

        // 10 Hz refresh ticker (every 100ms) on Swing EDT
        this.tickTimer = new Timer(100, e -> emitSnapshot());

        // Check if a file is already loaded at startup
        File initialFile = gateway.getLoadedGcodeFile();
        if (initialFile != null && initialFile.exists()) {
            onFileLoaded(initialFile);
        }
    }

    public void start() {
        tickTimer.start();
        emitSnapshot();
    }

    public void stop() {
        tickTimer.stop();
    }

    public void addSnapshotListener(Consumer<JobSnapshot> listener) {
        if (!snapshotListeners.contains(listener)) {
            snapshotListeners.add(listener);
            listener.accept(buildSnapshot());
        }
    }

    public void removeSnapshotListener(Consumer<JobSnapshot> listener) {
        snapshotListeners.remove(listener);
    }

    public void triggerResimulation() {
        if (currentFile != null && currentFile.exists()) {
            runBackgroundSimulation(currentFile);
        }
    }

    private void runBackgroundSimulation(File file) {
        state = JobState.SIMULATING;
        emitSnapshot();

        MachineProfile prof = this.machineProfile;
        workerExecutor.submit(() -> {
            try {
                SimulationResult res = KinematicsSimulator.simulateFile(file, prof);
                SwingUtilities.invokeLater(() -> {
                    this.simulationResult = res;
                    this.state = gateway.isSendingFile() ? (gateway.isPaused() ? JobState.PAUSED : JobState.RUNNING) : JobState.READY;
                    emitSnapshot();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    this.simulationResult = SimulationResult.empty();
                    this.state = JobState.READY;
                    emitSnapshot();
                });
            }
        });
    }

    private void emitSnapshot() {
        // Synchronize with gateway state in case an event was missed or on initial startup
        File activeFile = gateway.getLoadedGcodeFile();
        if (activeFile != null && activeFile.exists() && (currentFile == null || !activeFile.equals(currentFile))) {
            onFileLoaded(activeFile);
            return;
        } else if (currentFile != null && (activeFile == null || !activeFile.exists())) {
            onFileUnloaded();
            return;
        }

        if (gateway.isSendingFile()) {
            if (gateway.isPaused()) {
                if (state != JobState.PAUSED) {
                    onStreamPaused();
                }
            } else {
                if (state != JobState.RUNNING) {
                    if (!isTiming) {
                        runStartNanos = System.nanoTime();
                        isTiming = true;
                    }
                    state = JobState.RUNNING;
                }
            }
        } else {
            if (state == JobState.RUNNING || state == JobState.PAUSED) {
                onStreamCompleted();
            } else if (!gateway.isConnected() && currentFile == null) {
                state = JobState.DISCONNECTED;
            } else if (state == JobState.DISCONNECTED && gateway.isConnected()) {
                state = (currentFile != null) ? JobState.READY : JobState.IDLE;
            }
        }

        int compRows = gateway.getCompletedRows();

        if (compRows > lastCompletedCommandIndex) {
            lastCompletedCommandIndex = compRows;
        }

        int ovr = gateway.getActiveFeedOverridePercent();
        if (ovr > 0 && ovr != feedOverridePercent) {
            feedOverridePercent = ovr;
        }

        JobSnapshot snapshot = buildSnapshot();
        for (Consumer<JobSnapshot> listener : snapshotListeners) {
            listener.accept(snapshot);
        }
    }

    public JobSnapshot buildSnapshot() {
        double totalTime = simulationResult.getTotalDurationSeconds();

        // Calculate wall-clock elapsed time
        double elapsedSeconds = 0.0;
        if (isTiming) {
            long now = System.nanoTime();
            elapsedSeconds = (accumulatedElapsedNanos + (now - runStartNanos)) / 1_000_000_000.0;
        } else {
            elapsedSeconds = accumulatedElapsedNanos / 1_000_000_000.0;
        }

        // Calculate simulated time completed
        double simCompletedSeconds = 0.0;
        double progressPercent = 0.0;

        if (state == JobState.COMPLETED) {
            simCompletedSeconds = totalTime;
            progressPercent = 100.0;
        } else if (totalTime > 0.0) {
            simCompletedSeconds = simulationResult.getTimeAtCommand(lastCompletedCommandIndex);
            progressPercent = Math.min(100.0, Math.max(0.0, (simCompletedSeconds / totalTime) * 100.0));
        }

        // Calculate remaining time with feed override and adaptive calibration factor
        double remainingSeconds = 0.0;
        String etaStr = "--:--:--";

        if (state == JobState.COMPLETED) {
            remainingSeconds = 0.0;
            etaStr = "Completed";
        } else if (totalTime > 0.0) {
            double nominalSimRemaining = Math.max(0.0, totalTime - simCompletedSeconds);
            double overrideFactor = Math.max(0.1, feedOverridePercent / 100.0);
            double baseRemaining = nominalSimRemaining / overrideFactor;

            // Adaptive real-world calibration factor
            double calibFactor = 1.0;
            if (elapsedSeconds > 15.0 && simCompletedSeconds > 10.0) {
                double rawRatio = elapsedSeconds / simCompletedSeconds;
                calibFactor = Math.min(1.25, Math.max(0.85, rawRatio));
            }

            remainingSeconds = baseRemaining * calibFactor;

            if (state == JobState.RUNNING || state == JobState.PAUSED) {
                if (remainingSeconds > 0) {
                    LocalTime etaTime = LocalTime.now().plusSeconds((long) Math.ceil(remainingSeconds));
                    etaStr = etaTime.format(ETA_FORMATTER);
                } else {
                    etaStr = "Finishing…";
                }
            } else if (state == JobState.READY) {
                remainingSeconds = totalTime;
            }
        }

        String fileNameStr = (currentFile != null) ? currentFile.getName() : "No file loaded";

        return new JobSnapshot(
                state,
                totalTime,
                elapsedSeconds,
                remainingSeconds,
                progressPercent,
                etaStr,
                feedOverridePercent,
                lastCompletedCommandIndex,
                simulationResult.getTotalCommands(),
                fileNameStr,
                simulationResult,
                machineProfile
        );
    }

    // -------------------------------------------------------------
    // JobInfoListener Implementation
    // -------------------------------------------------------------

    @Override
    public void onFileLoaded(File file) {
        this.currentFile = file;
        this.lastCompletedCommandIndex = 0;
        this.accumulatedElapsedNanos = 0L;
        this.isTiming = false;
        runBackgroundSimulation(file);
    }

    @Override
    public void onFileUnloaded() {
        this.currentFile = null;
        this.simulationResult = SimulationResult.empty();
        this.lastCompletedCommandIndex = 0;
        this.accumulatedElapsedNanos = 0L;
        this.isTiming = false;
        this.state = JobState.IDLE;
        emitSnapshot();
    }

    @Override
    public void onStreamStarted() {
        this.accumulatedElapsedNanos = 0L;
        this.runStartNanos = System.nanoTime();
        this.isTiming = true;
        this.lastCompletedCommandIndex = 0;
        this.state = JobState.RUNNING;
        emitSnapshot();
    }

    @Override
    public void onStreamPaused() {
        if (isTiming) {
            accumulatedElapsedNanos += (System.nanoTime() - runStartNanos);
            isTiming = false;
        }
        this.state = JobState.PAUSED;
        emitSnapshot();
    }

    @Override
    public void onStreamResumed() {
        this.runStartNanos = System.nanoTime();
        this.isTiming = true;
        this.state = JobState.RUNNING;
        emitSnapshot();
    }

    @Override
    public void onStreamCompleted() {
        if (isTiming) {
            accumulatedElapsedNanos += (System.nanoTime() - runStartNanos);
            isTiming = false;
        }
        this.state = (currentFile != null && simulationResult.getTotalDurationSeconds() > 0)
                ? JobState.COMPLETED : JobState.READY;
        emitSnapshot();
    }

    @Override
    public void onCommandCompleted(int commandIndex, boolean isGenerated) {
        if (!isGenerated && commandIndex >= 0) {
            this.lastCompletedCommandIndex = Math.max(this.lastCompletedCommandIndex, commandIndex);
        }
    }

    @Override
    public void onFeedOverrideChanged(int overridePercent) {
        this.feedOverridePercent = Math.max(10, Math.min(200, overridePercent));
        emitSnapshot();
    }

    @Override
    public void onMachineProfileChanged(MachineProfile profile) {
        this.machineProfile = profile;
        if (currentFile != null && currentFile.exists()) {
            runBackgroundSimulation(currentFile);
        } else {
            emitSnapshot();
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected) {
        if (!connected) {
            if (state == JobState.RUNNING || state == JobState.PAUSED) {
                onStreamCompleted();
            }
            if (currentFile == null) {
                this.state = JobState.DISCONNECTED;
            }
        } else {
            if (state == JobState.DISCONNECTED) {
                this.state = (currentFile != null) ? JobState.READY : JobState.IDLE;
            }
        }
        emitSnapshot();
    }
}

