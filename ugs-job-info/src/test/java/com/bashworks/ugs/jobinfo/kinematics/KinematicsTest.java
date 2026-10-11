package com.bashworks.ugs.jobinfo.kinematics;

import com.bashworks.ugs.jobinfo.mqtt.MqttConfig;
import com.bashworks.ugs.jobinfo.mqtt.MqttTelemetryService;
import com.bashworks.ugs.jobinfo.tracker.JobSnapshot;
import com.bashworks.ugs.jobinfo.tracker.JobState;

import java.util.ArrayList;
import java.util.List;


public class KinematicsTest {

    public static void main(String[] args) {
        System.out.println("Running Kinematics Engine Unit Tests...");

        testEffectiveAcceleration();
        testJunctionVelocity();
        testTrapezoidalIntegration();
        testFullSimulation();
        testMqttIntegration();

        System.out.println("ALL KINEMATICS & MQTT TESTS PASSED SUCCESSFULLY!");
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

        // 0 degree collinear (straight line)
        double vStraight = prof.junctionVelocity(1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 500.0);
        assertTrue(Double.isInfinite(vStraight) && vStraight > 0.0, "0 deg straight move must allow infinite/unlimited junction velocity");

        // 15 degree gentle turn
        double rad15 = Math.toRadians(15);
        double v15 = prof.junctionVelocity(1.0, 0.0, 0.0, Math.cos(rad15), Math.sin(rad15), 0.0, 500.0);

        // 90 degree corner between (+X) and (+Y)
        double v90 = prof.junctionVelocity(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 500.0);
        assertTrue(v90 > 0.0 && v90 < 100.0, "90 deg junction velocity should be positive and finite");
        assertTrue(v15 > v90, "15 deg gentle turn velocity (" + v15 + ") must be significantly faster than 90 deg corner (" + v90 + ")");

        // 165 degree sharp turn
        double rad165 = Math.toRadians(165);
        double v165 = prof.junctionVelocity(1.0, 0.0, 0.0, Math.cos(rad165), Math.sin(rad165), 0.0, 500.0);
        assertTrue(v90 > v165, "90 deg corner must allow faster speed than sharp 165 deg turn");

        // 180 degree reversal
        double vRev = prof.junctionVelocity(1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 500.0);
        assertTrue(vRev == 0.0, "180 deg reversal must come to complete stop (0 mm/s)");

        System.out.println("✓ Junction velocity test passed (monotonic angle decrease verified).");
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

    private static void testMqttIntegration() {
        // Test MqttConfig defaults & topic generation
        MqttConfig cfg = new MqttConfig();
        assertTrue(MqttConfig.DEFAULT_PUBLIC_HOST.equals(cfg.getHost()), "Default host should be broker.hivemq.com");
        assertTrue(cfg.getPort() == 1883, "Default port should be 1883");
        assertTrue(cfg.getTopic() != null && cfg.getTopic().startsWith("gbashware/ugs/"), "Default topic should have valid prefix");

        // Test Cloud preset
        cfg.applyHiveMqCloudPreset("abc1234.s1.eu.hivemq.cloud");
        assertTrue(cfg.isUseTls(), "Cloud preset must enable TLS");
        assertTrue(cfg.getPort() == 8883, "Cloud preset port must be 8883");

        // Test JSON Serialization of JobSnapshot
        MachineProfile prof = MachineProfile.standardRouter();
        JobSnapshot snapshot = new JobSnapshot(
                JobState.RUNNING,
                1200.0,
                300.0,
                900.0,
                25.0,
                "15:30",
                100,
                500,
                2000,
                "bracket.nc",
                SimulationResult.empty(),
                prof
        );

        String json = MqttTelemetryService.serializeJson(snapshot);
        assertTrue(json != null && json.startsWith("{") && json.endsWith("}"), "JSON must be well-formed object");
        assertTrue(json.contains("\"state\":\"RUNNING\""), "JSON must contain state");
        assertTrue(json.contains("\"fileName\":\"bracket.nc\""), "JSON must contain fileName");
        assertTrue(json.contains("\"progressPercent\":25.0"), "JSON must contain progressPercent");
        assertTrue(json.contains("\"elapsedSeconds\":300.0"), "JSON must contain elapsedSeconds");
        assertTrue(json.contains("\"remainingSeconds\":900.0"), "JSON must contain remainingSeconds");
        assertTrue(json.contains("\"eta\":\"15:30\""), "JSON must contain eta");

        System.out.println("✓ MQTT Configuration & JSON Telemetry Serialization tests passed.");
    }

    private static void assertTrue(boolean condition, String message) {

        if (!condition) {
            throw new AssertionError("TEST ASSERTION FAILED: " + message);
        }
    }
}
