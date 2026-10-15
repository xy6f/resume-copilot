<template>
  <div
    class="conn-pill"
    :class="{ ok: state === 'ok', bad: state === 'bad' }"
    title="点击重新探测 API 连通性"
    @click="probe"
  >
    <span class="conn-dot"></span>
    <span>{{ label }}</span>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { probeBackend } from '../api/http'

const state = ref('checking') // checking | ok | bad
const label = ref('检测中…')
let timer = null

async function probe () {
  state.value = 'checking'
  label.value = '检测中…'
  const ok = await probeBackend()
  if (ok) {
    state.value = 'ok'
    label.value = '后端在线 · 8083'
  } else {
    state.value = 'bad'
    label.value = '后端不可达'
  }
}

onMounted(() => {
  probe()
  timer = setInterval(probe, 10000) // 每 10s 巡检（旧版行为保留）
})
onBeforeUnmount(() => clearInterval(timer))
</script>
