/* 示例简历 / 示例 JD —— 从旧版 app.js 原样迁入（真实实习生简历口径） */

export const SAMPLE_RESUME = `张韦 · Java 后端开发实习生 · 求职意向：后端开发

教育背景：XX大学 软件工程 本科（2027届） GPA 3.7 / 4.0

技能清单
- 熟悉 Java 基础，掌握集合框架、IO 与常用设计模式
- 掌握 JUC 并发编程，理解 AQS、线程池核心参数与拒绝策略，有生产级调优实践
- 熟悉 Spring Boot 与 SpringMVC，理解 IoC / AOP 与自动装配原理
- 熟悉 MySQL 数据库，理解 B+ 树索引、事务隔离级别与慢查询优化
- 掌握 Redis 缓存，能处理缓存穿透、击穿与雪崩问题
- 熟悉 MyBatis-Plus 持久层框架与常见分页插件用法
- 了解 JVM 内存模型与常见 GC 收集器的调优思路

实习经历
XX科技有限公司 · Java 后端开发实习生 · 2025.06 - 2025.09
- 参与订单中心单体拆分，基于 Spring Boot + MyBatis 重构 3 个核心模块，接口平均响应时间下降 35%
- 使用 Redis 缓存热点商品数据，压测 QPS 从 800 提升至 2400，缓存命中率稳定在 92%
- 通过 JUC 线程池与 CompletableFuture 并行化聚合查询，报表导出耗时从 12s 降至 3s

项目经历
校园二手交易平台（个人项目，负责人）
- 基于 Spring Boot + MySQL 设计商品发布与交易流程，为高频查询字段建立组合索引，列表页查询耗时下降 80%
- 使用 Redis + setnx 分布式锁解决库存超卖问题，1000 并发压测下零超卖
- 引入线程池异步化图片审核链路，峰值吞吐提升 2.1 倍
- 基于 JWT 实现登录鉴权，接口统一异常处理与参数校验覆盖 100%`

export const SAMPLE_JD_TITLE = 'Java 后端开发实习生'
export const SAMPLE_JD_COMPANY = '某互联网科技公司'

/** 六条示例要求：格式 "技能名 | 要求描述 | 最低年限" */
export const SAMPLE_JD_LINES = [
  'Java | 扎实的 Java 基础，熟悉集合、IO 与常用设计模式 | 0',
  'Java并发 | 熟练掌握 JUC，理解 AQS、线程池参数调优，有高并发场景实践 | 0',
  'Spring Boot | 熟悉 Spring Boot / SpringMVC，理解 IoC、AOP 与自动装配 | 0',
  'MySQL | 掌握 MySQL 索引原理与 SQL 调优，理解事务与隔离级别 | 0',
  'Redis | 熟悉 Redis 常用数据结构，能处理缓存穿透/击穿/雪崩 | 0',
  'JVM | 了解 JVM 内存模型与 GC 调优思路 | 0',
]

/** 评测接口管理口令默认值：后端配置 resume-copilot.admin-token（${ADMIN_TOKEN:local-admin}） */
export const DEFAULT_ADMIN_TOKEN = 'local-admin'

/** 逐行解析："技能名 | 描述 | 年限"，年限缺失记 0（旧版 parseJdLines 迁入） */
export function parseJdLines (text) {
  return String(text || '').split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'))
    .map((l) => {
      const parts = l.split('|').map((p) => p.trim())
      const years = parts.length >= 3 ? (parseInt(parts[2], 10) || 0) : 0
      return { skill: parts[0], description: parts[1] || `${parts[0]} 相关要求`, minYears: years }
    })
}
