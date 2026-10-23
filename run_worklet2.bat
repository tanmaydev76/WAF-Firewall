@echo off
setlocal
cd /d "%~dp0"

echo =================================================================
echo    BUILDING & RUNNING ALL WORKLET 2 (C++) CODES
echo =================================================================

set GPP=C:\MinGW\bin\g++.exe
if not exist "%GPP%" set GPP=g++

echo [1/3] Compiling and running test_pipeline...
"%GPP%" -std=c++1z -Iworklet2/include ^
    worklet2/src/grammar/Grammar.cpp ^
    worklet2/src/grammar/GrammarConfig.cpp ^
    worklet2/src/cyk/Cell.cpp ^
    worklet2/src/cyk/CYKTable.cpp ^
    worklet2/src/tree/TreeBuilder.cpp ^
    worklet2/src/tree/TreeChecker.cpp ^
    worklet2/src/tree/ActionExtractor.cpp ^
    worklet2/src/pipeline/StructuralValidator.cpp ^
    worklet2/src/pipeline/Worklet2Gateway.cpp ^
    worklet2/src/bridge/Worklet1Adapter.cpp ^
    worklet2/tests/test_pipeline.cpp ^
    -o worklet2\test_pipeline.exe
if errorlevel 1 goto error
worklet2\test_pipeline.exe
if errorlevel 1 goto error

echo.
echo [2/3] Compiling and running bench_cyk...
"%GPP%" -std=c++1z -Iworklet2/include ^
    worklet2/src/grammar/Grammar.cpp ^
    worklet2/src/grammar/GrammarConfig.cpp ^
    worklet2/src/cyk/Cell.cpp ^
    worklet2/src/cyk/CYKTable.cpp ^
    worklet2/tests/bench_cyk.cpp ^
    -o worklet2\bench_cyk.exe
if errorlevel 1 goto error
worklet2\bench_cyk.exe
if errorlevel 1 goto error

echo.
echo [3/3] Compiling and running worklet2_demo...
"%GPP%" -std=c++1z -Iworklet2/include ^
    worklet2/src/grammar/Grammar.cpp ^
    worklet2/src/grammar/GrammarConfig.cpp ^
    worklet2/src/cyk/Cell.cpp ^
    worklet2/src/cyk/CYKTable.cpp ^
    worklet2/src/tree/TreeBuilder.cpp ^
    worklet2/src/tree/TreeChecker.cpp ^
    worklet2/src/tree/ActionExtractor.cpp ^
    worklet2/src/pipeline/StructuralValidator.cpp ^
    worklet2/src/pipeline/Worklet2Gateway.cpp ^
    worklet2/src/bridge/Worklet1Adapter.cpp ^
    worklet2/src/main.cpp ^
    -o worklet2\worklet2_demo.exe
if errorlevel 1 goto error
worklet2\worklet2_demo.exe
if errorlevel 1 goto error

echo =================================================================
echo  ALL WORKLET 2 BINARIES BUILT AND EXECUTED SUCCESSFULLY!
echo =================================================================
exit /b 0

:error
echo [ERROR] Worklet 2 build or test failed.
exit /b 1
