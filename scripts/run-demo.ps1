# run-demo.ps1 - build backend jar, start docker middleware + backend, then start admin and miniapp dev.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\run-demo.ps1
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"

Write-Host "run-demo: starting mysql + redis"
Push-Location $backend
try {
    docker compose up -d mysql redis
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }

    if (-not $SkipBuild) {
        Write-Host "run-demo: building backend jar (mvn -DskipTests package)"
        mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "maven package failed" }
    }

    $jar = Join-Path $backend "target\weiguang-backend.jar"
    if (-not (Test-Path $jar)) {
        throw "backend jar not found: $jar（先执行 mvn -DskipTests package，或不要加 -SkipBuild）"
    }

    Write-Host "run-demo: starting backend container"
    docker compose up -d backend
    if ($LASTEXITCODE -ne 0) { throw "backend container failed to start" }
} finally {
    Pop-Location
}

$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 3
        if ($health.status -eq "UP") { $ready = $true; break }
    } catch {
        # 启动中，继续等待
    }
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    throw "backend health check did not become UP within 60s"
}

Write-Host "run-demo: starting admin-web (port 5174)"
$adminListening = Get-NetTCPConnection -LocalPort 5174 -State Listen -ErrorAction SilentlyContinue
if (-not $adminListening) {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\admin-web'; npm run dev"
} else {
    Write-Host "run-demo: admin-web already listening on 5174, skip"
}

Write-Host "run-demo: starting miniapp dev build"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\front\miniapp'; npm run dev:mp-weixin"

Write-Host ""
Write-Host "run-demo: ready"
Write-Host "  backend : http://localhost:8080/actuator/health"
Write-Host "  admin   : http://localhost:5174"
Write-Host "  miniapp : import front\miniapp\dist\dev\mp-weixin into WeChat DevTools"
