# UGS Better Job Info — Installation & Quick Start

## 1. Overview
**UGS Better Job Info** is a high-accuracy job estimation and tracking plugin for **Universal Gcode Sender (UGS) Platform 2.1.6**.
Unlike standard estimators that simply divide elapsed time by completed line count, this plugin runs a true **2-pass lookahead kinematics simulation** using your machine's physical acceleration limits ($120-$122) and maximum feedrates ($110-$112).

---

## 2. Installation in UGS Platform

1. Launch **Universal Gcode Sender Platform** (version 2.1.6 or newer).
2. Go to top menu: **Tools → Plugins**.
3. Switch to the **Downloaded** tab.
4. Click **Add Plugins...** and browse to:
   `G-BASHWARE-UGS-Better-Job-Info-for-UGS-2.1.6.nbm` (or `ugs-platform-plugin-job-info-1.0.0.nbm`).
5. Click **Install** and proceed through the NetBeans module installation wizard.
6. When prompted, restart UGS.
7. After restart, open the window from:
   **Window → Plugins → UGS Better Job Info**.
8. Dock the tab wherever you prefer in the UGS interface (typically docked next to the Visualizer or Controller Status).

---

## 3. How It Works

* **Automatic Machine Calibration**:
  When you connect to your machine (GRBL, FluidNC, GrblHAL), the plugin queries your firmware settings (`$$`) to automatically read:
  - Axis accelerations: `$120` (X), `$121` (Y), `$122` (Z)
  - Axis max feedrates: `$110` (X), `$111` (Y), `$112` (Z)
  - Cornering junction deviation: `$11`
  - Arc tolerance: `$12`
* **Instant Calculation on File Load**:
  When you open a G-code file (`.nc`, `.gcode`, `.tap`), the simulator calculates the full motion duration and cumulative timeline in the background. Total estimated time is available immediately before you hit Send.
* **Pause-Aware Stopwatch**:
  The elapsed time timer pauses when your machine enters `HOLD` or `PAUSE`, maintaining sub-second accuracy.
* **True Cutting % Progress**:
  Progress % is calculated from simulated kinematic work done, rather than arbitrary line counts.
* **Live Feedrate Override Countdown**:
  If you adjust the feedrate override slider (e.g. 120%), remaining time and ETA adapt immediately.
