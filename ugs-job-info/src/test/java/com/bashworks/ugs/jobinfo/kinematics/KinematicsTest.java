package com.bashworks.ugs.jobinfo.kinematics;

import java.util.ArrayList;
import java.util.List;

public class KinematicsTest {

    public static void main(String[] args) {
        System.out.println("Running Kinematics Engine Unit Tests...");

        testEffectiveAcceleration();
        testJunctionVelocity();
        testTrapezoidalIntegration();
        testFullSimulation();

        System.out.println("ALL KINEMATICS TESTS PASSED SUCCESSFULLY!");
    }

    private static void testEffectiveAcceleration() {
        MachineProfile prof = new MachineProfile("Test", 500.0, 500.0, 150.0, 5000.0, 5000.0, 1200.0, 0.010, 0.002, 1.5);

        // Pure X move
        double ax = prof.effectiveAcceleration(100.0, 0.0, 0.0);
        assertTrue(Math.abs(ax - 500.0) < 1e-4, "Pure X acceleration should equal X limit (500)");

        // Pure Z move
        double az = prof.effectiveAcceleration(0.0, 0.0, 10.0);
        assertTrue(Math.abs(az - 150.0) < 1e-4, "Pure Z acceleration should equal Z limit (150)");

        // 45 degree XY move
        double axy = prof.effectiveAcceleration(100.0, 100.0, 0.0);
        // unit vector is (1/sqrt(2), 1/sqrt(2)) -> 500 * sqrt(2) approx 707.1 mm/s^2
        assertTrue(axy > 500.0, "Diagonal acceleration should scale by component direction");
        System.out.println("✓ Effective acceleration test passed.");
    }

    private static void testJunctionVelocity() {
        MachineProfile prof = MachineProfile.standardRouter();

        // 90 degree corner between (+X) and (+Y)
        double vJunc = prof.junctionVelocity(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 500.0);
        assertTrue(vJunc > 0.0 && vJunc < 100.0, "90 deg junction velocity should be positive and finite");

        // 180 degree reversal
        double vRev = prof.junctionVelocity(1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 500.0);
        assertTrue(vRev == 0.0, "180 deg reversal must come to complete stop (0 mm/s)");

        System.out.println("✓ Junction velocity test passed.");
    }

    private static void testTrapezoidalIntegration() {
        MachineProfile prof = new MachineProfile("Test", 500.0, 500.0, 500.0, 6000.0, 6000.0, 6000.0, 0.010, 0.002, 0.0);
        // Move: length = 100 mm, max speed = 100 mm/s (6000 mm/min), accel = 500 mm/s^2
        // Time to accel from 0 to 100: t_acc = 100 / 500 = 0.2 s, d_acc = 0.5 * 500 * 0.04 = 10 mm
        // Time to decel from 100 to 0: t_dec = 0.2 s, d_dec = 10 mm
        // Cruise distance: 80 mm at 100 mm/s -> t_cruise = 0.8 s
        // Expected total time: 0.2 + 0.8 + 0.2 = 1.2 s

        List<MotionSegment> segs = new ArrayList<>();
        MotionSegment seg = new MotionSegment(1, 0, MotionSegment.Type.CUT, 0, 0, 0, 100, 0, 0, 6000.0, prof);
        segs.add(seg);

        LookaheadPlanner.plan(segs, prof);

        double actualTime = seg.getDurationSeconds();
        assertTrue(Math.abs(actualTime - 1.20) < 0.01, "Expected 1.20s for 100mm trapezoidal move, got: " + actualTime);
        System.out.println("✓ Trapezoidal velocity integration test passed (expected 1.20s, got " + actualTime + "s).");
    }

    private static void testFullSimulation() {
        MachineProfile prof = MachineProfile.standardRouter();
        String gcode =
                "G21\n" +
                "G90\n" +
                "G0 X0 Y0 Z5\n" +
                "G1 Z-1 F300\n" +
                "G1 X100 Y0 F3000\n" +
                "G1 X100 Y100 F3000\n" +
                "G4 P2.0\n" +
                "G0 Z5\n";

        try {
            SimulationResult result = KinematicsSimulator.simulateString(gcode, prof);
            assertTrue(result.getTotalDurationSeconds() > 2.0, "Total duration must include dwell and motion");
            assertTrue(result.getDwellDurationSeconds() == 2.0, "Dwell duration must be exactly 2.0s");
            assertTrue(result.getTotalCommands() == 8, "Expected 8 commands parsed");
            assertTrue(result.getTotalDistanceMm() > 200.0, "Distance should include all segments");
            System.out.println("✓ Full G-code simulation test passed (Total duration: " + String.format("%.2f", result.getTotalDurationSeconds()) + "s).");
        } catch (Exception e) {
            throw new RuntimeException("Simulation failed", e);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("TEST ASSERTION FAILED: " + message);
        }
    }
}
