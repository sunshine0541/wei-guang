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
  let heartbeatTimer = null
  let reconnectTimer = null
  let retryCount = 0
  let manualClosed = false
  let connected = false
  let connecting = false
  let handlersBound = false
  const messageListeners = []
  const statusListeners = []

  function emit(listeners, payload) {
    listeners.forEach((fn) => fn(payload))
  }

  /** 全局 Socket 事件（uni.onSocketXxx）：本模块为单例，仅注册一次 */
  function bindGlobalHandlers() {
    if (handlersBound) {
      return
    }
    handlersBound = true
    uni.onSocketOpen(() => {
      connected = true
      connecting = false
      retryCount = 0
      startHeartbeat()
      emit(statusListeners, 'open')
    })
    uni.onSocketMessage((res) => {
      let envelope = res.data
      try {
        envelope = JSON.parse(res.data)
      } catch (e) {
        // 非 JSON 帧按原文透出
      }
      emit(messageListeners, envelope)
    })
    uni.onSocketClose(() => {
      connected = false
      connecting = false
      stopHeartbeat()
      emit(statusListeners, 'close')
      scheduleReconnect()
    })
    uni.onSocketError(() => {
      connected = false
      connecting = false
      stopHeartbeat()
      emit(statusListeners, 'error')
      scheduleReconnect()
    })
  }

  function connect() {
    if (connecting || connected) {
      return
    }
    connecting = true
    manualClosed = false
    const token = getToken()
    if (!token) {
      connecting = false
      return
    }
    // 注意：uni.connectSocket 的返回值在 Vue3 运行时不是 SocketTask（被 Promise 化，
    // socketTask.onOpen 不存在），故统一改用 uni 全局 Socket 事件。
    bindGlobalHandlers()
    uni.connectSocket({ url: `${WS_URL}?token=${token}` })
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
    if (!connected) {
      return false
    }
    try {
      uni.sendSocketMessage({
        data: JSON.stringify({
          clientMsgId: `${Date.now()}-${Math.floor(Math.random() * 100000)}`,
          ts: Date.now(),
          ...envelope
        })
      })
      return true
    } catch (e) {
      return false
    }
  }

  function close() {
    manualClosed = true
    stopHeartbeat()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (connected || connecting) {
      try {
        uni.closeSocket({})
      } catch (e) {
        // 忽略关闭异常
      }
    }
    connected = false
    connecting = false
  }

  return {
    connect,
    close,
    send,
    onMessage: (fn) => {
      messageListeners.push(fn)
      return () => {
        const index = messageListeners.indexOf(fn)
        if (index >= 0) {
          messageListeners.splice(index, 1)
        }
      }
    },
    onStatus: (fn) => {
      statusListeners.push(fn)
      return () => {
        const index = statusListeners.indexOf(fn)
        if (index >= 0) {
          statusListeners.splice(index, 1)
        }
      }
    },
    isConnected: () => connected
  }
}

export const wsClient = createWsClient()
