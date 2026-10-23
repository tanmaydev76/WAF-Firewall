$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1\jbr"
$java = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1\jbr\bin\java.exe"

# Start Mock Website on 3000
$mockJob = Start-Process -FilePath $java -ArgumentList "-cp target/classes com.wafgateway.proxy.MockWebsiteServer 3000" -PassThru -NoNewWindow
Start-Sleep -Seconds 2

# Start WAF Proxy on 8080 targeting 3000
$proxyJob = Start-Process -FilePath $java -ArgumentList "-cp target/classes com.wafgateway.proxy.WafReverseProxyServer --port 8080 --target http://localhost:3000" -PassThru -NoNewWindow
Start-Sleep -Seconds 2

try {
    Write-Host "`n--- TEST 1: Sending Legitimate Login to Port 8080 ---" -ForegroundColor Cyan
    $headers = @{"Content-Type" = "application/json"}
    $body = '{"action":"LOGIN"}'
    $resp1 = Invoke-WebRequest -Uri "http://localhost:8080/api/login" -Method POST -Headers $headers -Body $body -UseBasicParsing
    Write-Host "Status Code : $($resp1.StatusCode)"
    Write-Host "WAF Verdict : $($resp1.Headers['X-WAF-Verdict'])"
    Write-Host "Body        : $($resp1.Content)"

    Write-Host "`n--- TEST 2: Sending SQL Injection Attack to Port 8080 ---" -ForegroundColor Cyan
    $bodySql = '{"item":"book%27%20OR%201%3D1--"}'
    try {
        $resp2 = Invoke-WebRequest -Uri "http://localhost:8080/api/cart" -Method POST -Headers $headers -Body $bodySql -UseBasicParsing
    } catch {
        $resp2 = $_.Exception.Response
        $stream = $resp2.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $respBody = $reader.ReadToEnd()
        Write-Host "Status Code : $([int]$resp2.StatusCode)"
        Write-Host "WAF Verdict : $($resp2.Headers['X-WAF-Verdict'])"
        Write-Host "Body        : $respBody"
    }

    Write-Host "`n--- TEST 3: Sending JSON Bomb Attack to Port 8080 ---" -ForegroundColor Cyan
    $bodyBomb = '{"a":{"b":{"c":{"d":"bomb"}}}}'
    try {
        $resp3 = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Method POST -Headers $headers -Body $bodyBomb -UseBasicParsing
    } catch {
        $resp3 = $_.Exception.Response
        $stream = $resp3.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $respBody = $reader.ReadToEnd()
        Write-Host "Status Code : $([int]$resp3.StatusCode)"
        Write-Host "WAF Verdict : $($resp3.Headers['X-WAF-Verdict'])"
        Write-Host "Body        : $respBody"
    }
}
finally {
    Stop-Process -Id $proxyJob.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $mockJob.Id -Force -ErrorAction SilentlyContinue
}
