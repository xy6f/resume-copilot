<template>
  <div class="donut-wrap">
    <svg viewBox="0 0 120 120" class="donut">
      <circle cx="60" cy="60" r="50" class="donut-track" />
      <circle
        cx="60" cy="60" r="50" class="donut-value"
        :style="{ stroke: strokeColor, strokeDasharray: C, strokeDashoffset: offset }"
      />
    </svg>
    <div class="donut-center">
      <div class="donut-num">{{ display }}</div>
      <div class="donut-cap">总分</div>
    </div>
  </div>
</template>

<script setup>
/* 总分圆环：stroke 动画扫到分数 + 数字滚动（旧版 renderReport 圆环段迁入） */
import { computed, nextTick, ref, watch } from 'vue'
import { countUp } from '../utils/countUp'

const props = defineProps({
  score: { type: Number, default: 0 },
})

// 圆环半径 50 -> 周长，stroke-dasharray 动画基准
const C = 2 * Math.PI * 50
const offset = ref(C)
const display = ref(0)

// 高分玫红、中等琥珀、低分灰
const strokeColor = computed(() =>
  props.score >= 75 ? 'var(--rose)' : props.score >= 50 ? 'var(--amber)' : '#64748b',
)

watch(() => props.score, async (s) => {
  offset.value = C // 先归零，确保过渡从头播放
  display.value = 0
  await nextTick()
  requestAnimationFrame(() => { offset.value = C * (1 - s / 100) })
  countUp((v) => { display.value = Math.round(v) }, s, 1400)
}, { immediate: true })
</script>
