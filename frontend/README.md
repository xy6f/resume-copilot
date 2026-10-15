# Resume Copilot 前端（Vue 3 + Vite + Element Plus + Pinia）

简历 × JD 匹配助手的前端，v2.0 从原生三件套升级为 Vue 3 工程化版本（纯 JavaScript，无 TypeScript）。
三步向导的交互语义、覆盖矩阵、SSE 解析、503 解锁卡等逻辑全部继承旧版 `app.js`，视觉保留
琥珀→玫红能量设计与 `#0b1020` 玻璃拟态（`src/styles/tokens.css`），叠加 Element Plus 暗色主题。

## 快速开始

```bash
cd frontend
npm install          # .npmrc 已指向 registry.npmmirror.com
npm run dev          # http://localhost:5175（strictPort，/api 代理到 8083）
npm run build        # 产物输出 dist/
npm run preview      # 预览 dist/
```

前置条件：后端已在 `8083` 运行（见根目录 README / application.yml）。
`vite.config.js` 中 `/api` 代理指向 `http://localhost:8083`，因此代码里一律使用相对路径
`/api/...`——这是前后端唯一的耦合点，无 CORS、无硬编码主机。

## 技术选型与结构

| 层 | 选型 | 位置 |
|---|---|---|
| 构建 | Vite 6（port 5175 strictPort + `/api` proxy） | `vite.config.js` |
| 框架 | Vue 3 SFC（`<script setup>`，纯 JS） | `src/` |
| UI | Element Plus 2 暗色主题（el-steps/el-table/el-popover/el-tooltip/el-drawer/el-input/ElMessage） | `main.js` 全量引入 |
| 状态 | Pinia：`wizard`（resumeId/jdId/reportId 流转 + 步骤前置校验）、`apiLog`（最近 80 条请求日志） | `src/stores/` |
| 设计系统 | tokens.css：CSS 变量 + 玻璃拟态 + 能量渐变，并与 EP 暗色变量桥接 | `src/styles/` |

```
frontend/
├── index.html
├── package.json / .npmrc / vite.config.js
├── dist/                          # 生产构建产物（已保留）
└── src/
    ├── main.js                    # EP 暗色主题 + Pinia + 中文 locale
    ├── App.vue                    # 顶栏 / el-steps 向导 / 视图切换 / 日志抽屉
    ├── api/http.js                # fetchJson 统一请求层（自动落日志、解析 {code,message,hint}）
    ├── stores/wizard.js           # 向导状态机（goto 带前置校验）
    ├── stores/apiLog.js           # API 日志 store
    ├── styles/tokens.css          # 设计系统（玻璃拟态 + EP 暗色桥接）
    ├── utils/samples.js           # 示例简历/JD 常量 + parseJdLines + 默认管理口令
    ├── utils/countUp.js / esc.js  # 数字滚动 / HTML 转义
    ├── components/
    │   ├── ConnPill.vue           # 顶栏连通状态灯（onMounted 探测 + 10s 巡检）
    │   ├── ApiLogDrawer.vue       # API 日志抽屉（el-drawer）
    │   ├── ScoreDonut.vue         # 总分圆环：SVG stroke 动画 + count-up
    │   ├── DimBars.vue            # 五维条形动画
    │   ├── CoverageMatrix.vue     # 覆盖矩阵（el-table + el-popover 证据 hover）
    │   ├── AdviceStream.vue       # SSE 手写解析（fetch + ReadableStream，503 → 解锁卡）
    │   ├── UnlockCard.vue         # 琥珀解锁卡（LLM_NOT_CONFIGURED）
    │   └── EvalPanel.vue          # 评测指标翻牌（逐张入场 + 百分比 count-up）+ 用例明细
    └── views/
        ├── ResumeStep.vue         # 步骤 1：简历摄取
        ├── JdStep.vue             # 步骤 2：JD 结构化直投
        └── ReportStep.vue         # 步骤 3：匹配报告 + 建议 + 评测
```

## 功能对照清单（旧版 → v2.0）

| 旧版 app.js | v2.0 落点 | 状态 |
|---|---|---|
| 三步向导 + 前置校验（无 resumeId 不进 JD、无 jdId 不进报告） | `stores/wizard.js` + `App.vue` el-steps 可点击标题 | ✓ |
| POST /api/resumes/raw → GET /api/resumes/{id}，degraded/discardedSkills 呈现 | `views/ResumeStep.vue`（通道徽章、证据闸丢弃注记） | ✓ |
| 技能 chips 色阶 + 证据 tooltip、经历时间线（含量化 bullet ★ 高亮）、basic 网格 | `ResumeStep.vue`（el-tooltip；level 推断逻辑原样迁入） | ✓ |
| JD 逐行解析「技能 \| 描述 \| 年限」实时预览 + POST /api/jds（parseBy=SUBMITTED） | `views/JdStep.vue` + `utils/samples.js parseJdLines` | ✓ |
| 提交 JD 后自动跳转并生成匹配 | `wizard.autoMatch` 联动 `ReportStep.vue` | ✓ |
| POST /api/match → GET /api/match/{id}；圆环 stroke 动画 + count-up、grade 徽章、出身三元组、语义通道标注 | `ReportStep.vue` + `ScoreDonut.vue` | ✓ |
| 五维条形动画 + 权重留痕 | `DimBars.vue` | ✓ |
| 覆盖矩阵三态（绿✓黄~红✗）+ 通道标签/sim + 证据 hover 弹卡 | `CoverageMatrix.vue`（el-popover 替代手写浮层） | ✓ |
| SSE：fetch + ReadableStream 手动分帧（advice_chunk / advice_item_done / error / done） | `AdviceStream.vue`（EventSource 读不到 503 JSON 体，故保留手动解析） | ✓ |
| 503 LLM_NOT_CONFIGURED → 琥珀解锁卡（非故障话术） | `UnlockCard.vue` | ✓ |
| POST /api/eval/run（X-Admin-Token，默认 `local-admin`）：指标翻牌 + 用例明细表 | `EvalPanel.vue` + 报告页口令输入 | ✓ |
| 连通状态灯（任意 HTTP 响应=在线；探针 GET /api/resumes/999999；10s 巡检；点击重测） | `ConnPill.vue` | ✓ |
| API 日志抽屉（80 条上限、busy/ok/err 三态、清空） | `ApiLogDrawer.vue` + `stores/apiLog.js` | ✓ |
| 示例简历 / 六条示例 JD / 一键填充 | `utils/samples.js` | ✓ |
| Toast | 行内状态条保留 + 关键节点 `ElMessage` | ✓ |

## 接口备忘（后端 8083）

- `POST /api/resumes/raw` `{fileName, rawText}` → `{resumeId, degraded, discardedSkills}`
- `GET /api/resumes/{id}` → ResumeProfile
- `POST /api/jds` `{title, company, rawText, source, requirements[]}` → `{jdId, parseBy, requirements}`
- `POST /api/match` `{resumeId, jdId}` → `{reportId, totalScore, grade, ...}`
- `GET /api/match/{id}` → `{reportId, totalScore, grade, detail{outcomes[], dimensions, weights, semanticChannelEnabled, ...}}`
- `GET /api/match/{id}/advice` → SSE；无 OPENAI_API_KEY 时 503 JSON `{code:LLM_NOT_CONFIGURED}`
- `POST /api/eval/run`（头 `X-Admin-Token`，默认值 `local-admin`，见 `resume-copilot.admin-token`）

全程离线演示约定：规则通道可用；仅"改写建议"依赖 LLM key。
