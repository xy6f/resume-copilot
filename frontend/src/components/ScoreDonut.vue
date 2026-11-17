<template>
  <div class="donut-wrap">
    <svg viewBox="0 0 120 120" class="donut">
      <defs>
        <!-- 品牌渐变：总分圆环（三处保留渐变之一） -->
        <linearGradient id="donutGrad" x1="0" y1="0" x2="1" y2="1">
          <stop stop-color="#9B7BFF"/>
          <stop offset="0.55" stop-color="#7C5CFC"/>
          <stop offset="1" stop-color="#6A4BE0"/>
        </linearGradient>
      </defs>
      <circle cx="60" cy="60" r="50" class="donut-track" />
      <circle
        cx="60" cy="60" r="50" class="donut-value"
        :style="{ strokeDasharray: C, strokeDashoffset: offset }"
      />
    </svg>
    <div class="donut-center">
      <div class="donut-num">{{ display }}</div>
      <div class="donut-cap">总分</div>
    </div>
  </div>
</template>

<script setup>
/* 总分圆环：stroke 动画扫到分数 + 数字滚动（旧版 renderReport 圆环段迁入）
 * v3：200px 尺寸 + 品牌渐变描边（不再按分数改色，统一品牌色系统） */
import { nextTick, ref, watch } from 'vue'
import { countUp } from '../utils/countUp'

const props = defineProps({
  score: { type: Number, default: 0 },
})

// 圆环半径 50 -> 周长，stroke-dasharray 动画基准
const C = 2 * Math.PI * 50
const offset = ref(C)
const display = ref(0)

watch(() => props.score, async (s) => {
  offset.value = C // 先归零，确保过渡从头播放
  display.value = 0
  await nextTick()
  requestAnimationFrame(() => { offset.value = C * (1 - s / 100) })
  countUp((v) => { display.value = Math.round(v) }, s, 1400)
}, { immediate: true })
</script>
