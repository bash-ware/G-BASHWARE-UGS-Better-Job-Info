# UGS Better Job Info

> **High-Accuracy CNC Job Duration, Progress Tracking, and Time-Left Estimator for Universal Gcode Sender (UGS) Platform 2.1.6+**

---

## 🚀 Overview

Standard job time estimators in CNC software (including UGS's built-in estimation) rely on a naive formula:
$$\text{Remaining Time} = \frac{\text{Elapsed Time}}{\text{Completed Lines}} \times \text{Remaining Lines}$$
In real-world CNC machining, this fails completely:
1. G-code files combine thousands of dense micro-segments (e.g. 3D surface contours, small circular arcs) with long high-speed travels.
2. It ignores machine physics: axis acceleration limits ($\text{mm/s}^2$), maximum axis velocities ($\text{mm/min}$), cornering junction deviation, and dwells (`G4`).
3. It cannot provide any estimate before streaming begins (`-1`).

**UGS Better Job Info** solves this by embedding a **physics-accurate CNC kinematics simulation engine** directly into UGS Platform. It automatically reads your connected machine's real acceleration and speed parameters (`$$` settings), runs a full **2-pass lookahead velocity planner**, and provides real-time tracking with sub-second accuracy, live feedrate-override adjustments, and true cutting percentage.

---

## ✨ Features

* **Physics-Accurate Total Job Time**:
  Calculates exact trapezoidal and triangular motion profiles across all linear moves (`G0`/`G1`), arcs (`G2`/`G3`), dwells (`G4`), and spindle spin-up delays (`M3`/`M4`).
* **Live Machine Settings Auto-Grabber**:
  Queries the controller via `IFirmwareSettings` on connection to automatically extract:
  * Accelerations ($120, $121, $122) in $\text{mm/s}^2$
  * Maximum Feedrates ($110, $111, $112) in $\text{mm/min}$
  * Junction Deviation ($11) and Arc Tolerance ($12)
  * Dynamically refreshes if settings are updated in UGS.
* **True Kinematic Progress %**:
  Progress is calculated from **simulated cutting time completed**, eliminating wild jumps caused by varying segment lengths.
* **Feedrate-Override-Aware Countdown & Live ETA**:
  Dynamically scales remaining time whenever you adjust the hardware or software feed override slider (e.g. 120%), displaying both countdown duration and exact clock ETA (`HH:mm:ss`).
* **Pause-Aware Stopwatch**:
  Wall-clock elapsed timer automatically pauses when the machine is held (`HOLD`, `DOOR`, `PAUSE`), maintaining exact synchronization.
* **Real-World Drift Calibration**:
  Applies an adaptive calibration factor comparing actual wall time against simulated time to account for serial communication buffers and controller queue headroom.
* **Rich Modern Dashboard**:
  High-polish dark-mode UI with metric cards, animated gradient progress bar, and live machine telemetry.
* **Sub-2-Second Standalone Dev Loop**:
  Run `.\preview.ps1` or double-click `preview.bat` to launch the interactive preview UI with simulated G-code streaming in under 2 seconds without launching UGS.

---

## 📦 Installation in UGS Platform

1. Download `G-BASHWARE-UGS-Better-Job-Info-for-UGS-2.1.6.nbm` from the [`dist/`](file:///f:/GDrive/BashWorks%20OU/CODEX/Tools/UGS%20Plugins/UGS%20Better%20Job%20info/dist) directory.
2. In UGS Platform (v2.1.6+), open **Tools → Plugins**.
3. Select the **Downloaded** tab and click **Add Plugins...**.
4. Select the `.nbm` file and click **Install**.
5. Restart UGS when prompted.
6. Open the window from **Window → Plugins → UGS Better Job Info** and dock it in your preferred pane.

---

## 🛠️ Standalone Preview Runner

To test UI changes or simulate jobs without running UGS Platform:

```powershell
.\preview.ps1
```
Or double-click `preview.bat`.

To run only the kinematics unit tests:
```powershell
.\preview.ps1 -TestOnly
```

---

## 🏗️ Building & Packaging

### Local Compilation (NBM)
```powershell
.\build.ps1
```
Generates: `ugs-job-info/target/nbm/ugs-platform-plugin-job-info-1.0.0.nbm`.

### Full Release Packaging
```powershell
.\package.ps1
```
Outputs release artifacts to `dist/`:
* `G-BASHWARE-UGS-Better-Job-Info-for-UGS-2.1.6.nbm` — Ready for manual installation.
* `G-BASHWARE-UGS-Better-Job-Info-1.0.0_UGS-2.1.6_Windows.zip` — Packaged release archive with installation guide.
* `SHA256SUMS.txt` — SHA-256 verification checksums.

---

## 📁 Repository Layout

```text
├── .m2/                                      # Local Maven repository junction
├── .upstream/                                # Cloned UGS 2.1.6 distribution junction
├── dist/                                     # Packaged release archives and checksums
├── docs/
│   └── INSTALL.md                            # End-user install guide
├── ugs-job-info/                             # Java plugin Maven module
│   ├── pom.xml                               # NetBeans module definition
│   └── src/
│       ├── main/java/com/bashworks/ugs/jobinfo/
│       │   ├── JobInfoTopComponent.java      # NetBeans window entrypoint
│       │   ├── PluginInfo.java               # Version and branding constants
│       │   ├── kinematics/                   # Kinematics engine
│       │   │   ├── MachineProfile.java       # Machine accels ($120-122) & speeds ($110-112)
│       │   │   ├── MotionSegment.java        # Motion block data model
│       │   │   ├── LookaheadPlanner.java     # 2-pass reverse/forward velocity planner
│       │   │   ├── KinematicsSimulator.java  # G-code parser & timeline integrator
│       │   │   └── SimulationResult.java     # Cumulative timeline and statistics
│       │   ├── machine/                      # Hardware abstraction
│       │   │   ├── JobInfoListener.java      # Event listener interface
│       │   │   ├── JobInfoMachineGateway.java# Decoupled machine interface
│       │   │   ├── UgsJobInfoMachineGateway.java # Live UGS BackendAPI integration
│       │   │   └── SimulatedJobInfoMachineGateway.java # Standalone mock
│       │   ├── tracker/                      # Live tracking state machine
│       │   │   ├── JobState.java             # IDLE, SIMULATING, READY, RUNNING, PAUSED, COMPLETED
│       │   │   ├── JobSnapshot.java          # Immutable telemetry record
│       │   │   └── JobTracker.java           # Live stopwatch, override scaling, ETA
│       │   └── ui/                           # Swing components
│       │       ├── UITheme.java              # Color tokens and formatters
│       │       ├── MetricCard.java           # Rounded dashboard metric card
│       │       ├── SmoothProgressBar.java    # Anti-aliased gradient progress bar
│       │       ├── KinematicsConfigDialog.java # Settings dialog ($110-$122 inspection/editing)
│       │       └── JobInfoPanel.java         # Main dashboard panel
│       └── test/java/com/bashworks/ugs/jobinfo/
│           ├── kinematics/KinematicsTest.java # Kinematic unit tests
│           └── ui/JobInfoStandalone.java      # Standalone interactive preview runner
├── build.ps1                                 # Compiles plugin NBM
├── package.ps1                               # Builds distribution zip and SHA256 sums
├── preview.bat                               # Quick launcher for standalone preview
├── preview.ps1                               # Compiles & runs standalone preview
└── PLAN-accurate-job-info.md                 # Detailed mathematical & architectural plan
```

---

## 📜 License

Licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
Developed by **G-BASHWARE**.
