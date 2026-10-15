<template>
  <el-drawer
    v-model="store.drawerOpen"
    class="api-drawer"
    direction="rtl"
    size="min(400px, 92vw)"
    :with-header="false"
  >
    <div class="drawer-head">
      <strong>API 调用日志</strong>
      <div style="display:flex;gap:8px">
        <button class="btn tiny ghost" @click="store.clear()">清空</button>
        <button class="btn tiny ghost" @click="store.drawerOpen = false">收起</button>
      </div>
    </div>
    <div class="log-list">
      <div v-for="e in store.entries" :key="e.id" class="log-entry" :class="e.kind">
        <div class="l-meta">
          <span class="l-method">{{ e.method }}</span>
          <span>{{ e.path }}</span>
          <span>{{ e.extra }}</span>
        </div>
        <div v-if="e.body" class="l-body">{{ e.body }}</div>
      </div>
      <p v-if="!store.entries.length" class="muted">还没有请求——去走一遍三步向导。</p>
    </div>
  </el-drawer>
</template>

<script setup>
import { useApiLogStore } from '../stores/apiLog'

const store = useApiLogStore()
</script>

<style scoped>
.drawer-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px; color: var(--text);
}
.drawer-head strong { font-size: 14px; }
.log-list { height: calc(100vh - 110px); }
</style>
