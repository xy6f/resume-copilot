<template>
  <section class="view-enter">
    <!-- 操作条 -->
    <div class="report-actions glass card">
      <button class="btn primary big" :disabled="busy.match" @click="runMatch">生成匹配报告</button>
      <span class="hint" style="margin:0">需要：简历已摄取 + JD 已提交</span>
      <div class="spacer"></div>
      <el-input
        v-model="adminToken"
        size="small"
        style="max-width:150px"
        placeholder="X-Admin-Token"
        title="X-Admin-Token：与后端 resume-copilot.admin-token 配置一致"
      />
      <button class="btn cyan" :disabled="busy.eval" @click="runEval">跑评测（Eval Run）</button>
    </div>

    <div class="inline-status">
      <div v-if="status" class="msg" :class="status.kind" v-html="status.html"></div>
    </div>

    <template v-if="report">
      <!-- 总分圆环 + 五维 + 元信息 -->
      <div class="grid-2 report-top">
        <div class="glass card score-card">
          <ScoreDonut :score="totalScore" />
          <div class="score-side">
            <span class="grade-badge">{{ grade }}</span>
            <div class="meta-lines">
              promptVersion <b>{{ d.promptVersion }}</b> · model <b>{{ d.model || '—' }}</b>
              · 别名字典 <b>{{ d.aliasDictionarySize }}</b> 条<br>
              匹配通道：
              <span v-if="d.semanticChannelEnabled" class="chan-on">SEMANTIC 已启用</span>
              <template v-else>
                <span class="chan-off">仅规则通道</span>（{{ esc(d.semanticNote || '未配置 embedding 模型，装配期即跳过') }}）
              </template><br>
              证据闸丢弃：
              <b v-if="d.discardedSkills?.length" style="color:var(--rose)">{{ d.discardedSkills.join('、') }}</b>
              <b v-else>无</b>
              <template v-if="!d.discardedSkills?.length">（幻觉防线 0 拦截——本份无非法证据）</template>
            </div>
          </div>
        </div>
        <div class="glass card">
          <div class="card-head">
            <h2>五维得分</h2>
            <small class="dim-weights-note">{{ weightNote }}</small>
          </div>
          <DimBars :dimensions="d.dimensions || {}" />
        </div>
      </div>

      <!-- 覆盖矩阵 -->
      <div class="glass card" style="margin-top:18px">
        <div class="card-head">
          <h2>JD 要求 × 证据覆盖矩阵</h2>
          <small class="dim-weights-note">✓ 覆盖 · ~ 弱覆盖 · ✗ 缺失（证据悬停查看原文）</small>
        </div>
        <CoverageMatrix :outcomes="d.outcomes || []" />
      </div>

      <!-- 改写建议（SSE） -->
      <div class="glass card" style="margin-top:18px">
        <div class="card-head">
          <h2>改写建议 <small class="kbd-tag">SSE 流式</small></h2>
          <div class="head-actions">
            <button class="btn ghost" :disabled="busy.advice" @click="pullAdvice">拉取建议</button>
          </div>
        </div>
        <AdviceStream ref="adviceRef" :report-id="wizard.reportId" />
      </div>

      <!-- 评测指标卡 -->
      <EvalPanel v-if="evalReport" :report="evalReport" />
    </template>
  </section>
</template>

<script setup>
/* 步骤 3：匹配报告（POST /api/match -> GET /api/match/{id} -> 圆环/五维/矩阵/SSE 建议/评测） */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchJson } from '../api/http'
import { useWizardStore } from '../stores/wizard'
import { DEFAULT_ADMIN_TOKEN } from '../utils/samples'
import { esc } from '../utils/esc'
import ScoreDonut from '../components/ScoreDonut.vue'
import DimBars from '../components/DimBars.vue'
import CoverageMatrix from '../components/CoverageMatrix.vue'
import AdviceStream from '../components/AdviceStream.vue'
import EvalPanel from '../components/EvalPanel.vue'

