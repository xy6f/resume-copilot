<template>
  <section class="view-enter">
    <div class="grid-2">
      <!-- 左：简历原文输入 -->
      <div class="glass card">
        <div class="card-head">
          <h2>简历原文</h2>
          <div class="head-actions">
            <button class="btn ghost" @click="fillSample">一键填充示例</button>
            <button class="btn primary" :disabled="busy" @click="ingest">摄取 →</button>
          </div>
        </div>
        <p class="hint">无 LLM key 时走规则抽取通道：技能逐行匹配别名字典，经历按标题+破折号行切分。</p>
        <div class="code-area">
          <el-input
            v-model="rawText"
            type="textarea"
            :autosize="false"
            resize="vertical"
            spellcheck="false"
            placeholder="把简历纯文本粘贴到这里（PDF 文本、Markdown 均可）…"
          />
        </div>
        <div class="inline-status">
          <div v-if="status" class="msg" :class="status.kind" v-html="status.html"></div>
        </div>
      </div>

      <!-- 右：结构化结果 -->
      <div class="glass card">
        <div class="card-head">
          <h2>结构化结果</h2>
          <span v-if="profile" class="badge-soft">{{ channelLabel }}</span>
        </div>

        <div v-if="!profile" class="empty-state">
          <svg viewBox="0 0 24 24" fill="none">
            <path d="M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" stroke="#5a648c" stroke-width="1.4"/>
            <path d="M14 3v4h4M9 12h6M9 16h4" stroke="#5a648c" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          <p>摄取后这里会展示基本信息、技能 chips 与经历时间线</p>
        </div>

        <template v-else>
          <div class="basic-grid">
            <template v-if="hasBasic">
              <div v-for="([k, v]) in basicFields" :key="k" class="basic-cell">
                <span>{{ k }}</span><strong>{{ v || '—' }}</strong>
              </div>
            </template>
            <div v-else class="basic-cell">
              <span>基本信息</span>
              <strong style="font-size:12px;color:var(--muted)">规则通道不抽取基本信息，配 OPENAI_API_KEY 走 LLM 抽取可得</strong>
            </div>
          </div>

          <h3 class="sec-title">
            技能
            <span v-if="discardNote" class="sec-note">{{ discardNote }}</span>
          </h3>
          <div class="chips">
            <el-tooltip
              v-for="(s, i) in profile.skills || []"
              :key="i"
              effect="dark"
              placement="top"
              :content="`证据：${s.evidence || '无'}`"
            >
              <span class="chip" :class="levelClass(inferLevel(s))">
                {{ s.name }}<span v-if="inferLevel(s)" class="lv">{{ inferLevel(s) }}</span>
              </span>
            </el-tooltip>
            <span v-if="!(profile.skills || []).length" class="muted">未抽出技能</span>
          </div>

          <h3 class="sec-title">经历</h3>
          <div class="timeline">
            <div v-for="(exp, i) in profile.experiences || []" :key="i" class="tl-card">
              <div class="tl-title">{{ exp.title || '经历条目' }}</div>
              <div class="tl-meta">{{ [exp.type, exp.org, exp.period].filter(Boolean).join(' · ') }}</div>
              <ul>
                <li
                  v-for="(b, j) in exp.bullets || []"
                  :key="j"
                  :class="{ 'has-num': (exp.highlights || []).includes(b) || /\d/.test(b) }"
                >{{ b }}</li>
              </ul>
            </div>
            <p v-if="!(profile.experiences || []).length" class="muted">
              未识别到"实习经历/项目经历"段落——请确保段落标题在 20 字内且 bullet 以 - 或 * 开头
            </p>
          </div>
        </template>
      </div>
    </div>

    <!-- 历史分数曲线（W4 遗留）：GET /api/resumes/{id}/history，同文本指纹最近 5 次摄取 -->
    <div class="glass card" style="margin-top:18px">
      <div class="card-head">
        <h2>历史分数曲线 <small class="kbd-tag">同文本指纹 · 最近 5 次摄取</small></h2>
        <div class="head-actions">
          <button class="btn ghost" :disabled="historyBusy" @click="loadHistory">刷新</button>
        </div>
      </div>

      <div v-if="history && history.items.length" class="history-body">
        <svg :viewBox="`0 0 ${H_W} ${H_H}`" class="history-svg" role="img" aria-label="历史分数折线">
          <!-- 0 / 50 / 100 参考线与刻度 -->
          <line v-for="gy in [0, 50, 100]" :key="'g' + gy"
                :x1="H_PAD_L" :x2="H_W - H_PAD_R" :y1="hY(gy)" :y2="hY(gy)"
                stroke="rgba(148,163,210,0.18)" stroke-width="1"/>
          <text v-for="gy in [0, 50, 100]" :key="'t' + gy"
                :x="H_PAD_L - 6" :y="hY(gy) + 3" text-anchor="end" font-size="9" fill="#8b93b5">{{ gy }}</text>
          <!-- 折线只连有分数的点；未出报告的摄取画空心点在基线上 -->
          <polyline
            v-if="scoredPoints.length > 1"
            :points="scoredPoints.map(p => `${p.x},${p.y}`).join(' ')"
            fill="none" stroke="var(--cyan)" stroke-width="1.8" stroke-linejoin="round"
          />
          <template v-for="(p, i) in historyPoints" :key="i">
            <circle v-if="p.scored" :cx="p.x" :cy="p.y" r="3.4" fill="var(--cyan)">
              <title>#{{ p.resumeId }} · {{ p.totalScore }} 分</title>
            </circle>
            <circle v-else :cx="p.x" :cy="hY(0)" r="3" fill="none" stroke="#8b93b5" stroke-width="1.2">
              <title>#{{ p.resumeId }} · 未评分</title>
            </circle>
            <text :x="p.x" :y="H_H - 8" text-anchor="middle" font-size="9" fill="#8b93b5">
              {{ p.dateLabel }}
            </text>
          </template>
        </svg>
        <ul class="history-list">
          <li v-for="(p, i) in historyPoints" :key="i">
            #{{ p.resumeId }} · {{ p.fullLabel }} →
            <b v-if="p.scored" class="history-score">{{ p.totalScore }} 分</b>
            <span v-else class="muted">未评分</span>
          </li>
        </ul>
      </div>
      <p v-else-if="!wizard.resumeId" class="muted">尚无简历——摄取之后，这里会出现曲线</p>
      <p v-else-if="history && !history.items.length" class="muted">
        暂无历史：同一份简历文本被重复摄取（并出过匹配报告）后，这里会画出分数曲线
      </p>
      <p v-else class="muted">加载历史失败或后端未就绪——点"刷新"重试</p>
    </div>
  </section>
