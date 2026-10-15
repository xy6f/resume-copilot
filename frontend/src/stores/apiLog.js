import { defineStore } from 'pinia'

// 只留最近 80 条，防内存膨胀（与旧版一致）
const MAX_ENTRIES = 80

export const useApiLogStore = defineStore('apiLog', {
  state: () => ({
    /** 最近在前：{id, kind: busy|ok|err, method, path, extra, body, ts} */
    entries: [],
    drawerOpen: false,
  }),
  getters: {
    count: (s) => s.entries.length,
  },
  actions: {
    add (entry) {
      this.entries.unshift({ ...entry, ts: new Date().toLocaleTimeString() })
      if (this.entries.length > MAX_ENTRIES) this.entries.length = MAX_ENTRIES
      return entry.id
    },
    patch (id, data) {
      const e = this.entries.find((x) => x.id === id)
      if (e) Object.assign(e, data)
    },
    clear () {
      this.entries = []
    },
  },
})
