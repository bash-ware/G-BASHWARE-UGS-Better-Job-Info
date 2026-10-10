package com.bashworks.ugs.jobinfo.kinematics;

import java.util.Objects;

/**
 * Encapsulates the kinematic acceleration and velocity limits of a CNC machine,
 * corresponding directly to controller settings ($110-$112, $120-$122, $11, $12).
 */
public class MachineProfile {
    // Accelerations in mm/s^2 ($120, $121, $122)
    private double accelX;
    private double accelY;
    private double accelZ;

    // Maximum velocities in mm/min ($110, $111, $112)
    private double maxRateX;
    private double maxRateY;
    private double maxRateZ;

    // Cornering junction deviation in mm ($11)
    private double junctionDeviation;

    // Arc segment tolerance in mm ($12)
    private double arcTolerance;

    // Estimated delay for spindle spin-up upon M3/M4 in seconds
    private double spindleSpinUpDelaySeconds;

    // Name or source of this profile (e.g. "Live Controller (GRBL)", "Preset: Standard Router")
    private String name;

    public MachineProfile() {
        // Defaults aligned with common GRBL CNC routers (e.g. Shapeoko, X-Carve, 3018, LeadCNC)
        this("Default CNC Profile",
                500.0, 500.0, 150.0,    // Accel: X, Y, Z (mm/s^2)
                5000.0, 5000.0, 1200.0, // Max rate: X, Y, Z (mm/min)
                0.010,                  // Junction deviation ($11)
                0.002,                  // Arc tolerance ($12)
                1.5);                   // Spindle spin-up delay (s)
    }

    public MachineProfile(String name,
                          double accelX, double accelY, double accelZ,
                          double maxRateX, double maxRateY, double maxRateZ,
                          double junctionDeviation, double arcTolerance,
                          double spindleSpinUpDelaySeconds) {
        this.name = name;
        this.accelX = Math.max(10.0, accelX);
        this.accelY = Math.max(10.0, accelY);
        this.accelZ = Math.max(5.0, accelZ);
        this.maxRateX = Math.max(60.0, maxRateX);
        this.maxRateY = Math.max(60.0, maxRateY);
        this.maxRateZ = Math.max(60.0, maxRateZ);
        this.junctionDeviation = Math.max(0.001, junctionDeviation);
        this.arcTolerance = Math.max(0.0005, arcTolerance);
        this.spindleSpinUpDelaySeconds = Math.max(0.0, spindleSpinUpDelaySeconds);
    }

    public static MachineProfile standardRouter() {
        return new MachineProfile("Standard CNC Router",
                500.0, 500.0, 150.0,
                5000.0, 5000.0, 1200.0,
                0.010, 0.002, 1.5);
    }

    public static MachineProfile fastEngraver() {
        return new MachineProfile("Fast Engraver / Laser",
                1500.0, 1500.0, 500.0,
                10000.0, 10000.0, 3000.0,
                0.015, 0.002, 0.5);
    }

    public static MachineProfile heavyMill() {
        return new MachineProfile("Heavy Milling / Metal",
                250.0, 250.0, 100.0,
                2500.0, 2500.0, 800.0,
                0.008, 0.002, 2.5);
    }

    /**
     * Calculates the effective acceleration along a 3D displacement vector.
     * In multi-axis motion, the vector acceleration is constrained by the axis
     * that reaches its physical limit first: a_eff = min(A_i / |u_i|) for u_i != 0.
     *
     * @param dx displacement X (mm)
     * @param dy displacement Y (mm)
     * @param dz displacement Z (mm)
     * @return effective acceleration along path in mm/s^2
     */
    public double effectiveAcceleration(double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) {
            return Math.min(accelX, Math.min(accelY, accelZ));
        }

        double ux = Math.abs(dx / len);
        double uy = Math.abs(dy / len);
        double uz = Math.abs(dz / len);

        double eff = Double.POSITIVE_INFINITY;
        if (ux > 1e-6) eff = Math.min(eff, accelX / ux);
        if (uy > 1e-6) eff = Math.min(eff, accelY / uy);
        if (uz > 1e-6) eff = Math.min(eff, accelZ / uz);

