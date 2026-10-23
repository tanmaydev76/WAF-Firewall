@echo off
title WAF Firewall Launcher
color 0A

set ROOT=%~dp0
set JAVA=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1\jbr\bin\java.exe

echo ============================================================
echo   WAF FIREWALL - Starting Servers
echo ============================================================
echo.

cd /d "%ROOT%"

echo Starting Mock Website on port 3000 (in new window)...
start "Mock Website :3000" cmd /k ""%JAVA%" -cp target\classes com.wafgateway.proxy.MockWebsiteServer 3000"
timeout /t 2 /nobreak >nul

echo Starting WAF Reverse Proxy on port 8080 (foreground)...
echo.
echo ============================================================
echo   WAF PROXY LOGS  (8080 -> localhost:3000)
echo   Press Ctrl+C to stop
echo ============================================================
echo.

"%JAVA%" -cp target\classes com.wafgateway.proxy.WafReverseProxyServer --port 8080 --target http://localhost:3000
pause