</template>

<script setup>
/* 步骤 1：简历摄取（POST /api/resumes/raw -> GET /api/resumes/{id}） */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchJson } from '../api/http'
import { useWizardStore } from '../stores/wizard'
import { SAMPLE_RESUME } from '../utils/samples'
import { esc } from '../utils/esc'

const wizard = useWizardStore()
const rawText = ref('')
const busy = ref(false)
const status = ref(null) // {kind: ok|err|busy, html}
const profile = ref(null)

/* ===== 历史分数曲线（W4 遗留）：GET /api/resumes/{id}/history ===== */
const history = ref(null)   // {resumeId, fingerprint, items:[{resumeId,createdAt,totalScore,...}]}
const historyBusy = ref(false)
// SVG 画布几何（viewBox 坐标系，纯内联不引图表库=不新增依赖）
const H_W = 320
const H_H = 120
const H_PAD_L = 34
const H_PAD_R = 10
const H_PLOT_TOP = 10
const H_BASELINE = 84 // 0 分线的 y

/** 分数 0~100 -> y 坐标 */
function hY (score) {
  return H_PLOT_TOP + (H_BASELINE - H_PLOT_TOP) * (1 - score / 100)
}

/** createdAt 形如 "2026-10-01T09:00:00"（后端 LocalDateTime ISO 序列化） */
function dateOf (iso) {
  return iso ? String(iso).slice(5, 10) : '?'
}
function timeOf (iso) {
  return iso ? String(iso).slice(5, 16).replace('T', ' ') : '?'
}

const historyPoints = computed(() => {
  const items = history.value?.items || []
  const n = items.length
  const innerW = H_W - H_PAD_L - H_PAD_R
  return items.map((it, i) => ({
    ...it,
    scored: it.totalScore != null,
    x: n === 1 ? H_PAD_L + innerW / 2 : H_PAD_L + (innerW * i) / (n - 1),
    y: it.totalScore == null ? H_BASELINE : hY(it.totalScore),
    dateLabel: dateOf(it.createdAt),
    fullLabel: timeOf(it.createdAt),
  }))
})
const scoredPoints = computed(() => historyPoints.value.filter((p) => p.scored))

