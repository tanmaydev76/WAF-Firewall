@echo off
setlocal
cd /d "%~dp0"

set PROXY_PORT=8080
set TARGET_URL=http://localhost:3000

if not "%~1"=="" set PROXY_PORT=%~1
if not "%~2"=="" set TARGET_URL=%~2

set JAVA="C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1\jbr\bin\java.exe"
if not exist %JAVA% set JAVA=java

echo =================================================================
echo   STARTING WAF REVERSE PROXY
echo   Proxy Port     : http://localhost:%PROXY_PORT%
echo   Target Website : %TARGET_URL%
echo =================================================================

%JAVA% -cp "target/classes" com.wafgateway.worklet3.WAFProxyServer --port %PROXY_PORT% --target %TARGET_URL%