const wizard = useWizardStore()
const report = ref(null)      // GET /api/match/{id} 响应
const evalReport = ref(null)  // POST /api/eval/run 响应
const status = ref(null)
const busy = ref({ match: false, eval: false, advice: false })
const adminToken = ref(DEFAULT_ADMIN_TOKEN)
const adviceRef = ref(null)

const d = computed(() => report.value?.detail || {})
const totalScore = computed(() => report.value?.totalScore ?? d.value.totalScore ?? 0)
const grade = computed(() => report.value?.grade || d.value.grade || '—')
const weightNote = computed(() => {
  const w = d.value.weights || {}
  return `权重 ${(w.skill ?? 0.35)}/${(w.experience ?? 0.25)}/${(w.years ?? 0.15)}/${(w.depth ?? 0.15)}/${(w.expression ?? 0.1)}`
})

async function runMatch () {
  if (!wizard.resumeId || !wizard.jdId) {
    status.value = { kind: 'err', html: '前置条件不满足：需要已摄取简历 + 已提交 JD' }
    return
  }
  busy.value.match = true
  status.value = { kind: 'busy', html: 'POST /api/match 生成报告（纯规则通道，无需模型）…' }
  try {
    const created = await fetchJson('POST', '/api/match', {
      body: { resumeId: wizard.resumeId, jdId: wizard.jdId },
    })
    wizard.reportId = created.reportId
    wizard.persist()
    report.value = await fetchJson('GET', `/api/match/${created.reportId}`)
    status.value = {
      kind: 'ok',
      html: `报告 #${report.value.reportId} 已生成 · 语义通道：`
        + (created.semanticChannelEnabled || report.value.detail?.semanticChannelEnabled
          ? '<b>启用</b>' : '<b>未启用（降级，报告中已注明）</b>'),
    }
  } catch (e) {
    status.value = { kind: 'err', html: esc(e.message) }
    ElMessage.error(e.message)
  } finally {
    busy.value.match = false
  }
}

function pullAdvice () {
  if (!wizard.reportId) {
    status.value = { kind: 'err', html: '请先生成匹配报告' }
    return
  }
  adviceRef.value?.stream()
}

async function runEval () {
  busy.value.eval = true
  status.value = { kind: 'busy', html: '跑评测（评测不依赖模型，无 key 环境同样可跑）…' }
  try {
    evalReport.value = await fetchJson('POST', '/api/eval/run', {
      headers: { 'X-Admin-Token': adminToken.value.trim() || DEFAULT_ADMIN_TOKEN },
      body: {},
    })
    status.value = {
      kind: 'ok',
      html: `评测完成 · promptVersion=${esc(evalReport.value.promptVersion)} · 用例 ${evalReport.value.caseCount} 条`,
    }
    ElMessage.success('评测跑批完成')
  } catch (e) {
    const hint = e.status === 403 ? '（X-Admin-Token 不对：后端配置 resume-copilot.admin-token）' : ''
    status.value = { kind: 'err', html: `${esc(e.message)} ${esc(hint)}` }
    ElMessage.error(e.message)
  } finally {
    busy.value.eval = false
  }
}

/* 修复原因：刷新后凭持久化的 reportId 回读报告，恢复圆环/五维/矩阵展示 */
onMounted(async () => {
  if (wizard.reportId && !report.value) {
    try {
      report.value = await fetchJson('GET', `/api/match/${wizard.reportId}`)
    } catch { /* 后端重启导致 id 失效：保持空态，重新生成即可 */ }
  }
})

// JD 提交成功后进入本页自动生成一次匹配（旧版 gotoStep(3)+runMatch 联动）
watch(() => [wizard.step, wizard.autoMatch], ([step, auto]) => {
  if (step === 3 && auto) {
    wizard.autoMatch = false
    runMatch()
  }
})
</script>