async function loadHistory () {
  if (!wizard.resumeId) {
    history.value = null
    return
  }
  historyBusy.value = true
  try {
    history.value = await fetchJson('GET', `/api/resumes/${wizard.resumeId}/history`)
  } catch {
    // 后端未就绪/id 失效：曲线区落空态即可，不打断简历主流程
    history.value = null
  } finally {
    historyBusy.value = false
  }
}

const channelLabel = computed(() =>
  wizard.ingest?.degraded ? '规则抽取通道（LLM 未配置时降级）' : 'LLM 抽取通道')

const basicFields = computed(() => {
  const b = profile.value?.basic || {}
  return [['姓名', b.name], ['年级', b.grade], ['学校', b.school], ['专业', b.major], ['GPA', b.gpa]]
})
const hasBasic = computed(() => basicFields.value.some(([, v]) => v))

const discardNote = computed(() => {
  const disc = wizard.ingest?.discardedSkills || []
  return disc.length ? `（证据校验丢弃 ${disc.length} 项：${disc.join('、')}）` : ''
})

/** 无 level 时从证据原文推断展示等级（仅展示辅助，后端数据不动） */
function inferLevel (skill) {
  if (skill.level) return skill.level
  const src = `${skill.evidence || ''}`
  for (const lv of ['精通', '掌握', '熟练', '熟悉', '了解']) {
    if (src.includes(lv)) return lv === '熟练' ? '熟悉' : lv
  }
  return ''
}
function levelClass (level) {
  if (!level) return 'lv-solid'
  if (level.includes('精通') || level.includes('掌握')) return 'lv-master'
  if (level.includes('熟悉') || level.includes('熟练')) return 'lv-familiar'
  if (level.includes('了解')) return 'lv-know'
  return 'lv-solid'
}

/* 修复原因：刷新后凭持久化的 resumeId 回读结构化结果，恢复右侧展示 */
onMounted(async () => {
  if (wizard.resumeId && !profile.value) {
    try {
      profile.value = await fetchJson('GET', `/api/resumes/${wizard.resumeId}`)
    } catch { /* 后端重启导致 id 失效：保持空态，用户重新摄取即可 */ }
  }
  loadHistory() // 历史曲线独立加载：结构化结果读失败也照样尝试画线
})

function fillSample () {
  rawText.value = SAMPLE_RESUME
  status.value = { kind: 'busy', html: '示例简历已填充——点击"摄取"走规则通道抽取' }
}

async function ingest () {
  const text = rawText.value.trim()
  if (!text) {
    status.value = { kind: 'err', html: '简历文本不能为空' }
    return
  }
  busy.value = true
  status.value = { kind: 'busy', html: '正在提交 /api/resumes/raw（规则抽取 + 证据校验）…' }
  try {
    // 只传 rawText：后端 lexical 规则通道抽取，无 LLM 也可用（degraded=true）
    const ing = await fetchJson('POST', '/api/resumes/raw', {
      body: { fileName: 'frontend-demo.txt', rawText: text },
    })
    wizard.resumeId = ing.resumeId
    wizard.ingest = ing
    wizard.persist()
    profile.value = await fetchJson('GET', `/api/resumes/${ing.resumeId}`)
    const disc = ing.discardedSkills?.length ? ` · 证据闸丢弃 ${ing.discardedSkills.length} 项` : ''
    status.value = {
      kind: 'ok',
      html: `摄取成功 · resumeId=<b>${ing.resumeId}</b> · 通道：${ing.degraded ? '规则抽取（LLM 降级）' : 'LLM 抽取'}${disc} —— 进入步骤 2`,
    }
    ElMessage.success(`简历已摄取（resumeId=${ing.resumeId}）`)
    loadHistory() // 新摄取入链：同文本重复摄取的点数 +1，曲线随之刷新
    setTimeout(() => wizard.goto(2), 600)
  } catch (e) {
    status.value = { kind: 'err', html: esc(e.message) }
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
/* 历史分数曲线卡：SVG 自适应宽度，列表给逐点读数（画线之外还要能"读出数"） */
.history-body { display: flex; gap: 18px; align-items: flex-start; flex-wrap: wrap; }
.history-svg { flex: 1 1 260px; max-width: 420px; height: auto; }
.history-list {
  list-style: none; margin: 0; padding: 0; font-size: 12px; color: var(--muted);
  display: flex; flex-direction: column; gap: 4px; min-width: 180px;
}
.history-score { color: var(--cyan); }
</style>
