# Architecture & Implementation Plan: UGS Accurate Job Info Plugin

## Executive Summary

The default Universal Gcode Sender (UGS) job time estimation uses a naive calculation:
$$\text{Remaining Time} = \frac{\text{Elapsed Time}}{\text{Completed Lines}} \times \text{Remaining Lines}$$
This formula is notoriously inaccurate in CNC operations because:
1. G-code files mix thousands of short micro-segments (e.g. 3D surface finishing, small arcs) with long high-speed travel lines.
2. It completely ignores machine kinematics: axis acceleration limits ($\text{mm/s}^2$), maximum feedrates ($\text{mm/min}$), junction deviation cornering, rapid traverse speeds, and dwell commands (`G4`).
3. Before the job starts, it cannot provide any estimate at all (`-1`).

This project, **UGS Better Job Info** (Plugin module: `ugs-job-info`), implements a **physics-accurate CNC kinematics simulation engine** directly inside UGS Platform 2.1.6. It automatically extracts the connected machine's real acceleration and velocity parameters (GRBL `$$` settings), runs a full 2-pass lookahead trajectory planner across the loaded G-code, and tracks the active job in real-time with sub-second precision, live feedrate-override adaptation, and accurate percentage completion based on actual cutting time.

---

## 1. Kinematics & Simulation Mathematics

### 1.1 Machine Kinematic Profile
The machine profile encapsulates the physical constraints of each axis:
* **Max Velocities ($V_{max}$)**: GRBL `$110` (X), `$111` (Y), `$112` (Z) in $\text{mm/min}$ (converted internally to $\text{mm/s}$).
* **Accelerations ($A$)**: GRBL `$120` (X), `$121` (Y), `$122` (Z) in $\text{mm/s}^2$.
* **Junction Deviation ($\delta$)**: GRBL `$11` in $\text{mm}$ (default $0.010\text{ mm}$).
* **Arc Tolerance ($S_{arc}$)**: GRBL `$12` in $\text{mm}$ (default $0.002\text{ mm}$).

### 1.2 Multi-Axis Directional Vectoring
For a motion block from position $\vec{P}_0$ to $\vec{P}_1$:
$$\vec{D} = \vec{P}_1 - \vec{P}_0 = (\Delta x, \Delta y, \Delta z)$$
$$\text{Length } L = \|\vec{D}\| = \sqrt{\Delta x^2 + \Delta y^2 + \Delta z^2}$$
$$\text{Unit Vector } \hat{u} = \frac{\vec{D}}{L} = (u_x, u_y, u_z)$$

The effective acceleration limit $a_{eff}$ and maximum velocity limit $v_{max\_eff}$ for this directional vector are governed by the most constrained active axis:
$$a_{eff} = \min_{i \in \{x, y, z\}, |u_i| > 0} \left( \frac{A_i}{|u_i|} \right)$$
$$v_{max\_eff} = \min_{i \in \{x, y, z\}, |u_i| > 0} \left( \frac{V_{max,i}}{|u_i|} \right)$$

For rapid moves (`G0`):
$$v_{nominal} = v_{max\_eff}$$
For cutting feed moves (`G1`, `G2`, `G3`) with programmed feedrate $F$ ($\text{mm/min}$):
$$v_{nominal} = \min\left( \frac{F}{60}, v_{max\_eff} \right)$$

### 1.3 Junction Cornering Velocity (GRBL Junction Deviation Algorithm)
When transitioning from block $n-1$ (unit vector $\hat{u}_{prev}$) to block $n$ (unit vector $\hat{u}_{curr}$):
1. Compute the change in direction angle $\theta$:
   $$\cos(\theta) = -\hat{u}_{prev} \cdot \hat{u}_{curr}$$
   $$\sin\left(\frac{\theta}{2}\right) = \sqrt{\frac{1 + \hat{u}_{prev} \cdot \hat{u}_{curr}}{2}}$$
