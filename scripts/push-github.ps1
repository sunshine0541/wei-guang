#Requires -Version 7.0
<#
  一键把「微光」项目推送到 GitHub。
  用法（在仓库根目录或任意位置运行均可）：
     pwsh scripts/push-github.ps1 -RepoUrl "https://github.com/<你的用户名>/wei-guang.git"
  说明：
     - 不会动现有 gitee 的 origin 远程，新增名为 github 的远程。
     - 推 master 分支，并把上游设为 github/master。
     - 首次推送若弹登录：账号填 GitHub 用户名，密码填 Personal Access Token（勾 repo 权限）。
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoUrl
)

$ErrorActionPreference = "Stop"
# 切到脚本所在仓库根（scripts 的上一级）
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "== 校验 GitHub 连通性 ==" -ForegroundColor Cyan
$dns = (Resolve-DnsName github.com -Type A -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress }).IPAddress
if ($dns -contains "127.0.0.1" -or -not $dns) {
    Write-Host "⚠ 仍解析到 127.0.0.1：hosts 还没生效。请先用管理员改 hosts 并 ipconfig /flushdns。" -ForegroundColor Yellow
}

Write-Host "== 配置远程 github ==" -ForegroundColor Cyan
git remote remove github 2>$null
git remote add github $RepoUrl
git remote -v

Write-Host "== 推送 master ==" -ForegroundColor Cyan
git push -u github master

Write-Host "== 完成 ==" -ForegroundColor Green
git log --oneline -1
