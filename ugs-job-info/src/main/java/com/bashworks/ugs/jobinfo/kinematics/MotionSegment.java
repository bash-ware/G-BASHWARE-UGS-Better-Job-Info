package com.bashworks.ugs.jobinfo.kinematics;

/**
 * Represents a single planned motion or dwell block in the G-code program.
 */
public class MotionSegment {
    public enum Type {
        RAPID,  // G0
        CUT,    // G1
        ARC,    // G2 / G3
        DWELL   // G4
    }

    private final int lineNumber;
    private final int commandIndex;
    private final Type type;

    // Spatial coordinates (mm)
    private final double startX, startY, startZ;
    private final double endX, endY, endZ;
    private final double dx, dy, dz;
    private final double length; // Euclidean length (mm)

    // Unit direction vector
    private final double ux, uy, uz;

    // Kinematic limits for this segment
    private double feedRateMmMin;      // Programmed feedrate in mm/min
    private double nominalSpeed;       // Cruise speed ceiling in mm/s
    private double accel;              // Effective acceleration along path in mm/s^2

    // Lookahead planned velocities
    private double maxJunctionEntry;   // Maximum entry speed allowed by cornering junction (mm/s)
    private double entrySpeed;         // Actual planned entry speed (mm/s)
    private double exitSpeed;          // Actual planned exit speed (mm/s)

    // Non-motion delays
    private double dwellSeconds;

    // Time integration results
    private double durationSeconds;
    private double cumulativeTimeSeconds;

    public MotionSegment(int lineNumber, int commandIndex, Type type,
                         double startX, double startY, double startZ,
                         double endX, double endY, double endZ,
                         double feedRateMmMin, MachineProfile profile) {
        this.lineNumber = lineNumber;
        this.commandIndex = commandIndex;
        this.type = type;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;

        this.dx = endX - startX;
        this.dy = endY - startY;
        this.dz = endZ - startZ;
        this.length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (this.length > 1e-9) {
            this.ux = dx / length;
            this.uy = dy / length;
            this.uz = dz / length;
        } else {
            this.ux = 0.0;
            this.uy = 0.0;
            this.uz = 0.0;
        }

        this.feedRateMmMin = feedRateMmMin;
        this.dwellSeconds = 0.0;

        if (type == Type.DWELL) {
            this.accel = 0.0;
            this.nominalSpeed = 0.0;
        } else {
            this.accel = profile.effectiveAcceleration(dx, dy, dz);
            double maxMachineSpeed = profile.effectiveMaxVelocity(dx, dy, dz);

            if (type == Type.RAPID) {
                this.nominalSpeed = maxMachineSpeed;
            } else {
                // Cut or Arc: speed is min(programmed feed, machine max velocity)
                double progSpeed = feedRateMmMin > 0 ? (feedRateMmMin / 60.0) : maxMachineSpeed;
                this.nominalSpeed = Math.min(progSpeed, maxMachineSpeed);
            }
        }

        this.maxJunctionEntry = this.nominalSpeed;
        this.entrySpeed = 0.0;
        this.exitSpeed = 0.0;
        this.durationSeconds = 0.0;
        this.cumulativeTimeSeconds = 0.0;
    }

    public static MotionSegment createDwell(int lineNumber, int commandIndex, double dwellSeconds) {
        MotionSegment seg = new MotionSegment(lineNumber, commandIndex, Type.DWELL,
                0, 0, 0, 0, 0, 0, 0, new MachineProfile());
        seg.dwellSeconds = Math.max(0.0, dwellSeconds);
        seg.durationSeconds = seg.dwellSeconds;
        return seg;
    }

    // Getters and Setters
    public int getLineNumber() { return lineNumber; }
    public int getCommandIndex() { return commandIndex; }
    public Type getType() { return type; }

    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getStartZ() { return startZ; }

    public double getEndX() { return endX; }
    public double getEndY() { return endY; }
    public double getEndZ() { return endZ; }

    public double getDx() { return dx; }
    public double getDy() { return dy; }
    public double getDz() { return dz; }
    public double getLength() { return length; }

    public double getUx() { return ux; }
    public double getUy() { return uy; }
    public double getUz() { return uz; }

    public double getFeedRateMmMin() { return feedRateMmMin; }
    public void setFeedRateMmMin(double feedRateMmMin) { this.feedRateMmMin = feedRateMmMin; }

    public double getNominalSpeed() { return nominalSpeed; }
    public void setNominalSpeed(double nominalSpeed) { this.nominalSpeed = nominalSpeed; }

    public double getAccel() { return accel; }
    public void setAccel(double accel) { this.accel = accel; }

    public double getMaxJunctionEntry() { return maxJunctionEntry; }
    public void setMaxJunctionEntry(double maxJunctionEntry) { this.maxJunctionEntry = maxJunctionEntry; }

    public double getEntrySpeed() { return entrySpeed; }
    public void setEntrySpeed(double entrySpeed) { this.entrySpeed = entrySpeed; }

    public double getExitSpeed() { return exitSpeed; }
    public void setExitSpeed(double exitSpeed) { this.exitSpeed = exitSpeed; }

    public double getDwellSeconds() { return dwellSeconds; }
    public void setDwellSeconds(double dwellSeconds) { this.dwellSeconds = dwellSeconds; }

    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }

    public double getCumulativeTimeSeconds() { return cumulativeTimeSeconds; }
    public void setCumulativeTimeSeconds(double cumulativeTimeSeconds) { this.cumulativeTimeSeconds = cumulativeTimeSeconds; }
}