2. Centripetal acceleration constraint at the junction:
   $$R_{corner} = \frac{\delta \cdot \sin(\theta/2)}{1 - \sin(\theta/2)}$$
   $$v_{junction} = \sqrt{a_{eff} \cdot R_{corner}}$$
3. Clamp junction speed:
   $$v_{junction} = \min(v_{junction}, v_{nominal, prev}, v_{nominal, curr})$$

If the vectors are collinear ($\theta = 0$), $v_{junction} = \min(v_{nominal, prev}, v_{nominal, curr})$. If reversing $180^\circ$, $v_{junction} = 0$.

### 1.4 Two-Pass Lookahead Velocity Planner
Given an array of motion blocks $[B_1, B_2, \dots, B_N]$:
Each block $B_k$ has length $L_k$, max cruise speed $v_{max, k}$, entry speed $v_{entry, k}$, and exit speed $v_{exit, k}$.

#### Pass 1: Reverse Pass (Deceleration Planning from End to Start)
Starting at the final block $B_N$ with $v_{exit, N} = 0$:
For $k = N-1$ down to $1$:
$$v_{max\_entry} = \sqrt{v_{exit, k}^2 + 2 \cdot a_{k} \cdot L_k}$$
$$v_{entry, k} = \min(v_{junction, k}, v_{max, k}, v_{max\_entry})$$
$$v_{exit, k-1} = v_{entry, k}$$

#### Pass 2: Forward Pass (Acceleration Planning from Start to End)
Starting at $B_1$ with $v_{entry, 1} = 0$:
For $k = 1$ up to $N$:
$$v_{max\_exit} = \sqrt{v_{entry, k}^2 + 2 \cdot a_{k} \cdot L_k}$$
$$v_{exit, k} = \min(v_{exit, k}, v_{max\_exit})$$
If $k < N$:
$$v_{entry, k+1} = v_{exit, k}$$

### 1.5 Exact Block Time Integration
With $v_{entry}$, $v_{exit}$, $v_{nominal}$, and $a$ established for block $k$:
1. Calculate acceleration distance to nominal speed:
   $$d_{acc} = \frac{v_{nominal}^2 - v_{entry}^2}{2a}$$
2. Calculate deceleration distance from nominal speed:
   $$d_{dec} = \frac{v_{nominal}^2 - v_{exit}^2}{2a}$$

**Case A: Trapezoidal Profile** ($d_{acc} + d_{dec} \le L$)
The machine reaches full commanded cruise speed:
$$d_{cruise} = L - d_{acc} - d_{dec}$$
$$t_{acc} = \frac{v_{nominal} - v_{entry}}{a}$$
$$t_{cruise} = \frac{d_{cruise}}{v_{nominal}}$$
$$t_{dec} = \frac{v_{nominal} - v_{exit}}{a}$$
$$T_{block} = t_{acc} + t_{cruise} + t_{dec}$$

**Case B: Triangular Profile** ($d_{acc} + d_{dec} > L$)
The move is too short to reach $v_{nominal}$. Peak speed is limited:
$$v_{peak} = \sqrt{\frac{2 a L + v_{entry}^2 + v_{exit}^2}{2}}$$
$$t_{acc} = \frac{v_{peak} - v_{entry}}{a}$$
$$t_{dec} = \frac{v_{peak} - v_{exit}}{a}$$
$$T_{block} = t_{acc} + t_{dec}$$

### 1.6 Non-Motion Blocks
* **Dwell (`G4 P...` / `G4 S...`)**: Directly adds specified dwell seconds to timeline.
* **Arcs (`G2` / `G3`)**: Calculated along the curved arc path length with radial acceleration limit $v_{max\_arc} = \sqrt{a_{eff} \cdot R_{arc}}$.
* **Spindle Delay (`M3`/`M4`)**: Configurable spindle acceleration delay (e.g., 2.0 s default, configurable in settings).

