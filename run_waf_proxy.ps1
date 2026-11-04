# ==============================================================================
# WAF Reverse Proxy Runner
# Protects your local website by intercepting requests on $ProxyPort (e.g. 8080)
# and forwarding authorized traffic to $TargetUrl (e.g. http://localhost:3000)
# ==============================================================================
param(
    [int]$ProxyPort = 8080,
    [string]$TargetUrl = "http://localhost:3000"
)

$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
Set-Location $workspaceRoot

$java = "C:\Program Files\Java\jdk-17\bin\java.exe"
if (-not (Test-Path $java)) {
    $java = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\jbr\bin\java.exe"
}
if (-not (Test-Path $java)) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) { $java = $javaCmd.Source }
}

Write-Host "Starting WAF Reverse Proxy..." -ForegroundColor Cyan
Write-Host "Listening on Port : http://localhost:$ProxyPort" -ForegroundColor Yellow
Write-Host "Target Website    : $TargetUrl" -ForegroundColor Yellow

& $java -cp "target/classes" com.wafgateway.proxy.WafReverseProxyServer --port $ProxyPort --target $TargetUrl
