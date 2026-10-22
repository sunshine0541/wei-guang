/**
 * 自动探测本机「局域网 IPv4」并写入 front/miniapp/.env.local 的 VITE_API_HOST。
 *
 * 背景：VITE_API_HOST 是**编译期**注入的，换网络/换电脑后旧 IP 会失效；
 * 这个小程序本身无法知道开发机的局域网 IP，故在**构建前**由 Node 探测并写入。
 *
 * 选取策略（频繁切换 Wi‑Fi 时尤为关键）：
 *   ① 首选「出口 IP」——对公网 UDP connect 一次，内核按路由表选定默认路由所用网卡，
 *      读回其本地地址；它与你现在真正联网的那张网卡一致，不受 IP 前缀影响。
 *   ② 出口 IP 落在被排除的网卡上（如 VPN 抢了默认路由）时，回退到「私网前缀优先级」排序。
 *   ③ 全程排除虚拟/热点网卡（WSL、Hyper‑V、各类 VPN、Docker、Wi‑Fi Direct 热点等）。
 *   ✳ 早期版本只按前缀排序（192.168.x 优于 10.x），热点/虚拟网卡因前缀更"像家用网"会被误选，
 *     一旦写死进产物就会一直连不上——故改为以出口 IP 为准，前缀仅作兜底。
 *
 * 用法：
 *   node scripts/detect-lan-host.mjs            # 探测并写入 .env.local
 *   node scripts/detect-lan-host.mjs --dry      # 只打印探测结果，不写文件
 *   $env:WGU_HOST='192.168.1.5:8080'; npm run dev:mp-weixin   # 手动指定（优先级最高）
 *   $env:WGU_HOST='localhost:8080';    npm run dev:mp-weixin   # 强制走 localhost（仅模拟器可用）
 *   ✳ 让手机连电脑热点时，出口 IP 是热点上游网卡，需用 WGU_HOST 显式指定热点地址（如 192.168.137.1:8080）
 *
 * 已在 package.json 挂为 predev:mp-weixin / prebuild:mp-weixin 前置钩子，故 `npm run` 时会自动执行。
 */
import os from 'node:os'
import fs from 'node:fs'
import path from 'node:path'
import dgram from 'node:dgram'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(__dirname, '..')
const envPath = path.join(projectRoot, '.env.local')
const dryRun = process.argv.includes('--dry')

const PORT = process.env.WGU_PORT || '8080'

/** 虚拟网卡 / 非目标网卡：WSL、Hyper-V 默认开关、各类 VPN、Docker、蓝牙、Wi-Fi Direct 热点等 */
const EXCLUDE_NAME = /(wsl|default switch|vpn|radmin|loopback|docker|virtual|vmware|hyper-?v|bluetooth|tailscale|zerotier|nordlynx|tap|tun|wi-?fi direct|local area connection\s*\*|本地连接\s*\*)/i

/** 私网地址优先级：192.168.x 最像家用/办公网 > 172.16–31.x > 10.x > 其它 */
function rank(ip) {
  if (ip.startsWith('192.168.')) return 1
  if (/^172\.(1[6-9]|2\d|3[01])\./.test(ip)) return 2
  if (ip.startsWith('10.')) return 3
  return 4
}

function collect() {
  const list = []
  for (const [name, addrs] of Object.entries(os.networkInterfaces())) {
    for (const addr of addrs || []) {
      if (addr.family !== 'IPv4' || addr.internal) continue
      if (addr.address.startsWith('169.254.')) continue // 链路本地，无效
      list.push({ name, address: addr.address })
    }
  }
  return list
}

/**
 * 取「出口 IPv4」：对公网 UDP connect 一次，内核按路由表为这次连接选定网卡，
 * 读回本地地址即可——比按 IP 前缀猜优先级可靠（热点/虚拟网卡常占 192.168.x）。
 * connect 只设定对端、不发包；离线或探测失败返回空串，交由前缀优先级兜底。
 */
function outboundIPv4(timeoutMs = 1500) {
  return new Promise((resolve) => {
    const sock = dgram.createSocket('udp4')
    let settled = false
    let timer = null
    const finish = (val) => {
      if (settled) return
      settled = true
      if (timer) clearTimeout(timer)
      try {
        sock.close()
      } catch (e) {
        /* 可能已关闭，忽略 */
      }
      resolve(val)
    }
    timer = setTimeout(() => finish(''), timeoutMs)
    sock.on('error', () => finish(''))
    sock.connect(53, '8.8.8.8', () => {
      const addr = sock.address().address
      finish(addr && addr !== '0.0.0.0' ? addr : '')
    })
  })
}

async function pick() {
  const all = collect()
  const real = all.filter((c) => !EXCLUDE_NAME.test(c.name))
  const pool = (real.length ? real : all).sort((a, b) => rank(a.address) - rank(b.address))
  const outbound = await outboundIPv4()
  // 出口 IP 必须落在未被排除的网卡上才采信，避免 VPN 抢走默认路由时选到虚拟地址
  const preferred = real.find((c) => c.address === outbound)
  return { all, pool, outbound, preferred }
}

const { all, pool, outbound, preferred } = await pick()

let host = process.env.WGU_HOST ? process.env.WGU_HOST.trim() : ''

if (!host) {
  if (!pool.length) {
    console.warn('[微光] 未探测到可用的局域网 IPv4，已跳过写入（可用 WGU_HOST=ip:端口 手动指定）')
    process.exit(0)
  }
  const chosen = preferred || pool[0]
  host = `${chosen.address}:${PORT}`
  const basis = preferred ? '出口 IP（当前默认路由所在网卡）' : '私网前缀优先级（未匹配到出口 IP）'
  console.log(`[微光] 选用 ${chosen.address}（网卡：${chosen.name}）· 依据：${basis}`)
  if (outbound && !preferred) {
    console.log(`[微光] 内核出口 IP 为 ${outbound}，但它属于已排除的网卡，已忽略`)
  }
  const others = pool.filter((c) => c !== chosen)
  if (others.length) {
    console.log('[微光] 其它候选：' + others.map((c) => `${c.address}(${c.name})`).join('、'))
  }
  const skipped = all.filter((c) => !pool.includes(c))
  if (skipped.length) {
    console.log('[微光] 已排除（虚拟/热点等非目标网卡）：' + skipped.map((c) => `${c.address}(${c.name})`).join('、'))
  }
} else {
  console.log(`[微光] 使用手动指定地址（WGU_HOST）：${host}`)
}

if (dryRun) {
  console.log(`[微光] --dry：不会写入文件。将要写入的值：VITE_API_HOST=${host}`)
  process.exit(0)
}

const content = `# 本机后端地址（此文件已被 .gitignore 忽略，永不入库）
# ✳ 本文件由 front/miniapp/scripts/detect-lan-host.mjs 在 npm run 前置钩子中自动重写（IP 会随网络变化）
#   手动指定：$env:WGU_HOST='192.168.1.5:8080'; npm run dev:mp-weixin
#   仅模拟器想走 localhost：$env:WGU_HOST='localhost:8080'; npm run dev:mp-weixin
#   手机连电脑热点调试：自动探测取的是「出口网卡」，需用 WGU_HOST 显式指定热点地址（如 192.168.137.1:8080）
#   真机调试需与本机同一局域网，并保证防火墙放行 8080（详见 README）
VITE_API_HOST=${host}
`

fs.writeFileSync(envPath, content, 'utf8')
console.log(`[微光] 已写入 ${path.relative(projectRoot, envPath)} → VITE_API_HOST=${host}`)