### 1.7 Timeline Indexing
The simulator builds a pre-computed timeline array:
$$\text{CumulativeTime}[k] = \sum_{i=1}^k T_{block}[i]$$
Total Job Duration:
$$T_{total} = \text{CumulativeTime}[N]$$
Each G-code command index $k$ maps directly to its finished cumulative time $\text{CumulativeTime}[k]$.

---

## 2. Machine Settings Acquisition (Auto-Grabber)

### 2.1 Live Firmware Settings Pipeline
UGS provides direct access to controller settings via:
```java
BackendAPI backend = CentralLookup.getDefault().lookup(BackendAPI.class);
IController controller = backend.getController();
IFirmwareSettings settings = controller.getFirmwareSettings();
```

The plugin automatically extracts:
```text
$110  ->  X Maximum Rate (mm/min)
$111  ->  Y Maximum Rate (mm/min)
$112  ->  Z Maximum Rate (mm/min)
$120  ->  X Acceleration (mm/s^2)
$121  ->  Y Acceleration (mm/s^2)
$122  ->  Z Acceleration (mm/s^2)
$11   ->  Junction Deviation (mm)
$12   ->  Arc Tolerance (mm)
$13   ->  Reporting Units (0=mm, 1=in)
```

### 2.2 Dynamic Settings Updates & Event Triggers
1. **Connection Event**: When UGS connects, `GrblControllerInitializer` runs `$$`. The plugin registers as an `IFirmwareSettingsListener` and `UGSEventListener`. When settings are updated, `FirmwareSettingEvent` fires, triggering an automatic cache refresh and background re-simulation.
2. **File Loaded Event**: When the user opens or reloads a file, `FileStateEvent(FILE_LOADED)` fires. The plugin immediately spawns a background `SwingWorker` simulation so total time is ready before the user clicks Send.
3. **Offline / Disconnected Fallback**:
   If no machine is connected, the plugin retrieves cached machine settings from `NbPreferences`. Users can also choose machine presets (e.g., Standard CNC Router 500mm/s², Desktop Engraver 1000mm/s², Heavy Milling 250mm/s²) or enter custom values manually.

---

## 3. Real-Time Tracking & Progress Metrics

| Metric | Calculation Method | Update Frequency |
|---|---|---|
| **Total Job Time** | $T_{total} = \sum T_{block}$ from kinematic simulation | On file load or settings change |
| **Time Elapsed** | High-resolution wall-clock timer, active during `RUN`, paused during `HOLD`/`PAUSE` | 10 Hz (every 100 ms) |
| **Progress %** | $\text{Progress} = \frac{\text{CumulativeTime}[\text{cmd}_{done}]}{T_{total}} \times 100\%$ | On each `CommandEvent` |
| **Time Remaining** | $\text{TimeLeft} = \frac{T_{total} - \text{CumulativeTime}[\text{cmd}_{done}]}{F_{override} / 100.0} \times \alpha_{calib}$ | 10 Hz |
| **Estimated Completion (ETA)** | $\text{CurrentTime} + \text{TimeLeft}$ | 10 Hz |

### Adaptive Calibration Factor ($\alpha_{calib}$)
While running, serial buffer transmission latency, controller planner queue headroom, and status reporting polling intervals can introduce small predictable delays.
The plugin calculates:
$$\alpha_{calib} = \frac{\text{Actual Wall Time Elapsed}}{\text{Cumulative Simulated Time Completed}}$$
This rolling ratio dynamically calibrates the remaining countdown, converging to sub-second accuracy as the job approaches completion.

---

## 4. Architecture & Repository Layout

