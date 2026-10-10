param(
    [string]$Java = "C:\Program Files\Java\jdk-22\bin\java.exe",
    [string]$Javac = "C:\Program Files\Java\jdk-22\bin\javac.exe",
    [switch]$TestOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$verifyDir = Join-Path $projectRoot ".verify\standalone-classes"

if (-not (Test-Path -LiteralPath $Java)) {
    throw "Java runtime not found at: $Java"
}
if (-not (Test-Path -LiteralPath $Javac)) {
    throw "Java compiler not found at: $Javac"
}

$sources = @(
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\PluginInfo.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\kinematics\MachineProfile.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\kinematics\MotionSegment.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\kinematics\LookaheadPlanner.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\kinematics\SimulationResult.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\kinematics\KinematicsSimulator.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\machine\JobInfoListener.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\machine\JobInfoMachineGateway.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\machine\SimulatedJobInfoMachineGateway.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\tracker\JobState.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\tracker\JobSnapshot.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\tracker\JobTracker.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\ui\UITheme.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\ui\DarkButton.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\ui\MetricCard.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\ui\SmoothProgressBar.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\mqtt\MqttConfig.java"),

    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\mqtt\MiniMqttClient.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\mqtt\MqttTelemetryService.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\mqtt\MqttConfigDialog.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\ui\KinematicsConfigDialog.java"),
    (Join-Path $projectRoot "ugs-job-info\src\main\java\com\bashworks\ugs\jobinfo\ui\JobInfoPanel.java"),

    (Join-Path $projectRoot "ugs-job-info\src\test\java\com\bashworks\ugs\jobinfo\kinematics\KinematicsTest.java"),
    (Join-Path $projectRoot "ugs-job-info\src\test\java\com\bashworks\ugs\jobinfo\ui\JobInfoStandalone.java")
)

Write-Host "Compiling UGS Better Job Info standalone classes..."
New-Item -ItemType Directory -Force -Path $verifyDir | Out-Null
& $Javac -encoding UTF-8 -d $verifyDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed (exit $LASTEXITCODE)."
}

Write-Host "Running Kinematics unit tests..."
& $Java -cp $verifyDir com.bashworks.ugs.jobinfo.kinematics.KinematicsTest
if ($LASTEXITCODE -ne 0) {
    throw "Kinematics tests failed."
}

if (-not $TestOnly) {
    Write-Host "Launching UGS Better Job Info standalone preview..."
    & $Java -cp $verifyDir com.bashworks.ugs.jobinfo.ui.JobInfoStandalone
}
