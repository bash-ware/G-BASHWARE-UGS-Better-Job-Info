# G-BASHWARE Better Job Info — Installation & Quick Start

## 1. Overview
**G-BASHWARE Better Job Info** is a physics-accurate job estimation and tracking plugin for **Universal Gcode Sender (UGS) Platform 2.1.6**.
Unlike standard estimators that simply divide elapsed time by completed line count, this plugin runs a true **2-pass lookahead kinematics simulation** using your machine's physical acceleration limits ($120–$122) and maximum velocities ($110–$112).

---

## 2. Option A: In-App Update Center (Recommended)

To install directly inside UGS and receive one-click updates:

1. Open **Tools → Plugins → Settings**.
2. Click **Add**.
3. Enter:
   * **Name**: `G-BASHWARE Better Job Info`
   * **URL**: `https://raw.githubusercontent.com/bash-ware/G-BASHWARE-UGS-Better-Job-Info/update-center/updates.xml`
4. Click **OK**, then switch to the **Available Plugins** tab (click **Check for Newest** if needed).
5. Select **G-BASHWARE Better Job Info** under category `G-BASHWARE` and click **Install**.
6. Restart UGS when prompted.

---

## 3. Option B: Manual NBM Installation

1. Launch **Universal Gcode Sender Platform** (version 2.1.6 or newer).
2. Download `G-BASHWARE-UGS-Better-Job-Info-for-UGS-2.1.6.nbm` from the GitHub Releases page.
3. In UGS, go to top menu: **Tools → Plugins**.
4. Switch to the **Downloaded** tab.
5. Click **Add Plugins...** and select `G-BASHWARE-UGS-Better-Job-Info-for-UGS-2.1.6.nbm`.
6. Click **Install** and follow the wizard prompts.
7. Restart UGS when prompted.
8. Open the plugin window from:
   **Window → Plugins → G-BASHWARE Better Job Info**.

---

## 4. Key Features

* **Physics-Accurate Kinematics Engine**:
  Simulates every move with a 2-pass velocity planner (acceleration and deceleration ramps) adhering to physical axis constraints ($120–$122, $110–$112, $11, $12).
* **Automatic Controller Parameter Discovery**:
  Reads machine acceleration and feedrate parameters directly from GRBL/FluidNC/grblHAL firmware when connected.
* **Instant Calculation on File Load**:
  Accurate total time is ready immediately when you open a G-code file, before pressing Send.
* **Pause & Hold Aware**:
  Elapsed time stopwatch pauses during HOLD/PAUSE states to keep timing exact.
* **True Cutting % Progress**:
  Tracks progress based on physical time completed rather than misleading line counts.
* **Live Feedrate Override Scaling**:
  Dynamically recalculates remaining time and ETA in real time when feedrate overrides are applied in UGS.