        return Double.isInfinite(eff) ? accelX : eff;
    }

    /**
     * Calculates the maximum velocity along a 3D displacement vector in mm/s.
     * Rapid moves (G0) and fast feeds are capped by: v_eff = min(V_max_i / |u_i|).
     *
     * @param dx displacement X (mm)
     * @param dy displacement Y (mm)
     * @param dz displacement Z (mm)
     * @return maximum path velocity in mm/s
     */
    public double effectiveMaxVelocity(double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Max rates converted to mm/s (from mm/min)
        double vx = maxRateX / 60.0;
        double vy = maxRateY / 60.0;
        double vz = maxRateZ / 60.0;

        if (len < 1e-9) {
            return Math.min(vx, Math.min(vy, vz));
        }

        double ux = Math.abs(dx / len);
        double uy = Math.abs(dy / len);
        double uz = Math.abs(dz / len);

        double eff = Double.POSITIVE_INFINITY;
        if (ux > 1e-6) eff = Math.min(eff, vx / ux);
        if (uy > 1e-6) eff = Math.min(eff, vy / uy);
        if (uz > 1e-6) eff = Math.min(eff, vz / uz);

        return Double.isInfinite(eff) ? vx : eff;
    }

    /**
     * Computes the maximum allowable cornering velocity at a junction between two motion segments
     * using the GRBL junction deviation centripetal acceleration model.
     *
     * @param u1x unit vector X of incoming move
     * @param u1y unit vector Y of incoming move
     * @param u1z unit vector Z of incoming move
     * @param u2x unit vector X of outgoing move
     * @param u2y unit vector Y of outgoing move
     * @param u2z unit vector Z of outgoing move
     * @param accel effective acceleration at junction in mm/s^2
     * @return maximum junction speed in mm/s
     */
    public double junctionVelocity(double u1x, double u1y, double u1z,
                                    double u2x, double u2y, double u2z,
                                    double accel) {
        // Dot product of unit vectors: cos(theta) = - (u1 . u2) in junction direction change terms
        // In GRBL: cos_theta = - u1x * u2x - u1y * u2y - u1z * u2z
        double cosTheta = - (u1x * u2x + u1y * u2y + u1z * u2z);

        // Near-collinear (moving in same direction): cosTheta is close to -1
        if (cosTheta < -0.999999) {
            // Full speed continuous transition
            return Double.POSITIVE_INFINITY;
        }

        // Full 180 reversal: cosTheta is close to +1
        if (cosTheta > 0.999999) {
            return 0.0;
        }

        // sin(theta/2) = sqrt((1 + cos_theta) / 2)
        double sinHalfTheta = Math.sqrt((1.0 + cosTheta) * 0.5);
        if (sinHalfTheta > 0.9999) {
            return 0.0;
        }

        // Equivalent turning radius R = (junctionDeviation * sinHalfTheta) / (1 - sinHalfTheta)
        double rCorner = (junctionDeviation * sinHalfTheta) / (1.0 - sinHalfTheta);

        // Centripetal acceleration: v_junction = sqrt(accel * R)
        double vJunction = Math.sqrt(accel * rCorner);
        return Math.max(0.0, vJunction);
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getAccelX() { return accelX; }
    public void setAccelX(double accelX) { this.accelX = accelX; }

    public double getAccelY() { return accelY; }
    public void setAccelY(double accelY) { this.accelY = accelY; }

    public double getAccelZ() { return accelZ; }
    public void setAccelZ(double accelZ) { this.accelZ = accelZ; }

    public double getMaxRateX() { return maxRateX; }
    public void setMaxRateX(double maxRateX) { this.maxRateX = maxRateX; }

    public double getMaxRateY() { return maxRateY; }
    public void setMaxRateY(double maxRateY) { this.maxRateY = maxRateY; }

    public double getMaxRateZ() { return maxRateZ; }
    public void setMaxRateZ(double maxRateZ) { this.maxRateZ = maxRateZ; }

    public double getJunctionDeviation() { return junctionDeviation; }
    public void setJunctionDeviation(double junctionDeviation) { this.junctionDeviation = junctionDeviation; }

    public double getArcTolerance() { return arcTolerance; }
    public void setArcTolerance(double arcTolerance) { this.arcTolerance = arcTolerance; }

    public double getSpindleSpinUpDelaySeconds() { return spindleSpinUpDelaySeconds; }
    public void setSpindleSpinUpDelaySeconds(double delay) { this.spindleSpinUpDelaySeconds = delay; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MachineProfile that = (MachineProfile) o;
        return Double.compare(that.accelX, accelX) == 0 &&
                Double.compare(that.accelY, accelY) == 0 &&
                Double.compare(that.accelZ, accelZ) == 0 &&
                Double.compare(that.maxRateX, maxRateX) == 0 &&
                Double.compare(that.maxRateY, maxRateY) == 0 &&
                Double.compare(that.maxRateZ, maxRateZ) == 0 &&
                Double.compare(that.junctionDeviation, junctionDeviation) == 0 &&
                Double.compare(that.arcTolerance, arcTolerance) == 0 &&
                Double.compare(that.spindleSpinUpDelaySeconds, spindleSpinUpDelaySeconds) == 0 &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accelX, accelY, accelZ, maxRateX, maxRateY, maxRateZ,
                junctionDeviation, arcTolerance, spindleSpinUpDelaySeconds, name);
    }

    @Override
    public String toString() {
        return String.format("%s (Acc: [%.0f, %.0f, %.0f] mm/s², Max: [%.0f, %.0f, %.0f] mm/min, Dev: %.3f mm)",
                name, accelX, accelY, accelZ, maxRateX, maxRateY, maxRateZ, junctionDeviation);
    }
}

