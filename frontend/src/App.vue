<template>
  <!-- 背景装饰：两团渐变光晕，衬托玻璃卡片 -->
  <div class="bg-glow glow-a"></div>
  <div class="bg-glow glow-b"></div>

  <header class="topbar glass">
    <div class="brand">
      <!-- 内联 SVG 徽标：文档+闪电，呼应"简历能量评分" -->
      <svg class="brand-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <rect x="4" y="3" width="13" height="18" rx="2.5" stroke="url(#lg1)" stroke-width="1.6"/>
        <path d="M7.5 8h6M7.5 11.5h6M7.5 15h4" stroke="#8b93b5" stroke-width="1.4" stroke-linecap="round"/>
        <path d="M17.5 12.5l-2.8 4h3l-2 4.5 4.8-6h-3z" fill="#f59e0b"/>
        <defs>
          <linearGradient id="lg1" x1="4" y1="3" x2="17" y2="21">
            <stop stop-color="#f59e0b"/><stop offset="1" stop-color="#f43f5e"/>
          </linearGradient>
        </defs>
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
          <path d="M8 6l-5 6 5 6M16 6l5 6-5 6" stroke="#f59e0b" stroke-width="1.6"
            stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        API 日志 <span class="log-count">{{ apiLog.count }}</span>
      </div>
    </div>
  </header>

  <!-- 三步向导 Stepper（el-steps；标题可点击，goto 带前置校验） -->
  <nav class="stepper glass" aria-label="向导步骤">
    <el-steps :active="wizard.step - 1" finish-status="success" align-center>
      <el-step v-for="(s, i) in STEPS" :key="s.name">
        <template #title>
          <button class="step-title-btn" @click="wizard.goto(i + 1)">{{ s.name }}</button>
        </template>
        <template #description>
          <small>{{ s.desc }}</small>
        </template>
      </el-step>
    </el-steps>
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
</script>
