# Resume Copilot 前端（Vue 3 + Vite + Element Plus + Pinia）

简历 × JD 匹配助手的前端，v2.0 从原生三件套升级为 Vue 3 工程化版本（纯 JavaScript，无 TypeScript）。
三步向导的交互语义、覆盖矩阵、SSE 解析、503 解锁卡等逻辑全部继承旧版 `app.js`。
v3.0 视觉重设计为"浅色 AI 产品感"（对标 Gamma / Kimi 智能助手），设计系统见 `src/styles/tokens.css`。

## v3.0 设计说明（浅色 AI 产品感）

设计基调：现代 AI 应用不再用深色科技风，而是**浅色 + 品牌渐变点缀 + 大圆角 + 呼吸感**。
业务逻辑与 API 层零改动，全部变化在样式层与少量展示性模板（进度轨结构、摘要行、呼吸点）。

### 1. 底色与卡片

- 主底 `#FCFCFD`，白卡片 `#ffffff`，大圆角 16px（`--radius`），柔和阴影 `0 1px 3px rgba(0,0,0,.06)`（hover 升为 `--shadow-pop`）。
- 暗色主题保留可切换：顶栏月亮/太阳按钮 + `localStorage`（key `rc-theme`）持久化，`index.html` 内联首帧防闪脚本，`html.dark` 整套路由到暗色变量并保留 EP dark css-vars 共存。

### 2. 品牌色系统（收敛为单一主色）

- 主色 `#7C5CFC`（紫，Kimi/Gamma 系），语义色仅保留状态用途（成功/失败/弱覆盖）。
- 渐变 `--grad`（`#9B7BFF → #7C5CFC → #6A4BE0`）**只用于三处**：
  1. Stepper 当前步圆点激活态；
  2. 总分圆环描边（SVG `linearGradient#donutGrad`）；
  3. 主 CTA 按钮（`.btn.primary`）。
  其余全部单色：五维条形、chips、时间线竖线、日志徽标等均为品牌色/中性色单色。

### 3. AI 感细节

- **三点呼吸加载**：`.breath`（三个错峰 0.2s 的弹跳渐隐圆点），用于摄取/生成/评测 busy 状态条与建议流首 token 前的等待行，替代 spinner。
- **建议流式文本区**：左侧 2px 品牌渐变竖线（`.advice-stream::before`），AI 输出区的视觉标识。
- **解锁卡浅紫化**：`UnlockCard` 由琥珀警示改为浅紫底（`--brand-soft`）+ 浅紫边（`--brand-border`）+ 品牌紫描边锁图标——AI 产品把"未解锁"做成 feature 引导而非警告。

### 4. Stepper 重设计（Gamma 式竖向进度轨）

顶部横向 el-steps 替换为**左侧竖向进度轨**（纯 CSS/模板实现，非组件库）：圆点 + 2px 连线 + 步骤名 + 副标题。
完成步为品牌色实心圆点带对勾色，当前步为品牌渐变填充圆点 + 外圈 6px 浅紫光环（`box-shadow: 0 0 0 6px var(--brand-soft)`）。
步骤名仍可点击跳转（`wizard.goto` 前置校验原样保留）。

### 5. 报告页信息层级重排

- 总分卡：圆环居左放大到 **200px**，右侧改为**三行摘要**——评级徽章（渐变）/ 最强维度（绿 tag）/ 最弱维度（琥珀 tag），promptVersion/通道/证据闸元信息收在摘要下方。
- **五维条形自总分卡下移**，独立整行展示。
- 覆盖矩阵：行高统一 **48px**，✓ / ~ / ✗ 状态图标 18px 字重、去底色徽章改为纯色字符（矩阵更"数据密集"，弱化装饰）。

### 6. EP 变量桥

`:root` 直接桥接 `--el-color-primary: var(--brand)`（即 `#7C5CFC`）及 text/border/fill 系列变量，el-input/el-table/el-popover/ElMessage 自动跟随品牌色与主题切换。

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
| 设计系统 | tokens.css v3：浅色 AI 产品感 + 单一品牌色 #7C5CFC + EP 变量桥（html.dark 暗色可切换） | `src/styles/` |

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
