/** HTML 转义：行内状态条用 v-html 渲染，外部文本（后端错误体等）入串前必须先转义 */
export function esc (s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}
