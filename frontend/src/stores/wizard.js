import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'

/* 修复原因：刷新后向导状态（resumeId/jdId/reportId/当前步骤）全部丢失，
 * 用户被迫从头再走一遍。用 sessionStorage 持久化主键与步骤，
 * 视图挂载时凭主键回读后端数据恢复展示。 */
const SS_KEY = 'resume-copilot.wizard.v1'

function loadPersisted () {
  try {
    const raw = sessionStorage.getItem(SS_KEY)
    if (!raw) return {}
    const p = JSON.parse(raw)
    return {
      step: Number(p.step) || 1,
      resumeId: p.resumeId ?? null,
      jdId: p.jdId ?? null,
      reportId: p.reportId ?? null,
      ingest: p.ingest ?? null,
    }
  } catch {
    return {}
  }
}

/**
 * 向导状态机：resumeId -> jdId -> reportId 三步流转（继承旧版全局 state）。
 * goto(n) 带前置校验：步骤只能前进到"已完成上一步"的位置。
 */
export const useWizardStore = defineStore('wizard', {
  state: () => {
    const p = loadPersisted()
    let step = Math.min(3, Math.max(1, p.step || 1))
    // 守卫：持久化的步骤不能越过未完成的前置条件
    if (step >= 2 && !p.resumeId) step = 1
    if (step >= 3 && !p.jdId) step = p.resumeId ? 2 : 1
    return {
      step,             // 当前视图 1..3
      resumeId: p.resumeId ?? null,   // 简历摄取后的主键
      jdId: p.jdId ?? null,           // JD 提交后的主键
      reportId: p.reportId ?? null,   // 匹配报告主键
      /** 摄取响应 {degraded, discardedSkills}，通道徽章用 */
      ingest: p.ingest ?? null,
      /** JD 提交成功后置真：报告视图进入时自动生成匹配（旧版 setTimeout(gotoStep(3)+runMatch) 的等价物） */
      autoMatch: false,
    }
  },
  getters: {
    canEnterJd: (s) => s.resumeId != null,
    canEnterReport: (s) => s.jdId != null,
  },
  actions: {
    goto (n) {
      if (n === 2 && !this.resumeId) {
        ElMessage.warning('请先摄取简历再进入下一步')
        return false
      }
      if (n === 3 && !this.jdId) {
        ElMessage.warning('请先提交目标 JD 再进入下一步')
        return false
      }
      this.step = n
      this.persist()
      return true
    },
    /** 把主键与当前步骤写入 sessionStorage（写入失败静默：隐私模式等场景不阻塞流程） */
    persist () {
      try {
        sessionStorage.setItem(SS_KEY, JSON.stringify({
          step: this.step,
          resumeId: this.resumeId,
          jdId: this.jdId,
          reportId: this.reportId,
          ingest: this.ingest,
        }))
      } catch { /* ignore */ }
    },
  },
})
