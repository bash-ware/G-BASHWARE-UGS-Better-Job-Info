package com.bashworks.ugs.jobinfo.tracker;

import com.bashworks.ugs.jobinfo.kinematics.MachineProfile;
import com.bashworks.ugs.jobinfo.kinematics.SimulationResult;

/**
 * Immutable snapshot representing the exact current state and metrics of the CNC job.
 */
public record JobSnapshot(
        JobState state,
        double totalTimeSeconds,
        double elapsedTimeSeconds,
        double remainingTimeSeconds,
        double progressPercent,
        String etaString,
        int feedOverridePercent,
        int completedCommands,
        int totalCommands,
        String fileName,
        SimulationResult simulationResult,
        MachineProfile machineProfile
) {
    public static JobSnapshot idle(MachineProfile profile) {
        return new JobSnapshot(
                JobState.IDLE,
                0.0,
                0.0,
                0.0,
                0.0,
                "--:--:--",
                100,
                0,
                0,
                "No file loaded",
                SimulationResult.empty(),
                profile != null ? profile : new MachineProfile()
        );
    }
}
