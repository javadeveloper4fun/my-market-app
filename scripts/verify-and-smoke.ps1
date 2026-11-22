param(
    [switch]$SkipBuild,
    [switch]$SkipDockerDown
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Invoke-Step([string]$Title, [scriptblock]$Action) {
    Write-Host "`n==> $Title" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "Step failed: $Title (exit code $LASTEXITCODE)"
    }
}

if (-not $SkipBuild) {
    $env:GRADLE_OPTS = "-Dorg.gradle.jvmargs=-Xmx640m -XX:MaxMetaspaceSize=384m"
    Invoke-Step "Clean build payments-service" { .\gradlew.bat --no-daemon --max-workers=1 :payments-service:clean :payments-service:build --console=plain }
    Invoke-Step "Clean build market-app" { .\gradlew.bat --no-daemon --max-workers=1 :market-app:clean :market-app:build --console=plain }
} else {
    if (-not (Test-Path "payments-service\build\libs\payments-service-0.0.1-SNAPSHOT.jar")) { throw "Missing payments-service boot jar. Run without -SkipBuild." }
    if (-not (Test-Path "market-app\build\libs\market-app-0.0.1-SNAPSHOT.jar")) { throw "Missing market-app boot jar. Run without -SkipBuild." }
}

if (-not $SkipDockerDown) {
    Invoke-Step "docker compose down (keep volumes)" { docker compose down }
}

Invoke-Step "docker compose up -d --build" { docker compose up -d --build }
Invoke-Step "docker compose ps" { docker compose ps }

Write-Host "`n==> Waiting for services" -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(3)
$paymentsOk = $false
$marketOk = $false
while ((Get-Date) -lt $deadline -and (-not ($paymentsOk -and $marketOk))) {
    Start-Sleep -Seconds 3
    try {
        $balance = curl.exe -sS -m 5 http://localhost:8081/api/v1/balance
        if ($LASTEXITCODE -eq 0 -and $balance) { $paymentsOk = $true }
    } catch {}
    try {
        $code = curl.exe -sS -m 5 -o NUL -w "%{http_code}" http://localhost:8080/items
        if ($LASTEXITCODE -eq 0 -and $code -eq '200') { $marketOk = $true }
    } catch {}
}

Write-Host "`n==> Smoke checks" -ForegroundColor Cyan
$balance = curl.exe -sS -m 10 http://localhost:8081/api/v1/balance
if ($LASTEXITCODE -ne 0) { throw "Payments balance check failed" }
Write-Host "Balance: $balance"

$itemsCode = curl.exe -sS -m 10 -o NUL -w "%{http_code}" http://localhost:8080/items
if ($LASTEXITCODE -ne 0 -or $itemsCode -ne '200') { throw "Market items check failed (HTTP $itemsCode)" }
Write-Host "Items: HTTP $itemsCode"

Write-Host "`nSmoke checks passed. For purchase scenarios, test via UI: success, insufficient funds (402), and payments unavailable (docker compose stop payments-service)." -ForegroundColor Green
