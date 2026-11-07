# GitHub 迁移指南（微光项目）

> 目标：把项目从"推 Gitee"转为"推 GitHub"，并保留 Gitee 作备份。
> 当前状态：本地测试相关改动已提交（`4c724e1`），只差把 commits 推到 GitHub。

## 一、先解决 GitHub 连不上（本机 hosts 被改）

本机 `C:\Windows\System32\drivers\etc\hosts` 把 `github.com` 等域名指向了 `127.0.0.1`，导致 GitHub 完全无法访问。用**管理员 PowerShell** 执行：

```powershell
$h = "$env:WINDIR\System32\drivers\etc\hosts"
Copy-Item $h "$h.bak"
Set-ItemProperty $h -Name IsReadOnly -Value $false
(Get-Content $h) | ForEach-Object { if ($_ -match 'github') { "# $_" } else { $_ } } | Set-Content $h -Encoding UTF8
ipconfig /flushdns
```

验证（应返回真实 IP，不是 127.0.0.1）：

```powershell
Resolve-DnsName github.com -Type A
```

## 二、在 GitHub 新建仓库

1. 打开 https://github.com/new
2. Repository name：`wei-guang`（英文名，别用中文）
3. 可见性：**Public**（作品集/找工作更有利；代码敏感选 Private）
4. **不要**勾选 "Add a README / .gitignore / license"（本地已有仓库，勾了会冲突）
5. 点 Create repository

## 三、推送（二选一）

### 方式 A：用现成脚本（推荐）
```powershell
cd C:\Users\17469\Desktop\微光
pwsh scripts/push-github.ps1 -RepoUrl "https://github.com/<你的用户名>/wei-guang.git"
```

### 方式 B：手动命令
```powershell
cd C:\Users\17469\Desktop\微光
git remote add github https://github.com/<你的用户名>/wei-guang.git
git push -u github master
```

> 保留 Gitee：`origin` 仍是 gitee，以后想同步备份可 `git push origin master`。
> 首次 push 弹登录：密码用 **Personal Access Token**（GitHub → Settings → Developer settings → Personal access tokens → Generate new token，勾 `repo`），不要用账号登录密码。

## 四、常见问题

- `remote github already exists`：先 `git remote remove github` 再重来（脚本已处理）。
- `Authentication failed`：确认用的是 Token 而不是密码；Token 需有 `repo`（或 Contents 读写）权限。
- 推送后看不到测试文件：确认 `postman/`、`docs/接口测试报告.*` 已提交（本地 `4c724e1` 已包含）；`.tools/`、`.postman/` 已加入 `.gitignore` 不入库。
