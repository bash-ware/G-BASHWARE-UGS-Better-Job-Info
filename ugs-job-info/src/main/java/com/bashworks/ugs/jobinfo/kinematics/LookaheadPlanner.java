package com.bashworks.ugs.jobinfo.kinematics;

import java.util.List;

/**
 * Two-pass lookahead velocity planner based on GRBL kinematics and junction deviation.
 * Plans entry/exit velocities for every motion block and integrates exact trapezoidal/triangular
 * duration down to sub-millisecond precision.
 */
public final class LookaheadPlanner {

    private LookaheadPlanner() {}

    /**
     * Executes the lookahead planning and time integration across the motion segments.
     *
     * @param segments list of motion segments in sequential program order
     * @param profile machine acceleration and velocity parameters
     */
    public static void plan(List<MotionSegment> segments, MachineProfile profile) {
        if (segments == null || segments.isEmpty()) {
            return;
        }

        int n = segments.size();

        // -------------------------------------------------------------
        // Step 1: Precompute maximum allowable junction speeds
        // -------------------------------------------------------------
        segments.get(0).setMaxJunctionEntry(0.0);

        for (int i = 1; i < n; i++) {
            MotionSegment prev = segments.get(i - 1);
            MotionSegment curr = segments.get(i);

            if (prev.getType() == MotionSegment.Type.DWELL || curr.getType() == MotionSegment.Type.DWELL
                    || prev.getLength() < 1e-9 || curr.getLength() < 1e-9) {
                curr.setMaxJunctionEntry(0.0);
            } else {
                double a = Math.min(prev.getAccel(), curr.getAccel());
                double vJunc = profile.junctionVelocity(
                        prev.getUx(), prev.getUy(), prev.getUz(),
                        curr.getUx(), curr.getUy(), curr.getUz(),
                        a);

                double vMax = Math.min(vJunc, Math.min(prev.getNominalSpeed(), curr.getNominalSpeed()));
                curr.setMaxJunctionEntry(vMax);
            }
        }

        // -------------------------------------------------------------
        // Step 2: Reverse Pass (Deceleration Planning from End to Start)
        // -------------------------------------------------------------
        segments.get(n - 1).setExitSpeed(0.0);

        for (int i = n - 1; i >= 0; i--) {
            MotionSegment curr = segments.get(i);

            if (curr.getType() == MotionSegment.Type.DWELL) {
                curr.setEntrySpeed(0.0);
                if (i > 0) {
                    segments.get(i - 1).setExitSpeed(0.0);
                }
                continue;
            }

            if (curr.getLength() < 1e-9) {
                curr.setEntrySpeed(curr.getExitSpeed());
                if (i > 0) {
                    segments.get(i - 1).setExitSpeed(curr.getEntrySpeed());
                }
                continue;
            }

            // Maximum speed we could be entering at, such that we can decelerate to exitSpeed:
            // v_entry^2 <= v_exit^2 + 2 * a * L
            double vExit = curr.getExitSpeed();
            double maxReachableEntry = Math.sqrt(vExit * vExit + 2.0 * curr.getAccel() * curr.getLength());

            double entry = Math.min(curr.getMaxJunctionEntry(), Math.min(curr.getNominalSpeed(), maxReachableEntry));
            curr.setEntrySpeed(entry);

            if (i > 0) {
                segments.get(i - 1).setExitSpeed(entry);
            }
        }

        // -------------------------------------------------------------
        // Step 3: Forward Pass (Acceleration Planning from Start to End)
        // -------------------------------------------------------------
        segments.get(0).setEntrySpeed(0.0);

        for (int i = 0; i < n; i++) {
            MotionSegment curr = segments.get(i);

            if (curr.getType() == MotionSegment.Type.DWELL) {
                curr.setExitSpeed(0.0);
                if (i + 1 < n) {
                    segments.get(i + 1).setEntrySpeed(0.0);
                }
                continue;
            }

            if (curr.getLength() < 1e-9) {
                curr.setExitSpeed(curr.getEntrySpeed());
                if (i + 1 < n) {
                    segments.get(i + 1).setEntrySpeed(curr.getExitSpeed());
                }
                continue;
            }

            // Maximum speed we can reach starting from entrySpeed:
            // v_exit^2 <= v_entry^2 + 2 * a * L
            double vEntry = curr.getEntrySpeed();
            double maxReachableExit = Math.sqrt(vEntry * vEntry + 2.0 * curr.getAccel() * curr.getLength());

            double exit = Math.min(curr.getExitSpeed(), maxReachableExit);
            curr.setExitSpeed(exit);

            if (i + 1 < n) {
                segments.get(i + 1).setEntrySpeed(exit);
            }
        }

        // -------------------------------------------------------------
        // Step 4: Time Integration & Cumulative Timeline Generation
        // -------------------------------------------------------------
        double cumulativeTime = 0.0;

        for (MotionSegment seg : segments) {
            double duration;

            if (seg.getType() == MotionSegment.Type.DWELL) {
                duration = seg.getDwellSeconds();
            } else if (seg.getLength() < 1e-9) {
                duration = 0.0;
            } else {
                duration = integrateSegmentDuration(seg);
            }

            seg.setDurationSeconds(duration);
            cumulativeTime += duration;
            seg.setCumulativeTimeSeconds(cumulativeTime);
        }
    }

    /**
     * Integrates the duration of a motion segment using trapezoidal or triangular velocity profiles.
     */
    private static double integrateSegmentDuration(MotionSegment seg) {
        double l = seg.getLength();
        double a = seg.getAccel();
        double v0 = seg.getEntrySpeed();
        double v1 = seg.getExitSpeed();
        double vc = seg.getNominalSpeed();

        if (a <= 0.0 || vc <= 0.0) {
            return vc > 0.0 ? (l / vc) : 0.0;
        }

        // Acceleration and deceleration distances to cruise speed vc
        double dAcc = Math.max(0.0, (vc * vc - v0 * v0) / (2.0 * a));
        double dDec = Math.max(0.0, (vc * vc - v1 * v1) / (2.0 * a));

        if (dAcc + dDec <= l) {
            // Trapezoidal velocity profile (reaches full cruise speed vc)
            double dCruise = l - dAcc - dDec;
            double tAcc = (vc - v0) / a;
            double tCruise = dCruise / vc;
            double tDec = (vc - v1) / a;

            return Math.max(0.0, tAcc) + Math.max(0.0, tCruise) + Math.max(0.0, tDec);
        } else {
            // Triangular velocity profile (move too short to reach vc)
            // 2 * a * l = (vPeak^2 - v0^2) + (vPeak^2 - v1^2) => 2 * vPeak^2 = 2 * a * l + v0^2 + v1^2
            double vPeakSq = (2.0 * a * l + v0 * v0 + v1 * v1) * 0.5;
            double vPeak = Math.sqrt(Math.max(0.0, vPeakSq));
            vPeak = Math.min(vPeak, vc);

            double tAcc = Math.max(0.0, (vPeak - v0) / a);
            double tDec = Math.max(0.0, (vPeak - v1) / a);

            return tAcc + tDec;
        }
    }
}