```text
ugs-job-info/
├── pom.xml                                   # NetBeans NBM module definition (UGS 2.1.6, Java 17+)
└── src/
    ├── main/
    │   ├── java/com/bashworks/ugs/jobinfo/
    │   │   ├── JobInfoTopComponent.java      # NetBeans TopComponent (Window dock entrypoint)
    │   │   ├── PluginInfo.java               # Version, ID, build metadata
    │   │   ├── kinematics/
    │   │   │   ├── MachineProfile.java       # Holds accels ($120-122), max rates ($110-112), junction ($11, $12)
    │   │   │   ├── MotionSegment.java        # Motion block: start, end, rapid/feed/arc, dwell, speeds, time
    │   │   │   ├── TrajectoryPlanner.java    # 2-pass reverse/forward lookahead velocity calculator
    │   │   │   └── KinematicsSimulator.java  # Parses G-code, feeds planner, produces cumulative timeline
    │   │   ├── machine/
    │   │   │   ├── MachineGateway.java       # Interface abstracting UGS BackendAPI
    │   │   │   ├── UgsMachineGateway.java    # Live UGS implementation accessing CentralLookup
    │   │   │   └── MachineSettingsReader.java# Parses IFirmwareSettings ($110-$122, $11, $12)
    │   │   ├── tracker/
    │   │   │   ├── JobTracker.java           # State machine managing wall clock, progress %, ETA, overrides
    │   │   │   └── JobStatusSnapshot.java    # Immutable record pushed to UI on every update
    │   │   └── ui/
    │   │       ├── JobInfoPanel.java         # Main Swing panel with high-polish dark dashboard
    │   │       ├── MetricCard.java           # Reusable styled UI metric cards (Total, Passed, %, Left, ETA)
    │   │       ├── SmoothProgressBar.java    # Custom anti-aliased gradient progress bar
    │   │       ├── KinematicsDialog.java     # Settings dialog for viewing/editing machine accels
    │   │       └── UITheme.java              # Color tokens, fonts, styling helpers
    │   └── resources/
    │       └── com/bashworks/ugs/jobinfo/
    │           └── Bundle.properties         # Localization & descriptions
    └── test/
        └── java/com/bashworks/ugs/jobinfo/
            ├── kinematics/
            │   ├── TrajectoryPlannerTest.java# Unit tests verifying trapezoidal & junction speed math
            │   └── SimulatorBenchmark.java   # Speed benchmarks for parsing 100k-line G-code files
            └── ui/
                └── JobInfoStandalone.java    # Instant preview runner (no UGS needed)
```

---

## 5. UI Design & User Experience

### 5.1 Visual Layout (Modern Dark Theme)
```text
┌────────────────────────────────────────────────────────────────────────┐
│  UGS BETTER JOB INFO                         [⚙ Machine Settings] [🔄] │
├────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────┐  ┌───────────────────────┐                  │
│  │ TOTAL ESTIMATED TIME  │  │ TIME REMAINING        │                  │
│  │ 01h 42m 18s           │  │ 01h 18m 33s           │                  │
│  │ Nominal cut duration  │  │ ETA: 16:45:12 PM      │                  │
│  └───────────────────────┘  └───────────────────────┘                  │
│  ┌───────────────────────┐  ┌───────────────────────┐                  │
│  │ TIME PASSED (ELAPSED) │  │ PROGRESS COMPLETED    │                  │
│  │ 00h 23m 45s           │  │ 23.4 %                │                  │
│  │ Wall clock run time   │  │ Kinematic time basis  │                  │
│  └───────────────────────┘  └───────────────────────┘                  │
├────────────────────────────────────────────────────────────────────────┤
│  [==================>                                    ]  23.4%     │
├────────────────────────────────────────────────────────────────────────┤
│  📊 KINEMATIC DETAILS & MACHINE PARAMETERS                             │
│  • X Accel: 500 mm/s² | Y Accel: 500 mm/s² | Z Accel: 150 mm/s²       │
│  • X Max: 5000 mm/min | Y Max: 5000 mm/min | Z Max: 1200 mm/min       │
│  • Junction Dev: 0.010 mm | Arc Tol: 0.002 mm | Feed Override: 100%   │
│  • Distance: Rapid: 1,420 mm | Cut: 8,950 mm | Total Lines: 14,820     │
└────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Standalone Preview Runner
Following the developer guide, the module contains a `preview.ps1` script and `JobInfoStandalone.java` mock harness with sample G-code and simulated machine streaming, enabling rapid UI iteration in < 2 seconds without launching UGS Platform.
