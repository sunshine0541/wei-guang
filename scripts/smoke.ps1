# smoke.ps1 - core API smoke test against a running backend.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\smoke.ps1 [-BaseUrl http://localhost:8080]
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Invoke-Api([string]$method, [string]$path, [string]$token, $body) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $call = @{
        Method = $method
        Uri = "$BaseUrl$path"
        Headers = $headers
        ContentType = "application/json; charset=utf-8"
        TimeoutSec = 15
    }
    if ($null -ne $body) {
        $call["Body"] = ($body | ConvertTo-Json -Depth 6 -Compress)
    }
    return Invoke-RestMethod @call
}

function Assert-Ok($resp, [string]$name) {
    if ($null -eq $resp -or $resp.code -ne 0) {
        $code = if ($null -eq $resp) { "null" } else { $resp.code }
        Write-Host ("FAIL  " + $name + " -> code=" + $code)
        return $false
    }
    Write-Host ("PASS  " + $name)
    return $true
}

Write-Host "smoke: base=$BaseUrl"

$login = Invoke-Api "POST" "/api/auth/login" $null @{ username = "13867429153"; password = "wg123456" }
if (-not (Assert-Ok $login "POST /api/auth/login")) { exit 1 }
$token = $login.data.token
if (-not $token) {
    Write-Host "FAIL  login token is empty"
    exit 1
}

$failed = 0
$cases = @(
    @{ name = "GET /api/user/profile"; path = "/api/user/profile" },
    @{ name = "GET /api/user/light"; path = "/api/user/light" },
    @{ name = "GET /api/home/summary"; path = "/api/home/summary" },
    @{ name = "GET /api/community/summary"; path = "/api/community/summary" },
    @{ name = "GET /api/community/posts"; path = "/api/community/posts?tab=NEIGHBOR&page=1&pageSize=10" },
    @{ name = "GET /api/notifications"; path = "/api/notifications?page=1&pageSize=10" },
    @{ name = "GET /api/orders"; path = "/api/orders?tab=all&page=1&pageSize=10" },
    @{ name = "GET /api/orders/mine"; path = "/api/orders/mine?tab=DOING&page=1&pageSize=10" }
)

foreach ($c in $cases) {
    try {
        $resp = Invoke-Api "GET" $c.path $token $null
        if (-not (Assert-Ok $resp $c.name)) { $failed++ }
    } catch {
        Write-Host ("FAIL  " + $c.name + " -> " + $_.Exception.Message)
        $failed++
    }
}

if ($failed -gt 0) {
    Write-Host ("smoke failed: " + $failed + " case(s)")
    exit 1
}
Write-Host "smoke: all cases passed"
