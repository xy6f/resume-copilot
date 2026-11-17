<template>
  <div class="advice-area">
    <p v-if="idle" class="muted">
      点击"拉取建议"尝试 GET /api/match/{id}/advice。未配置 OPENAI_API_KEY 时后端返回 503，这里会给出解锁指引。
    </p>

    <UnlockCard v-if="unlock" :body="unlockBody" />
    <div v-if="errMsg" class="msg err">{{ errMsg }}</div>

    <template v-if="started">
      <!-- 生成中（尚无首 token）：三点呼吸加载，比 spinner 现代 -->
      <div v-if="streaming && !text" class="breath-row">
        <span class="breath"><i></i><i></i><i></i></span>
        <span class="muted">正在生成改写建议…</span>
      </div>
      <div v-if="text" class="advice-stream">{{ text }}<span v-if="streaming" class="advice-cursor">▊</span></div>
      <div v-for="(item, i) in doneItems" :key="i" class="advice-done-tag">
        ✓ 第 {{ item.index }} 条建议已落库 #{{ item.adviceId }}（针对：{{ item.targetSkill }} · {{ item.status }}）
        <!-- 采纳率回写：每条已落库建议一组 采纳/忽略 按钮（PATCH /api/advice/{id}/status） -->
        <span v-if="item.adviceId" class="advice-decide">
          <span v-if="item.decision" class="advice-decided">
            已{{ item.decision === 'ACCEPTED' ? '采纳 ✓' : '忽略 ✕' }}
          </span>
          <template v-else>
            <button class="btn ghost mini" :disabled="item.sending"
                    @click="decide(item, 'ACCEPTED')">采纳</button>
            <button class="btn ghost mini" :disabled="item.sending"
                    @click="decide(item, 'IGNORED')">忽略</button>
          </template>
        </span>
      </div>
    </template>
  </div>
</template>

<script setup>
/* ==========================================================================
 * 改写建议：GET /api/match/{id}/advice（SSE）
 * 用 fetch + ReadableStream 手动解析 SSE 帧（EventSource 读不到 503 的 JSON 错误体）。
 * 无 OPENAI_API_KEY：后端在推流开始前返回标准 JSON 错误体 {code:LLM_NOT_CONFIGURED}
 * -> 呈现琥珀解锁卡；有 key：逐 token 追加 advice_chunk，item_done/done 收尾。
 * ======================================================================== */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import UnlockCard from './UnlockCard.vue'
import { fetchJson } from '../api/http'
import { useApiLogStore } from '../stores/apiLog'

const props = defineProps({
  reportId: { type: [Number, String], default: null },
})

const store = useApiLogStore()
const idle = ref(true)
const streaming = ref(false)
const started = ref(false)
const text = ref('')
// 每条 advice_item_done 的元数据：{index, adviceId, targetSkill, status, decision, sending}
const doneItems = ref([])
const unlock = ref(false)
const unlockBody = ref(null)
const errMsg = ref('')

let seq = 1000 // 日志 id 与 fetchJson 的序列错开

async function stream () {
  if (!props.reportId) return
  reset()
  idle.value = false
  started.value = true
  streaming.value = true
  const logId = ++seq
  store.add({ id: logId, kind: 'busy', method: 'GET', path: `/api/match/${props.reportId}/advice`, extra: 'SSE…' })
  try {
    const res = await fetch(`/api/match/${props.reportId}/advice`, {
      headers: { Accept: 'text/event-stream' },
    })
    if (!res.ok) {
      // 503/502：错误体是 JSON（后端对 SSE Accept 也保住了状态码+JSON 体）
      let body = null
      try { body = await res.json() } catch { /* 非 JSON 就当空 */ }
      if (res.status === 503 || body?.code === 'LLM_NOT_CONFIGURED') {
        unlock.value = true
        unlockBody.value = body
        started.value = false
        store.patch(logId, { kind: 'err', extra: '503 已降级呈现' })
      } else {
        errMsg.value = body?.message || `HTTP ${res.status}`
        started.value = false
        store.patch(logId, { kind: 'err', extra: String(res.status) })
      }
      return
    }
    store.patch(logId, { kind: 'ok', extra: '200 stream' })
    // 手动解析 SSE：按空行分帧，帧内 event:/data: 行（data 多行以 \n 连接）
    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      let idx
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const frame = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        handleFrame(frame)
      }
    }
  } catch (e) {
    errMsg.value = e.message
    store.patch(logId, { kind: 'err', extra: e.message })
  } finally {
    streaming.value = false
  }
}

function handleFrame (frame) {
  let event = 'message'
  const dataLines = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
  }
  const data = dataLines.join('\n')
  if (event === 'advice_chunk') {
    text.value += data // 逐 token 追加正文
  } else if (event === 'advice_item_done') {
    // 后端元数据字段：{index, adviceId, targetSkill, status}——adviceId 是回写入口
    try {
      const meta = JSON.parse(data)
      doneItems.value.push({
        index: meta.index ?? '',
        adviceId: meta.adviceId ?? null,
        targetSkill: meta.targetSkill || '低分项',
        status: meta.status || '',
        decision: '',
        sending: false,
      })
    } catch { /* 元信息解析失败不影响正文 */ }
    text.value += '\n\n'
  } else if (event === 'error') {
    // 流中途模型失败：状态码已是 200，后端以 error 事件收尾
    try { errMsg.value = JSON.parse(data).message } catch { errMsg.value = data }
  } else if (event === 'done') {
    // 收尾：光标由 streaming=false 移除
  }
}

function reset () {
  idle.value = false
  started.value = false
  streaming.value = false
  text.value = ''
  doneItems.value = []
  unlock.value = false
  unlockBody.value = null
  errMsg.value = ''
}

/* 采纳率回写（W4 遗留）：PATCH /api/advice/{id}/status，状态词形与后端枚举一致
 * （ACCEPTED=采纳 / IGNORED=忽略）。成功后按钮变徽章，失败只 Toast 不改状态。 */
async function decide (item, status) {
  item.sending = true
  try {
    await fetchJson('PATCH', `/api/advice/${item.adviceId}/status`, { body: { status } })
    item.decision = status
    ElMessage.success(status === 'ACCEPTED' ? `建议 #${item.adviceId} 已采纳` : `建议 #${item.adviceId} 已忽略`)
  } catch (e) {
    ElMessage.error(`状态回写失败：${e.message}`)
  } finally {
    item.sending = false
  }
}

// 报告换了，建议区重置
watch(() => props.reportId, reset)

defineExpose({ stream, reset })
</script>

<style scoped>
/* 采纳/忽略按钮组：贴在同一条落库提示之后（全局 .btn 体系内的 mini 尺寸补丁） */
.advice-decide { margin-left: 8px; display: inline-flex; gap: 6px; vertical-align: middle; }
.advice-decide .btn.mini { padding: 2px 10px; font-size: 11px; line-height: 1.6; }
.advice-decided { color: var(--brand, #7c5cfc); font-weight: 600; }
/* 生成中呼吸行：与流式文本区同视觉起点 */
.breath-row { display: flex; align-items: center; gap: 10px; padding: 12px 16px; }
.breath-row .breath { font-size: 0; }
</style>
