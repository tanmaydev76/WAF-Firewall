# ==============================================================================
# Full WAF Project Runner:
# 1. Compiles & executes all Worklet 2 C++ codes, tests, and benchmarks
# 2. Compiles & executes the End-to-End WAF Pipeline (Worklet 1 -> 2 -> 3)
# ==============================================================================

$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
Set-Location $workspaceRoot

# 1. Run Worklet 2 complete suite
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " STEP 1: RUNNING ALL WORKLET 2 (C++) CODES, TESTS & BENCHMARKS   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

& .\run_worklet2.ps1

# 2. Run Integrated Pipeline
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host " STEP 2: RUNNING INTEGRATED PIPELINE (WORKLET 1 -> 2 -> 3)       " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Locate Java & JDK
$java = "C:\Program Files\Java\jdk-17\bin\java.exe"
if (-not (Test-Path $java)) {
    $java = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\jbr\bin\java.exe"
}
if (-not (Test-Path $java)) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) { $java = $javaCmd.Source }
}

if (Test-Path "C:\Program Files\Java\jdk-17") {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
} elseif (Test-Path "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\jbr") {
    $env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\jbr"
}

# Locate Maven
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"
if (-not (Test-Path $mvn)) {
    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvnCmd) { $mvn = $mvnCmd.Source }
}

Write-Host "Using Java : $java" -ForegroundColor Gray
Write-Host "Using Maven: $mvn" -ForegroundColor Gray

Write-Host "`nCompiling Java project..." -ForegroundColor Yellow
& $mvn compile -q

Write-Host "Executing FullPipelineDemo..." -ForegroundColor Yellow
& $java -cp "target/classes" com.wafgateway.FullPipelineDemo

Write-Host "=================================================================" -ForegroundColor Green
Write-Host " ALL SYSTEMS AND INTEGRATIONS RUN COMPLETED WITH FULL PASS!      " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
