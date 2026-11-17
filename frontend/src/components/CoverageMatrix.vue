<template>
  <div>
    <div class="table-wrap">
      <el-table :data="outcomes" style="width: 100%">
        <el-table-column label="状态" width="72">
          <template #default="{ row }">
            <span class="st-icon" :class="meta(row).cls" :title="meta(row).label">{{ meta(row).icon }}</span>
          </template>
        </el-table-column>
        <el-table-column label="要求" min-width="260">
          <template #default="{ row }">
            <div class="req-cell">
              <div class="req-name">{{ row.skill }}</div>
              <div class="req-raw">
                <template v-if="row.rawSkill && row.rawSkill !== row.skill">JD 原文写法：{{ row.rawSkill }} · </template>{{ row.description }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="判定通道" width="150">
          <template #default="{ row }">
            <span class="chan-tag" :class="row.channel || 'NONE'">{{ row.channel || 'NONE' }}</span>
            <div v-if="row.similarity != null" class="req-raw">sim={{ row.similarity.toFixed(3) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="证据" width="120">
          <template #default="{ row }">
            <el-popover
              v-if="row.evidences && row.evidences.length"
              placement="bottom-start"
              :width="340"
              trigger="hover"
              popper-class="ev-pop"
              :show-after="80"
            >
              <template #reference>
                <span class="ev-count">{{ row.evidences.length }} 条证据</span>
              </template>
              <h4>命中证据（简历原文片段）</h4>
              <p v-for="(e, i) in row.evidences" :key="i">{{ e }}</p>
            </el-popover>
            <span v-else class="muted">无证据</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="legend">
      <span class="st-COVERED">✓ 覆盖 {{ counts.COVERED }}</span>
      <span class="st-WEAK">~ 弱覆盖 {{ counts.WEAK }}</span>
      <span class="st-MISSING">✗ 缺失 {{ counts.MISSING }}</span>
    </div>
  </div>
</template>

<script setup>
/* JD 要求 × 证据覆盖矩阵：三态图标 + 通道标签 + 证据 hover 弹卡（el-popover） */
import { computed } from 'vue'

const props = defineProps({
  outcomes: { type: Array, default: () => [] },
})

const STATUS_META = {
  COVERED: { icon: '✓', cls: 'st-COVERED', label: '覆盖' },
  WEAK: { icon: '~', cls: 'st-WEAK', label: '弱覆盖' },
  MISSING: { icon: '✗', cls: 'st-MISSING', label: '缺失' },
}
const meta = (row) => STATUS_META[row.status] || { icon: '?', cls: '', label: row.status }

const counts = computed(() => {
  const c = { COVERED: 0, WEAK: 0, MISSING: 0 }
  for (const o of props.outcomes) if (c[o.status] != null) c[o.status]++
  return c
})
</script>
