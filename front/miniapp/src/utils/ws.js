import { WS_HEARTBEAT_INTERVAL, WS_MAX_BACKOFF, WS_URL } from './constants'
import { getToken } from '@/api/request'

/**
 * WebSocket 客户端（详设 §3.5 + 确认项 22）
 * - 连接 ws://host/ws?token=<JWT>（小程序不支持自定义 header，token 走查询参数）
 * - 客户端每 30s 发 PING；服务端 75s 未收到任何帧会关闭连接
 * - 断线退避重连 1s → 2s → 4s … 上限 30s
 * - 信封协议：{type, payload, ts, clientMsgId}
 */
export function createWsClient() {
  let socketTask = null
  let heartbeatTimer = null
  let reconnectTimer = null
  let retryCount = 0
  let manualClosed = false
  let connected = false
  const messageListeners = []
  const statusListeners = []

  function emit(listeners, payload) {
    listeners.forEach((fn) => fn(payload))
  }

  function connect() {
    manualClosed = false
    const token = getToken()
    if (!token) {
      return
    }
    socketTask = uni.connectSocket({ url: `${WS_URL}?token=${token}` })
    socketTask.onOpen(() => {
      connected = true
      retryCount = 0
      startHeartbeat()
      emit(statusListeners, 'open')
    })
    socketTask.onMessage((res) => {
      let envelope = res.data
      try {
        envelope = JSON.parse(res.data)
      } catch (e) {
        // 非 JSON 帧按原文透出
      }
      emit(messageListeners, envelope)
    })
    socketTask.onClose(() => {
      connected = false
      stopHeartbeat()
      emit(statusListeners, 'close')
      scheduleReconnect()
    })
    socketTask.onError(() => {
      connected = false
      stopHeartbeat()
      emit(statusListeners, 'error')
      scheduleReconnect()
    })
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      send({ type: 'PING', payload: null })
    }, WS_HEARTBEAT_INTERVAL)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function scheduleReconnect() {
    if (manualClosed || reconnectTimer) {
      return
    }
    const delay = Math.min(1000 * Math.pow(2, retryCount), WS_MAX_BACKOFF)
    retryCount += 1
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function send(envelope) {
    if (!socketTask || !connected) {
      return false
    }
    socketTask.send({
      data: JSON.stringify({
        clientMsgId: `${Date.now()}-${Math.floor(Math.random() * 100000)}`,
        ts: Date.now(),
        ...envelope
      })
    })
    return true
  }

  function close() {
    manualClosed = true
    stopHeartbeat()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (socketTask) {
      socketTask.close({})
      socketTask = null
    }
    connected = false
  }

  return {
    connect,
    close,
    send,
    onMessage: (fn) => messageListeners.push(fn),
    onStatus: (fn) => statusListeners.push(fn),
    isConnected: () => connected
  }
}

export const wsClient = createWsClient()
