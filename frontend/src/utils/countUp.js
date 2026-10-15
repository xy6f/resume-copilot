/**
 * 数字滚动动画（圆环总分、五维分、评测百分比共用）。
 * easeOutCubic，与旧版一致。
 * @param {(v:number)=>void} set  写入回调（Vue ref 赋值）
 * @param {number} target          目标值
 * @param {number} duration        毫秒
 * @param {number} delay           延迟毫秒
 */
export function countUp (set, target, duration, delay = 0) {
  setTimeout(() => {
    const t0 = performance.now()
    const tick = () => {
      const p = Math.min(1, (performance.now() - t0) / duration)
      set(target * (1 - Math.pow(1 - p, 3)))
      if (p < 1) requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  }, delay)
}
