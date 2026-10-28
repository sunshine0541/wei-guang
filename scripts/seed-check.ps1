# seed-check.ps1 - verify seed invariants against MySQL (docker container by default).
# Usage: powershell -ExecutionPolicy Bypass -File scripts\seed-check.ps1
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

function Query([string]$sql) {
    $result = docker exec -e "MYSQL_PWD=$dbPassword" $Container mysql -uroot -N -B -D $db -e $sql
    if ($LASTEXITCODE -ne 0) {
        throw "mysql query failed: $sql"
    }
    return ($result | Out-String).Trim()
}

Write-Host "seed-check: db=$db container=$Container"

$checks = @(
    @{ name = "PENDING limit per user"; expect = "0";
       sql = "SELECT COUNT(*) FROM (SELECT requester_id FROM help_order WHERE status='PENDING' GROUP BY requester_id HAVING COUNT(*) > (SELECT CAST(config_value AS UNSIGNED) FROM sys_config WHERE config_key='order.pending_limit')) t" },
    @{ name = "light_state values"; expect = "0";
       sql = "SELECT COUNT(*) FROM user WHERE light_state NOT IN ('UNLIT','LIT','STEADY','RESTING')" },
    @{ name = "point_ledger unique_key"; expect = "0";
       sql = "SELECT COUNT(*) FROM (SELECT unique_key FROM point_ledger GROUP BY unique_key HAVING COUNT(*) > 1) t" },
    @{ name = "order round = release_count + 1"; expect = "0";
       sql = "SELECT COUNT(*) FROM help_order WHERE current_round <> release_count + 1" },
    @{ name = "post like_count matches detail"; expect = "0";
       sql = "SELECT COUNT(*) FROM community_post p WHERE p.like_count <> (SELECT COUNT(*) FROM community_like l WHERE l.post_id = p.id)" },
    @{ name = "post comment_count matches detail"; expect = "0";
       sql = "SELECT COUNT(*) FROM community_post p WHERE p.comment_count <> (SELECT COUNT(*) FROM community_comment c WHERE c.post_id = p.id AND c.status = 'PUBLISHED')" },
    @{ name = "orders without requester"; expect = "0";
       sql = "SELECT COUNT(*) FROM help_order o LEFT JOIN user u ON u.id = o.requester_id WHERE u.id IS NULL" },
    @{ name = "posts without author"; expect = "0";
       sql = "SELECT COUNT(*) FROM community_post p LEFT JOIN user u ON u.id = p.author_id WHERE p.author_id <> 0 AND u.id IS NULL" },
    @{ name = "comments without post"; expect = "0";
       sql = "SELECT COUNT(*) FROM community_comment c LEFT JOIN community_post p ON p.id = c.post_id WHERE p.id IS NULL" },
    @{ name = "likes without post"; expect = "0";
       sql = "SELECT COUNT(*) FROM community_like l LEFT JOIN community_post p ON p.id = l.post_id WHERE p.id IS NULL" },
    @{ name = "completed orders have complete_requested_at"; expect = "0";
       sql = "SELECT COUNT(*) FROM help_order WHERE status='COMPLETED' AND complete_requested_at IS NULL" },
    @{ name = "order time sequence"; expect = "0";
       sql = "SELECT COUNT(*) FROM help_order WHERE (accepted_at IS NOT NULL AND accepted_at < created_at) OR (started_at IS NOT NULL AND (accepted_at IS NULL OR started_at < accepted_at)) OR (complete_requested_at IS NOT NULL AND (started_at IS NULL OR complete_requested_at < started_at)) OR (completed_at IS NOT NULL AND (complete_requested_at IS NULL OR completed_at < complete_requested_at)) OR (start_deadline IS NOT NULL AND accepted_at IS NOT NULL AND start_deadline < accepted_at)" },
    @{ name = "timeline aligned with order"; expect = "0";
       sql = "SELECT COUNT(*) FROM order_timeline t JOIN help_order o ON o.id=t.order_id WHERE (t.event_type='PUBLISH' AND t.created_at<>o.created_at) OR (t.event_type='ACCEPT' AND o.accepted_at IS NOT NULL AND t.created_at<>o.accepted_at) OR (t.event_type='START' AND o.started_at IS NOT NULL AND t.created_at<>o.started_at) OR (t.event_type='COMPLETE_REQUEST' AND o.complete_requested_at IS NOT NULL AND t.created_at<>o.complete_requested_at) OR (t.event_type IN ('CONFIRM','COMPLETE') AND o.completed_at IS NOT NULL AND t.created_at<>o.completed_at)" },
    @{ name = "completed duration sane (exclude #8 risk sample)"; expect = "0";
       sql = "SELECT COUNT(*) FROM help_order WHERE status='COMPLETED' AND id<>8 AND TIMESTAMPDIFF(MINUTE, started_at, complete_requested_at) NOT BETWEEN 15 AND 150" },
    @{ name = "risk sample #8 below online floor"; expect = "1";
       sql = "SELECT COUNT(*) FROM help_order WHERE id=8 AND status='COMPLETED' AND TIMESTAMPDIFF(MINUTE, started_at, complete_requested_at) < 5" },
    @{ name = "chat messages inside closed session"; expect = "0";
       sql = "SELECT COUNT(*) FROM chat_message m JOIN chat_session s ON s.id=m.session_id WHERE s.closed_at IS NOT NULL AND m.created_at > s.closed_at" },
    @{ name = "fraud report sample"; expect = "1";
       sql = "SELECT COUNT(*) FROM report WHERE type='FRAUD_RISK'" },
    @{ name = "demo account 13867429153"; expect = "1";
       sql = "SELECT COUNT(*) FROM user WHERE username = '13867429153'" }
)

$failed = 0
foreach ($c in $checks) {
    try {
        $actual = Query $c.sql
    } catch {
        Write-Host ("FAIL  " + $c.name + " -> query error")
        $failed++
        continue
    }
    if ($actual -eq $c.expect) {
        Write-Host ("PASS  " + $c.name)
    } else {
        Write-Host ("FAIL  " + $c.name + " -> expected " + $c.expect + ", actual " + $actual)
        $failed++
    }
}

if ($failed -gt 0) {
    Write-Host ("seed-check failed: " + $failed + " check(s)")
    exit 1
}
Write-Host "seed-check: all checks passed"
