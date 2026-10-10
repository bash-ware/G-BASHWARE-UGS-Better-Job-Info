param(
    [string]$JavaHome = 'C:\Program Files\Java\jdk-22',
    [string]$Maven = 'C:\java\maven\bin\mvn.cmd'
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$moduleRoot = Join-Path $projectRoot 'ugs-job-info'
$localRepository = Join-Path $projectRoot '.m2\repository'
$version = '1.0.0'
$ugsVersion = '2.1.6'
$moduleName = "ugs-platform-plugin-job-info-$version.nbm"
$sourceNbm = Join-Path $projectRoot "ugs-job-info\target\nbm\$moduleName"
$distRoot = Join-Path $projectRoot 'dist'
$releaseNbm = Join-Path $distRoot "G-BASHWARE-UGS-Better-Job-Info-for-UGS-$ugsVersion.nbm"
$releaseZip = Join-Path $distRoot "G-BASHWARE-UGS-Better-Job-Info-${version}_UGS-${ugsVersion}_Windows.zip"
$staging = Join-Path $projectRoot ".verify\jobinfo-package"
$updateSite = Join-Path $distRoot 'update-center'

& (Join-Path $projectRoot 'build.ps1') -JavaHome $JavaHome -Maven $Maven

New-Item -ItemType Directory -Force -Path $distRoot | Out-Null
New-Item -ItemType Directory -Force -Path $staging | Out-Null

Get-ChildItem -LiteralPath $staging -Force | Remove-Item -Recurse -Force

Copy-Item -LiteralPath $sourceNbm -Destination $releaseNbm -Force
Copy-Item -LiteralPath $releaseNbm -Destination (Join-Path $staging (Split-Path $releaseNbm -Leaf)) -Force
if (Test-Path (Join-Path $projectRoot 'docs\INSTALL.md')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot 'docs\INSTALL.md') -Destination (Join-Path $staging 'INSTALL.md') -Force
}
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $releaseZip -Force

$hashes = @($releaseNbm, $releaseZip) | ForEach-Object {
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_).Hash.ToLowerInvariant()
    "$hash  $(Split-Path $_ -Leaf)"
}
Set-Content -LiteralPath (Join-Path $distRoot 'SHA256SUMS.txt') -Value $hashes -Encoding ascii

# Generate update-center catalog
New-Item -ItemType Directory -Force -Path $updateSite | Out-Null
Copy-Item -LiteralPath $sourceNbm -Destination (Join-Path $moduleRoot "target\$moduleName") -Force
& $Maven -f (Join-Path $moduleRoot 'pom.xml') nbm:autoupdate `
    "-Dmaven.repo.local=$localRepository" `
    '-Dmaven.nbm.customDistBase=https://raw.githubusercontent.com/bash-ware/G-BASHWARE-UGS-Better-Job-Info/update-center' `
    '-Dmaven.nbm.updatesitexml=updates.xml'
if ($LASTEXITCODE -ne 0) {
    throw "Update-center generation failed (exit $LASTEXITCODE)."
}
Copy-Item -LiteralPath (Join-Path $moduleRoot 'target\netbeans_site\updates.xml') -Destination $updateSite -Force
Copy-Item -LiteralPath (Join-Path $moduleRoot 'target\netbeans_site\updates.xml.gz') -Destination $updateSite -Force
Copy-Item -LiteralPath (Join-Path $moduleRoot "target\netbeans_site\$moduleName") -Destination $updateSite -Force

Write-Host "=========================================="
Write-Host "Release NBM:   $releaseNbm"
Write-Host "Release ZIP:   $releaseZip"
Write-Host "Checksums:     $(Join-Path $distRoot 'SHA256SUMS.txt')"
Write-Host "Update center: $updateSite"
Write-Host "=========================================="
