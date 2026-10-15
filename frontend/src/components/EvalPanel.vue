<template>
  <div class="glass card" style="margin-top:18px">
    <div class="card-head">
      <h2>评测指标</h2>
      <small class="dim-weights-note">{{ metaNote }}</small>
    </div>
    <div class="metric-grid">
      <div
        v-for="(m, i) in metrics"
        :key="runId + '-' + m.key"
        class="metric-card"
        :style="{ animationDelay: i * 0.09 + 's' }"
      >
        <div class="metric-value" :class="{ warn: m.value < 0.6 }">{{ disp[i].toFixed(1) }}%</div>
        <div class="metric-label">{{ m.label }}</div>
      </div>
    </div>
    <div class="table-wrap">
      <div class="eval-cases">
        <table>
          <thead>
            <tr><th>用例</th><th>召回</th><th>精确</th><th>漏/多抽技能</th><th>注入幻觉</th></tr>
          </thead>
          <tbody>
            <tr v-for="c in report.cases || []" :key="c.id">
              <td>{{ c.id }}</td>
              <td>{{ Math.round(c.recall * 100) }}%</td>
              <td>{{ Math.round(c.precision * 100) }}%</td>
              <td>
                <span v-for="s in c.missedSkills || []" :key="'m' + s" class="tag-skill tag-miss">{{ s }}</span>
                <span v-for="s in c.extraSkills || []" :key="'x' + s" class="tag-skill tag-extra">{{ s }}</span>
                <span v-if="!(c.missedSkills || []).length && !(c.extraSkills || []).length" class="muted">—</span>
              </td>
              <td>
                <span v-if="c.fakeSkillRejected" class="tag-skill tag-hit">已拦截</span>
                <span v-else class="tag-skill tag-miss">漏网</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
/* 评测指标翻牌：逐张入场翻转（animation-delay）+ 百分比 count-up（旧版 renderEval 迁入） */
import { computed, ref, watch } from 'vue'
import { countUp } from '../utils/countUp'

const props = defineProps({
  report: { type: Object, default: null },
})

const AGGREGATE_DEFS = [
  ['skillRecall', '技能召回率'],
  ['skillPrecision', '技能精确率'],
  ['experienceCountAccuracy', '经历条数准确率'],
  ['evidenceRetentionRate', '合法证据保留率'],
  ['hallucinationRejectionRate', '幻觉拦截率'],
]

const metrics = ref([])
const disp = ref([])
const runId = ref(0)

const metaNote = computed(() => {
  const r = props.report
  if (!r) return ''
  return `${r.channel} · 字典 ${r.aliasDictionarySize} 条 · 落库 ${r.evalCaseUpserted} 用例 · ${r.goldenLocation}`
})

watch(() => props.report, (r) => {
  if (!r) return
  runId.value++
  metrics.value = AGGREGATE_DEFS.map(([key, label]) => ({
    key, label, value: Number(r.aggregate?.[key] ?? 0),
  }))
  disp.value = metrics.value.map(() => 0)
  metrics.value.forEach((m, i) => {
    // 翻牌感 = 数字滚动 + 卡片入场翻转
    countUp((v) => { disp.value[i] = v * 100 }, m.value, 1100)
  })
}, { immediate: true })
</script>
