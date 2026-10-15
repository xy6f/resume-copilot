<template>
  <section class="view-enter">
    <div class="grid-2">
      <!-- 左：JD 录入 -->
      <div class="glass card">
        <div class="card-head">
          <h2>目标 JD</h2>
          <div class="head-actions">
            <button class="btn ghost" @click="fillSample">填充示例要求</button>
            <button class="btn primary" :disabled="busy" @click="submit">提交 →</button>
          </div>
        </div>
        <div class="grid-2" style="grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px">
          <div>
            <label class="field-label">岗位名称</label>
            <el-input v-model="title" placeholder="如：Java 后端开发实习生" />
          </div>
          <div>
            <label class="field-label">公司</label>
            <el-input v-model="company" placeholder="如：某互联网大厂" />
          </div>
        </div>
        <p class="hint">
          每行一条要求，格式：<code>技能名 | 要求描述 | 最低年限(可省)</code>。
          带 requirements 提交即走结构化直投（parseBy=SUBMITTED），完全不依赖模型。
        </p>
        <div class="code-area">
          <el-input
            v-model="lines"
            type="textarea"
            resize="vertical"
            spellcheck="false"
            placeholder="Java并发 | 熟练掌握 JUC，理解 AQS、线程池参数调优 | 0"
          />
        </div>
        <div class="inline-status">
          <div v-if="status" class="msg" :class="status.kind" v-html="status.html"></div>
        </div>
      </div>

      <!-- 右：要求条目预览 -->
      <div class="glass card">
        <div class="card-head"><h2>要求条目预览</h2></div>
        <div v-if="!reqs.length" class="empty-state">
          <svg viewBox="0 0 24 24" fill="none">
            <path d="M4 6h16M4 12h16M4 18h10" stroke="#5a648c" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          <p>逐行输入后这里实时列出将被提交的结构化条目</p>
        </div>
        <div v-for="(r, i) in reqs" :key="i" class="jd-item">
          <span class="req-skill">{{ r.skill }}</span>
          <span class="req-desc">{{ r.description }}</span>
          <span class="req-years">{{ r.minYears > 0 ? `≥ ${r.minYears} 年` : '年限不限' }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
/* 步骤 2：JD 结构化直投（POST /api/jds，带 requirements 即 parseBy=SUBMITTED） */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchJson } from '../api/http'
import { useWizardStore } from '../stores/wizard'
import { SAMPLE_JD_TITLE, SAMPLE_JD_COMPANY, SAMPLE_JD_LINES, parseJdLines } from '../utils/samples'
import { esc } from '../utils/esc'

const wizard = useWizardStore()
const title = ref('')
const company = ref('')
const lines = ref('')
const busy = ref(false)
const status = ref(null)

// 逐行实时解析预览（旧版 input 监听 → renderJdPreview 的响应式等价物）
const reqs = computed(() => parseJdLines(lines.value))

function fillSample () {
  title.value = SAMPLE_JD_TITLE
  company.value = SAMPLE_JD_COMPANY
  lines.value = SAMPLE_JD_LINES.join('\n')
  status.value = { kind: 'busy', html: '六条示例要求已填充——提交后走结构化直投（不依赖模型）' }
}

async function submit () {
  if (!reqs.value.length) {
    status.value = { kind: 'err', html: '至少填写一行要求（格式：技能 | 描述 | 年限）' }
    return
  }
  const t = title.value.trim() || '未命名岗位'
  const c = company.value.trim()
  // rawText 必填：后端把原文当事实源，这里用标题+要求行合成
  const rawText = `${t} ${c}\n`
    + reqs.value.map((r, i) => `${i + 1}. ${r.description}${r.minYears ? `（${r.minYears} 年以上）` : ''}`).join('\n')
  busy.value = true
  status.value = { kind: 'busy', html: '提交 /api/jds（带 requirements，结构化直投）…' }
  try {
    const out = await fetchJson('POST', '/api/jds', {
      body: { title: t, company: c, rawText, source: 'TEXT', requirements: reqs.value },
    })
    wizard.jdId = out.jdId
    wizard.persist()
    status.value = {
      kind: 'ok',
      html: `JD 已入库 · jdId=<b>${out.jdId}</b> · parseBy=<b>${esc(out.parseBy)}</b>`
        + (out.parseBy === 'SUBMITTED' ? '（结构化直投，未调用模型）' : '') + ' —— 即将生成匹配报告',
    }
    ElMessage.success(`JD 已入库（jdId=${out.jdId}）`)
    setTimeout(() => {
      if (wizard.goto(3)) wizard.autoMatch = true
    }, 700)
  } catch (e) {
    status.value = { kind: 'err', html: esc(e.message) }
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}
</script>
