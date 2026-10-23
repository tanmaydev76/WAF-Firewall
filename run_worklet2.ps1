# ==============================================================================
# Worklet 2 Runner: Compiles & Executes all Worklet 2 (C++) Codes, Tests, Benchmarks
# ==============================================================================

$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
Set-Location $workspaceRoot

$gpp = "C:\MinGW\bin\g++.exe"
if (-not (Test-Path $gpp)) {
    $gppCmd = Get-Command g++ -ErrorAction SilentlyContinue
    if ($gppCmd) { $gpp = $gppCmd.Source }
    else { Write-Error "g++ compiler not found in C:\MinGW\bin\ or PATH." }
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   BUILDING & RUNNING ALL WORKLET 2 (C++) CODES                  " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Compiler: $gpp"

# 1. Build test_pipeline
Write-Host "`n[1/3] Compiling and running Member 4 QA test suite (test_pipeline)..." -ForegroundColor Yellow
& $gpp -std=c++1z -Iworklet2/include `
    worklet2/src/grammar/Grammar.cpp `
    worklet2/src/grammar/GrammarConfig.cpp `
    worklet2/src/cyk/Cell.cpp `
    worklet2/src/cyk/CYKTable.cpp `
    worklet2/src/tree/TreeBuilder.cpp `
    worklet2/src/tree/TreeChecker.cpp `
    worklet2/src/tree/ActionExtractor.cpp `
    worklet2/src/pipeline/StructuralValidator.cpp `
    worklet2/src/pipeline/Worklet2Gateway.cpp `
    worklet2/src/bridge/Worklet1Adapter.cpp `
    worklet2/tests/test_pipeline.cpp `
    -o worklet2/test_pipeline.exe
& .\worklet2\test_pipeline.exe

# 2. Build bench_cyk
Write-Host "`n[2/3] Compiling and running Member 2 CYK Benchmarks (bench_cyk)..." -ForegroundColor Yellow
& $gpp -std=c++1z -Iworklet2/include `
    worklet2/src/grammar/Grammar.cpp `
    worklet2/src/grammar/GrammarConfig.cpp `
    worklet2/src/cyk/Cell.cpp `
    worklet2/src/cyk/CYKTable.cpp `
    worklet2/tests/bench_cyk.cpp `
    -o worklet2/bench_cyk.exe
& .\worklet2\bench_cyk.exe

# 3. Build worklet2_demo
Write-Host "`n[3/3] Compiling and running Worklet 2 Orchestration Demo (worklet2_demo)..." -ForegroundColor Yellow
& $gpp -std=c++1z -Iworklet2/include `
    worklet2/src/grammar/Grammar.cpp `
    worklet2/src/grammar/GrammarConfig.cpp `
    worklet2/src/cyk/Cell.cpp `
    worklet2/src/cyk/CYKTable.cpp `
    worklet2/src/tree/TreeBuilder.cpp `
    worklet2/src/tree/TreeChecker.cpp `
    worklet2/src/tree/ActionExtractor.cpp `
    worklet2/src/pipeline/StructuralValidator.cpp `
    worklet2/src/pipeline/Worklet2Gateway.cpp `
    worklet2/src/bridge/Worklet1Adapter.cpp `
    worklet2/src/main.cpp `
    -o worklet2/worklet2_demo.exe
& .\worklet2\worklet2_demo.exe

Write-Host "=================================================================" -ForegroundColor Green
Write-Host " ALL WORKLET 2 BINARIES BUILT AND EXECUTED SUCCESSFULLY!         " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
