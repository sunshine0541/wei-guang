# demo-reset.ps1 - reset demo database with sql\reset.sql, then run seed-check.
# 注意：本文件必须保存为 UTF-8 with BOM（Windows PowerShell 5.1 需要 BOM 才能正确解析中文注释）。
# 不要用 PowerShell 管道把 SQL 喂给 mysql（PS 5.1 会把中文重编码成 ANSI/GBK，落库变成 '?'）。
# Usage: powershell -ExecutionPolicy Bypass -File scripts\demo-reset.ps1
param(
    [string]$Container = "weiguang-mysql"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root "backend\.env"

function Read-EnvValue([string]$key, [string]$default) {
    if (-not (Test-Path $envFile)) { return $default }
    $line = Get-Content $envFile | Where-Object { $_ -match "^\s*$key\s*=" } | Select-Object -First 1
    if (-not $line) { return $default }
    $value = ($line -split "=", 2)[1].Trim()
    if ($value -eq "") { return $default }
    return $value
}

$db = Read-EnvValue "MYSQL_DATABASE" "weiguang"
$dbPassword = Read-EnvValue "MYSQL_PASSWORD" "weiguang123"
$sqlFile = Join-Path $root "sql\reset.sql"

if (-not (Test-Path $sqlFile)) {
    throw "reset.sql not found: $sqlFile"
}

Write-Host "demo-reset: container=$Container db=$db"
# 容器内固定路径；不要把 SQL 通过 PowerShell 管道喂给 mysql（PS 5.1 管道默认 ANSI/GBK，中文会变成 '?'）
$destination = $Container + ":/tmp/weiguang-reset.sql"
Write-Host "demo-reset: copy $sqlFile -> $destination"
docker cp $sqlFile $destination
if ($LASTEXITCODE -ne 0) {
    throw "docker cp reset.sql failed"
}
docker exec -e "MYSQL_PWD=$dbPassword" $Container mysql -uroot --default-character-set=utf8mb4 -D $db -e "source /tmp/weiguang-reset.sql"
if ($LASTEXITCODE -ne 0) {
    throw "reset.sql execution failed"
}
Write-Host "demo-reset: database reset done"

& (Join-Path $PSScriptRoot "seed-check.ps1") -Container $Container
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
Write-Host "demo-reset: done"
