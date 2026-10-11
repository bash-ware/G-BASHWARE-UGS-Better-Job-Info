package com.bashworks.ugs.jobinfo.kinematics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance G-code parser and kinematics simulator.
 * Parses G-code programs, maintains modal states (units, distance modes, feed rates, coordinates),
 * and feeds motion blocks into LookaheadPlanner.
 */
public final class KinematicsSimulator {

    // Regex pattern to extract G-code words (e.g. G1, X10.5, Y-20, F500)
    private static final Pattern WORD_PATTERN = Pattern.compile("([A-Za-z])([+-]?\\d*\\.?\\d+)");

    private KinematicsSimulator() {}

    /**
     * Parses a G-code file and computes exact kinematics timeline.
     */
    public static SimulationResult simulateFile(File file, MachineProfile profile) throws Exception {
        if (file == null || !file.exists() || file.length() == 0) {
            return SimulationResult.empty();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8), 65536)) {
            return simulateReader(reader, profile);
        }
    }

    /**
     * Parses a G-code string content and computes exact kinematics timeline.
     */
    public static SimulationResult simulateString(String gcode, MachineProfile profile) throws Exception {
        if (gcode == null || gcode.trim().isEmpty()) {
            return SimulationResult.empty();
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(gcode))) {
            return simulateReader(reader, profile);
        }
    }

    /**
     * Main simulation loop over lines of G-code.
     */
    public static SimulationResult simulateReader(BufferedReader reader, MachineProfile profile) throws Exception {
        List<MotionSegment> segments = new ArrayList<>(16384);
        List<Integer> commandToSegmentMap = new ArrayList<>(16384);

        // Modal machine state
        double currentX = 0.0;
        double currentY = 0.0;
        double currentZ = 0.0;
        double feedRateMmMin = 1000.0; // Default nominal feed
        double unitFactor = 1.0;       // 1.0 = mm (G21), 25.4 = inches (G20)
        boolean absoluteDistance = true; // true = G90, false = G91
        MotionSegment.Type modalMotion = MotionSegment.Type.RAPID; // G0 initial

        // Statistics accumulators
        double minX = 0.0, maxX = 0.0;
        double minY = 0.0, maxY = 0.0;
        double minZ = 0.0, maxZ = 0.0;
        boolean hasCoordinates = false;

        String line;
        int lineNumber = 0;
        int commandIndex = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String cleanLine = stripComments(line).trim();
            if (cleanLine.isEmpty()) {
                continue;
            }

            int currentCmdIdx = commandIndex++;

            // Parse words
            Matcher matcher = WORD_PATTERN.matcher(cleanLine);

            boolean hasMotionCode = false;
            MotionSegment.Type lineMotionType = modalMotion;
            boolean hasX = false, hasY = false, hasZ = false;
            double targetX = currentX;
            double targetY = currentY;
            double targetZ = currentZ;

            boolean hasI = false, hasJ = false, hasR = false;
            double iVal = 0.0, jVal = 0.0, rVal = 0.0;
            boolean hasP = false, hasS = false;
            double pVal = 0.0;

            boolean isSpindleStart = false;

            while (matcher.find()) {
                char letter = Character.toUpperCase(matcher.group(1).charAt(0));
                double val = Double.parseDouble(matcher.group(2));

                switch (letter) {
                    case 'G':
                        int gCode = (int) Math.round(val * 10); // e.g. 0, 10, 20, 900, 910
                        if (gCode == 0) { // G0
                            lineMotionType = MotionSegment.Type.RAPID;
                            modalMotion = MotionSegment.Type.RAPID;
                            hasMotionCode = true;
                        } else if (gCode == 10) { // G1
                            lineMotionType = MotionSegment.Type.CUT;
                            modalMotion = MotionSegment.Type.CUT;
                            hasMotionCode = true;
                        } else if (gCode == 20) { // G2 (Arc CW)
                            lineMotionType = MotionSegment.Type.ARC;
                            modalMotion = MotionSegment.Type.ARC;
                            hasMotionCode = true;
                        } else if (gCode == 30) { // G3 (Arc CCW)
                            lineMotionType = MotionSegment.Type.ARC;
                            modalMotion = MotionSegment.Type.ARC;
                            hasMotionCode = true;
                        } else if (gCode == 40) { // G4 Dwell
                            lineMotionType = MotionSegment.Type.DWELL;
                            hasMotionCode = true;
                        } else if (gCode == 200) { // G20 (Inches)
                            unitFactor = 25.4;
                        } else if (gCode == 210) { // G21 (Millimeters)
                            unitFactor = 1.0;
                        } else if (gCode == 900) { // G90 (Absolute)
                            absoluteDistance = true;
                        } else if (gCode == 910) { // G91 (Incremental)
                            absoluteDistance = false;
                        }
                        break;

                    case 'M':
                        int mCode = (int) Math.round(val);
                        if (mCode == 3 || mCode == 4) { // Spindle ON
                            isSpindleStart = true;
                        }
                        break;

                    case 'F':
                        feedRateMmMin = val * unitFactor;
                        break;

                    case 'X':
                        hasX = true;
                        double xMm = val * unitFactor;
                        targetX = absoluteDistance ? xMm : (currentX + xMm);
                        break;

                    case 'Y':
                        hasY = true;
                        double yMm = val * unitFactor;
                        targetY = absoluteDistance ? yMm : (currentY + yMm);
                        break;

                    case 'Z':
                        hasZ = true;
                        double zMm = val * unitFactor;
                        targetZ = absoluteDistance ? zMm : (currentZ + zMm);
                        break;

                    case 'I':
                        hasI = true;
                        iVal = val * unitFactor;
                        break;

                    case 'J':
                        hasJ = true;
                        jVal = val * unitFactor;
                        break;

                    case 'R':
                        hasR = true;
                        rVal = val * unitFactor;
                        break;

                    case 'P':
                        hasP = true;
                        pVal = val;
                        break;

                    case 'S':
                        hasS = true;
                        break;
                }
            }

            // Handle spindle spin-up dwell
            if (isSpindleStart && profile.getSpindleSpinUpDelaySeconds() > 0) {
                MotionSegment dwell = MotionSegment.createDwell(lineNumber, currentCmdIdx, profile.getSpindleSpinUpDelaySeconds());
                segments.add(dwell);
                commandToSegmentMap.add(segments.size() - 1);
            }

            // Handle explicit dwell G4
            if (lineMotionType == MotionSegment.Type.DWELL) {
                double dwellSec = pVal;
                // If P > 1000 and has no decimal, some CAM posts use milliseconds
                if (pVal > 1000.0 && Math.floor(pVal) == pVal) {
                    dwellSec = pVal / 1000.0;
                }
                MotionSegment dwell = MotionSegment.createDwell(lineNumber, currentCmdIdx, dwellSec);
                segments.add(dwell);
                commandToSegmentMap.add(segments.size() - 1);
                continue;
            }

            // Check if coordinates moved
            if (hasX || hasY || hasZ) {
                // Update bounds
                if (!hasCoordinates) {
                    minX = maxX = targetX;
                    minY = maxY = targetY;
                    minZ = maxZ = targetZ;
                    hasCoordinates = true;
                } else {
                    minX = Math.min(minX, targetX);
                    maxX = Math.max(maxX, targetX);
                    minY = Math.min(minY, targetY);
                    maxY = Math.max(maxY, targetY);
                    minZ = Math.min(minZ, targetZ);
                    maxZ = Math.max(maxZ, targetZ);
                }

                // Create motion segment
                if (lineMotionType == MotionSegment.Type.ARC && (hasI || hasJ || hasR)) {
                    // Compute arc length and radial velocity limit
                    double dx = targetX - currentX;
                    double dy = targetY - currentY;
                    double chord = Math.sqrt(dx * dx + dy * dy);
                    double radius = hasR ? Math.abs(rVal) : Math.sqrt(iVal * iVal + jVal * jVal);

                    double arcLength = chord;
                    if (radius > 1e-4) {
                        double sinHalf = Math.min(1.0, chord / (2.0 * radius));
                        double dTheta = 2.0 * Math.asin(sinHalf);
                        if (chord < 1e-4 && (hasI || hasJ)) {
                            // Full circle
                            dTheta = 2.0 * Math.PI;
                        }
                        arcLength = Math.max(chord, radius * dTheta);
                    }

                    // Add 3D Z ramp if helical arc
                    double dz = targetZ - currentZ;
                    double totalArcLength = Math.sqrt(arcLength * arcLength + dz * dz);

                    MotionSegment seg = new MotionSegment(lineNumber, currentCmdIdx, MotionSegment.Type.ARC,
                            currentX, currentY, currentZ, targetX, targetY, targetZ,
                            feedRateMmMin, profile);
                    seg.setLength(totalArcLength);

                    // Radial acceleration cap for arcs: a_centripetal = v^2 / R <= a_eff => v <= sqrt(a_eff * R)
                    if (radius > 1e-4) {
                        double maxArcV = Math.sqrt(seg.getAccel() * radius);
                        seg.setNominalSpeed(Math.min(seg.getNominalSpeed(), maxArcV));
                    }

                    segments.add(seg);
                    commandToSegmentMap.add(segments.size() - 1);
                } else {
                    // Linear G0 or G1 move
                    MotionSegment seg = new MotionSegment(lineNumber, currentCmdIdx, lineMotionType,
                            currentX, currentY, currentZ, targetX, targetY, targetZ,
                            feedRateMmMin, profile);
                    segments.add(seg);
                    commandToSegmentMap.add(segments.size() - 1);
                }

                currentX = targetX;
                currentY = targetY;
                currentZ = targetZ;
            } else {
                // Command had no motion (e.g. F change or coordinate setting)
                // Map to previous segment index or -1
                commandToSegmentMap.add(segments.isEmpty() ? -1 : (segments.size() - 1));
            }
        }

        // Run lookahead velocity planner across all segments
        LookaheadPlanner.plan(segments, profile);

        // Aggregate stats
        double totalDuration = 0.0;
        double rapidDuration = 0.0;
        double cutDuration = 0.0;
        double dwellDuration = 0.0;

        double totalDist = 0.0;
        double rapidDist = 0.0;
        double cutDist = 0.0;

        for (MotionSegment seg : segments) {
            double dur = seg.getDurationSeconds();
            totalDuration += dur;

            if (seg.getType() == MotionSegment.Type.RAPID) {
                rapidDuration += dur;
                rapidDist += seg.getLength();
            } else if (seg.getType() == MotionSegment.Type.CUT || seg.getType() == MotionSegment.Type.ARC) {
                cutDuration += dur;
                cutDist += seg.getLength();
            } else if (seg.getType() == MotionSegment.Type.DWELL) {
                dwellDuration += dur;
            }
            totalDist += seg.getLength();
        }

        // Build commandCompletionTimes array (size = commandIndex)
        double[] completionTimes = new double[commandIndex];
        for (int i = 0; i < commandIndex; i++) {
            int segIdx = commandToSegmentMap.get(i);
            if (segIdx >= 0 && segIdx < segments.size()) {
                completionTimes[i] = segments.get(segIdx).getCumulativeTimeSeconds();
            } else {
                completionTimes[i] = (i > 0) ? completionTimes[i - 1] : 0.0;
            }
        }

        return new SimulationResult(
                totalDuration, rapidDuration, cutDuration, dwellDuration,
                totalDist, rapidDist, cutDist,
                minX, maxX, minY, maxY, minZ, maxZ,
                segments.size(), commandIndex,
                completionTimes, segments
        );
    }

    /**
     * Strips parentheses comments (like (comment)) and semicolon comments (like ; comment).
     */
    private static String stripComments(String line) {
        if (line == null) return "";
        // Strip semicolon to end of line
        int semiIdx = line.indexOf(';');
        if (semiIdx >= 0) {
            line = line.substring(0, semiIdx);
        }
        // Strip (comment)
        int parenStart = line.indexOf('(');
        while (parenStart >= 0) {
            int parenEnd = line.indexOf(')', parenStart);
            if (parenEnd > parenStart) {
                line = line.substring(0, parenStart) + line.substring(parenEnd + 1);
            } else {
                line = line.substring(0, parenStart);
                break;
            }
            parenStart = line.indexOf('(');
        }
        return line;
    }
}
