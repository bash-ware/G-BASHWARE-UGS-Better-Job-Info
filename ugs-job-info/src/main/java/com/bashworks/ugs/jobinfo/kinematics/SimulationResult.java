package com.bashworks.ugs.jobinfo.kinematics;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the results of a kinematics simulation over a G-code program.
 */
public class SimulationResult {
    private final double totalDurationSeconds;
    private final double rapidDurationSeconds;
    private final double cutDurationSeconds;
    private final double dwellDurationSeconds;

    private final double totalDistanceMm;
    private final double rapidDistanceMm;
    private final double cutDistanceMm;

    private final double minX, maxX;
    private final double minY, maxY;
    private final double minZ, maxZ;

    private final int totalSegments;
    private final int totalCommands;

    // Direct O(1) array mapping commandIndex -> cumulative completion time in seconds
    private final double[] commandCompletionTimes;
    private final List<MotionSegment> segments;

    public SimulationResult(double totalDurationSeconds,
                            double rapidDurationSeconds,
                            double cutDurationSeconds,
                            double dwellDurationSeconds,
                            double totalDistanceMm,
                            double rapidDistanceMm,
                            double cutDistanceMm,
                            double minX, double maxX,
                            double minY, double maxY,
                            double minZ, double maxZ,
                            int totalSegments,
                            int totalCommands,
                            double[] commandCompletionTimes,
                            List<MotionSegment> segments) {
        this.totalDurationSeconds = totalDurationSeconds;
        this.rapidDurationSeconds = rapidDurationSeconds;
        this.cutDurationSeconds = cutDurationSeconds;
        this.dwellDurationSeconds = dwellDurationSeconds;
        this.totalDistanceMm = totalDistanceMm;
        this.rapidDistanceMm = rapidDistanceMm;
        this.cutDistanceMm = cutDistanceMm;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.totalSegments = totalSegments;
        this.totalCommands = totalCommands;
        this.commandCompletionTimes = commandCompletionTimes != null ? commandCompletionTimes : new double[0];
        this.segments = segments != null ? Collections.unmodifiableList(segments) : Collections.emptyList();
    }

    public static SimulationResult empty() {
        return new SimulationResult(0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, new double[0], Collections.emptyList());
    }

    /**
     * Returns the simulated elapsed time up to a specific completed command index.
     */
    public double getTimeAtCommand(int commandIndex) {
        if (commandCompletionTimes.length == 0 || commandIndex < 0) {
            return 0.0;
        }
        if (commandIndex >= commandCompletionTimes.length) {
            return totalDurationSeconds;
        }
        return commandCompletionTimes[commandIndex];
    }

    /**
     * Returns the true kinematic percentage of the job completed at a specific command index.
     */
    public double getProgressPercentAtCommand(int commandIndex) {
        if (totalDurationSeconds <= 0.0 || commandIndex <= 0) {
            return 0.0;
        }
        double time = getTimeAtCommand(commandIndex);
        return Math.min(100.0, Math.max(0.0, (time / totalDurationSeconds) * 100.0));
    }

    // Getters
    public double getTotalDurationSeconds() { return totalDurationSeconds; }
    public double getRapidDurationSeconds() { return rapidDurationSeconds; }
    public double getCutDurationSeconds() { return cutDurationSeconds; }
    public double getDwellDurationSeconds() { return dwellDurationSeconds; }

    public double getTotalDistanceMm() { return totalDistanceMm; }
    public double getRapidDistanceMm() { return rapidDistanceMm; }
    public double getCutDistanceMm() { return cutDistanceMm; }

    public double getMinX() { return minX; }
    public double getMaxX() { return maxX; }
    public double getMinY() { return minY; }
    public double getMaxY() { return maxY; }
    public double getMinZ() { return minZ; }
    public double getMaxZ() { return maxZ; }

    public int getTotalSegments() { return totalSegments; }
    public int getTotalCommands() { return totalCommands; }
    public List<MotionSegment> getSegments() { return segments; }
}
