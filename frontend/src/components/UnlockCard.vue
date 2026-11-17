<template>
  <div class="unlock-card">
    <svg viewBox="0 0 24 24" fill="none">
      <!-- 浅紫引导系：锁体+钥匙孔用品牌紫描边，语义是"差一步配置"而非警告 -->
      <rect x="5" y="10" width="14" height="10" rx="2" stroke="#7C5CFC" stroke-width="1.6" />
      <path d="M8 10V7a4 4 0 0 1 8 0" stroke="#7C5CFC" stroke-width="1.6" />
      <circle cx="12" cy="15" r="1.6" fill="#7C5CFC" />
    </svg>
    <div>
      <h3>改写建议待解锁 · LLM_NOT_CONFIGURED</h3>
      <p>
        {{ body?.message || '改写建议需要真实对话模型，当前后端未检测到可用 OPENAI_API_KEY。' }}<br>
        {{ body?.hint || '配置后可用' }}。解锁方式：给后端进程设置环境变量
        <code>OPENAI_API_KEY</code>（可配 <code>OPENAI_BASE_URL</code> 指向任意兼容网关）后重启，
        再回到本报告页点击"拉取建议"。
      </p>
      <p class="unlock-note">
        注意：这不是故障——总分、五维与覆盖矩阵均来自确定性规则通道，
        已完整可用；只有这一个"改写建议"入口依赖模型。
      </p>
    </div>
  </div>
</template>

<script setup>
/* 503 -> 浅紫解锁卡：AI 产品把"未解锁"做成 feature 引导而非琥珀警告（旧版语义原样继承） */
defineProps({
  body: { type: Object, default: null },
})
</script>
