/* ==========================================================================
 * 统一 HTTP 层
 * - 一律使用相对路径 /api/...：开发期由 vite proxy 转发到 8083，
 *   生产构建同源部署，无 CORS、无硬编码主机（旧版 API_BASE 常量退役）。
 * - 每个请求自动落 API 日志（Pinia apiLog store），解析后端统一错误体
 *   {code,message,hint}，非 2xx 抛出带 .status/.body 的 Error。
 * ========================================================================== */
import { useApiLogStore } from '../stores/apiLog'

let seq = 0

/**
 * JSON 请求封装。
 * @param {string} method HTTP 方法
 * @param {string} path   以 /api 开头的路径
 * @param {object} opts   { body, headers, throwOnHttpError }
 */
export async function fetchJson (method, path, { body, headers, throwOnHttpError = true } = {}) {
  const store = useApiLogStore()
  const id = ++seq
  store.add({ id, kind: 'busy', method, path, extra: '…' })
  const t0 = performance.now()
  let res
  try {
    res = await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(headers || {}) },
      body: body == null ? undefined : JSON.stringify(body),
    })
  } catch (e) {
    store.patch(id, { kind: 'err', extra: '网络失败' })
    throw Object.assign(new Error(`无法连接后端（${location.origin} → 8083）：${e.message}`), { status: 0 })
  }
  const dur = `${Math.round(performance.now() - t0)}ms`
  let data = null
  const text = await res.text()
  try { data = text ? JSON.parse(text) : null } catch { data = { raw: text } }
  store.patch(id, { kind: res.ok ? 'ok' : 'err', extra: `${res.status} · ${dur}`, body: brief(data) })
  if (!res.ok && throwOnHttpError) {
    const msg = data && data.message ? `${data.code || res.status}: ${data.message}` : `HTTP ${res.status}`
    const err = new Error(msg)
    err.status = res.status
    err.body = data
    throw err
  }
  return data
}

/** 连通探针：走专用 /api/health（GUI 测试反馈——旧实现用必 404 的资源探测，
 *  浏览器 console 每 10s 记一条 404 噪音；健康检查就该是恒 200 的端点） */
export async function probeBackend () {
  try {
    const r = await fetch('/api/health', { method: 'GET' })
    return r.ok
  } catch {
    return false
  }
}

/** 日志只存摘要，防内存膨胀 */
function brief (data) {
  if (data == null) return null
  const s = typeof data === 'string' ? data : JSON.stringify(data)
  return s.length > 400 ? s.slice(0, 400) + '…' : s
}
