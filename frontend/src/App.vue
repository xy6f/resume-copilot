<template>
  <!-- 背景装饰：两团品牌色光晕，浅色下极淡 -->
  <div class="bg-glow glow-a"></div>
  <div class="bg-glow glow-b"></div>

  <header class="topbar glass">
    <div class="brand">
      <!-- 内联 SVG 徽标：文档+闪电，品牌紫单色系 -->
      <svg class="brand-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <rect x="4" y="3" width="13" height="18" rx="2.5" stroke="#7C5CFC" stroke-width="1.6"/>
        <path d="M7.5 8h6M7.5 11.5h6M7.5 15h4" stroke="#9aa3bd" stroke-width="1.4" stroke-linecap="round"/>
        <path d="M17.5 12.5l-2.8 4h3l-2 4.5 4.8-6h-3z" fill="#7C5CFC"/>
      </svg>
      <div>
        <h1>Resume Copilot</h1>
        <p class="subtitle">简历 × JD 匹配助手 · Spring AI 规则/语义双通道</p>
      </div>
    </div>
    <div class="topbar-actions">
      <!-- 顶栏连通状态灯（onMounted 探测 + 10s 巡检） -->
      <ConnPill />
      <!-- API 日志抽屉入口 -->
      <div class="log-tab" title="API 调用日志" @click="apiLog.drawerOpen = true">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M8 6l-5 6 5 6M16 6l5 6-5 6" stroke="currentColor" stroke-width="1.6"
            stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        API 日志 <span class="log-count">{{ apiLog.count }}</span>
      </div>
      <!-- 明暗主题切换（浅色默认，localStorage 持久化） -->
      <button
        class="theme-toggle"
        :title="isDark ? '切换到浅色主题' : '切换到暗色主题'"
        @click="toggleTheme"
      >
        <svg v-if="isDark" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="4.2" stroke="currentColor" stroke-width="1.6"/>
          <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4"
            stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none">
          <path d="M20 13.5A8 8 0 1 1 10.5 4a6.5 6.5 0 0 0 9.5 9.5z"
            stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>
  </header>

  <!-- Gamma 式左侧竖向进度轨（圆点+连线+步骤名+副标题；当前步品牌渐变圆点+6px 浅紫光环） -->
  <nav class="stepper glass" aria-label="向导步骤">
    <div class="step-rail">
      <div class="rail-track">
        <template v-for="(s, i) in STEPS" :key="s.name">
          <span class="rail-dot-wrap">
            <span class="rail-dot">{{ i + 1 }}</span>
          </span>
          <span v-if="i < STEPS.length - 1" class="rail-line" :class="{ done: wizard.step > i + 1 }"></span>
        </template>
      </div>
      <div class="rail-labels">
        <div
          v-for="(s, i) in STEPS"
          :key="s.name"
          class="rail-step-label"
          :class="{ current: wizard.step === i + 1, done: wizard.step > i + 1 }"
        >
          <button class="nm" @click="wizard.goto(i + 1)">{{ s.name }}</button>
          <span class="ds">{{ s.desc }}</span>
        </div>
      </div>
    </div>
  </nav>

  <main class="stage">
    <ResumeView v-show="wizard.step === 1" />
    <JdView v-show="wizard.step === 2" />
    <ReportView v-show="wizard.step === 3" />
  </main>

  <ApiLogDrawer />

  <footer class="footnote">
    resume-copilot frontend · Vue 3 + Element Plus · API 同源经 vite proxy 指向 <code>http://localhost:8083</code> · 无 key 环境全流程可演示
  </footer>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useWizardStore } from './stores/wizard'
import { useApiLogStore } from './stores/apiLog'
import ConnPill from './components/ConnPill.vue'
import ApiLogDrawer from './components/ApiLogDrawer.vue'
import ResumeView from './views/ResumeStep.vue'
import JdView from './views/JdStep.vue'
import ReportView from './views/ReportStep.vue'

const wizard = useWizardStore()
const apiLog = useApiLogStore()

const STEPS = [
  { name: '简历', desc: '粘贴文本 · 规则通道摄取' },
  { name: '目标 JD', desc: '逐行要求 · 结构化直投' },
  { name: '匹配报告', desc: '五维评分 · 覆盖矩阵 · 建议' },
]

/* 明暗主题：浅色为默认（AI 产品感），html.dark 持久化到 localStorage */
const isDark = ref(false)

function applyTheme (dark) {
  isDark.value = dark
  document.documentElement.classList.toggle('dark', dark)
  localStorage.setItem('rc-theme', dark ? 'dark' : 'light')
}

function toggleTheme () {
  applyTheme(!isDark.value)
}

onMounted(() => {
  // 与 main.js 的初始判断保持同一来源：localStorage > 默认浅色
  applyTheme(localStorage.getItem('rc-theme') === 'dark')
})
</script>

<style scoped>
/* 竖向进度轨布局细节（scoped）：轨道占 dot 宽，标签列等分撑开行距 */
.rail-labels { flex: 1; }
.rail-step-label { min-height: var(--rail-gap, 46px); }
.rail-step-label:first-child { min-height: 26px; justify-content: flex-start; }
.rail-step-label:last-child { min-height: 26px; justify-content: flex-end; }
/* 轨道高度与标签列总高一致（自然等分：三个 label 撑开整体高度） */
.step-rail { min-height: 118px; }
</style>
