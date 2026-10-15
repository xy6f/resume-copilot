> Spring Boot 3.5 单体 ｜ Spring AI 1.1 ｜ Vue 3 前端（前后端分离）｜ 无 LLM Key 自动降级为规则通道

**设计目标**：把"简历与 JD 的差距分析"这件手工活自动化——本工具：上传简历 → LLM 结构化抽取 → 逐条 JD 要求判"覆盖/弱覆盖/缺失" → 五维打分报告 → SSE 流式改写建议。**评测体系（golden.jsonl + EvalRunner）内建**，抽取准确率可复现、Prompt 版本可追溯——这是与"调 API 玩具"的分界线。

**功能速览**：PDF 解析（扫描件拒收）、结构化抽取（Schema 约束 + evidence 回溯防幻觉）、技能别名双通道匹配（规则 + 语义 Embedding 可选）、采纳率回写、历史分数曲线、管理接口统一鉴权；无 Key 时规则通道全链路可用（降级路径本身是被测行为）。

**快速开始**：

```bash
export SPRING_DATASOURCE_PASSWORD=your-mysql-password
export OPENAI_API_KEY=sk-xxx        # 可选，缺省走规则通道
mvn -s settings.xml spring-boot:run     # 8083
cd frontend && npm install && npm run dev   # 5175
```

---

# resume-copilot 简历-JD 匹配助手（Spring AI 单体应用）

上传简历 PDF + 录入目标 JD → 结构化抽取 → 逐条要求判"覆盖/弱覆盖/缺失" → 五维打分出报告 → SSE 流式改写建议。评测体系（golden.jsonl + EvalRunner）内建，指标可复现。

```bash
# 编译/测试（依赖缓存在 E 盘，勿用默认 C 盘仓库）
MVN=~/.m2/wrapper/dists/apache-maven-3.9.16/*/bin/mvn   # 或自己的 mvn
"$MVN" -s ../maven-settings.xml -f pom.xml test
"$MVN" -s ../maven-settings.xml -f pom.xml spring-boot:run   # 端口 8083
```

前置：MySQL `resume_copilot` 库已建（建库语句见 schema.sql 头注释），`application.yml` 里账号密码按本机调整。建表与别名字典由 `spring.sql.init` 每次启动幂等执行。

---

## 1. 模块状态表

| 模块 | 接口 | 状态 | 说明 |
|---|---|---|---|
| 简历摄取 | `POST /api/resumes`（multipart PDF） | 已实现，需 key | PDFBox 抽文 → LLM 结构化抽取 → 证据校验 → 落库 |
| 简历降级摄取 | `POST /api/resumes/raw`（JSON 文本） | 已实现，**无需 key** | 别名字典规则通道 lexical 抽取；或直接提交结构化 profile |
| 简历校正 | `PUT /api/resumes/{id}/profile` | 已实现，无需 key | 校正也要过证据闸，被丢技能如实返回 |
| 简历查询 | `GET /api/resumes/{id}` | 已实现 | |
| JD 录入 | `POST /api/jds` | 已实现，双路径 | 只给 rawText 走 LLM 解析（需 key）；带 `requirements` 直接结构化入库（无需 key） |
| JD 查询 | `GET /api/jds/{id}` | 已实现 | |
| 匹配报告 | `POST /api/match` `{resumeId, jdId}` | 已实现，**无需 key** | 规则通道打分；EmbeddingModel 存在时自动叠加语义通道 |
| 报告详情 | `GET /api/match/{reportId}` | 已实现 | 覆盖矩阵 + 五维分 + 权重 + 语义通道状态 + 被丢技能 |
| 改写建议 | `GET /api/match/{reportId}/advice`（SSE） | 已实现，需 key | 低分项逐条流式生成，事件 `advice_chunk/advice_item_done/done/error` |
| 评测跑批 | `POST /api/eval/run`（头 `X-Admin-Token`） | 已实现，**无需 key** | 确定性指标；用例同步 upsert 进 `t_eval_case` |
| 别名字典 | `t_skill_alias`（schema.sql 种子 41 行） | 已实现 | JUC/MyBatis-Plus/Spring Data Redis/SpringCloud 等；badcase 直接加行迭代 |
| Prompt 版本 | `t_prompt_version` 种子 3 行 | 已实现 | EXTRACT/JD_PARSE/ADVICE 三场景 v1 留档 |
| RAG 语料检索（Should） | — | 未做 | Redis Stack 向量索引，见计划书 W4 外扩项 |
| 历史版本对比（Should） | — | 未做 | `t_match_report` 已可按 resume_id 重跑对比，接口未封装 |

## 2. 没有 OPENAI_API_KEY 时：能用 / 不能用

**能用（全链路可跑）：**
- 应用启动：占位 key 只影响运行期调用，装配期不炸（`LlmAvailability` 显式判占位值，绝不打网络）
- 简历录入：`POST /api/resumes/raw` 走规则通道抽取（质量低于 LLM：技能靠字典别名命中，版式复杂会漏）
- JD 录入：`POST /api/jds` 在请求体直接带 `requirements: [{skill, description, minYears}]`
- 匹配打分出报告：引擎是纯 Java 规则通道（+可选语义通道），不碰对话模型
- 评测跑批：golden.jsonl 全部走确定性通道，指标可复现

**不能用（返回 503 + 明确错误码，附降级指引）：**
- PDF 上传的 LLM 抽取、JD 纯文本 LLM 解析、SSE 改写建议

错误码约定（全局异常体 `{code, message, hint}`）：
`LLM_NOT_CONFIGURED`=503（缺 key，重试无意义，按 hint 走降级路径）；`LLM_CALL_FAILED`=502（有 key 但调用失败，可重试）；入参/资源问题=400。

**开语义通道（可选）：** 配好真实 key 后设环境变量 `SPRING_AI_MODEL_EMBEDDING=openai`；不配则报告里固定标注"语义通道未启用"，语义判定不会静默生效也不会静默失败。

## 3. 冒烟示例（无 key）

```bash
# 1. 降级录入简历（规则通道抽取，prompt_version 落 v1-LEX 表明出身）
curl -X POST localhost:8083/api/resumes/raw -H "Content-Type: application/json" \
  -d '{"rawText":"技能：Java、JUC、Spring Boot、MyBatis-Plus、Redis\n项目经历\n- 基于Spring Boot实现订单模块，接口耗时下降40%"}'

# 2. 结构化直投 JD（降级路径，不碰 LLM）
curl -X POST localhost:8083/api/jds -H "Content-Type: application/json" \
  -d '{"title":"后端实习","rawText":"要求Java并发、MySQL","requirements":[{"skill":"Java并发","description":"精通Java并发编程","minYears":0}]}'

# 3. 纯规则通道匹配
curl -X POST localhost:8083/api/match -H "Content-Type: application/json" -d '{"resumeId":1,"jdId":1}'
curl localhost:8083/api/match/1

# 4. 改写建议：无 key 时 503（推流开始前判定，错误体是标准 JSON）
curl -i localhost:8083/api/match/1/advice

# 5. 评测跑批（口令默认 local-admin）
curl -X POST localhost:8083/api/eval/run -H "X-Admin-Token: local-admin"
```

## 4. 打分口径（速览）

- 覆盖矩阵：每条 JD 要求判 `COVERED/WEAK/MISSING`，规则通道三级（别名精确 → 标准名包含 → 经历文本兜底），语义通道只做 MISSING→WEAK 的兜底升格
- 五维（各 0~100，公式注释在 `ScoreEngine`）：技能覆盖 `(covered+0.5*weak)/N`；经历相关性按"半数要求命中"满分线；年限按"每段经历 1 年当量"粗代理；项目深度 `20*bullets+25*量化点`；表达质量看 bullet"15~120 字且含数字"占比
- 总分 = 加权和（权重在 `application.yml` `resume-copilot.match.*`，和必须为 1），评级线 85/70/55 = 优秀/良好/一般/较差

## 5. 真实评测接入步骤（把合成集换成真人标注集）

1. **收数据**：同学简历 PDF 脱敏后抽文本，按 `src/test/resources/eval/golden.jsonl` 一行一例的格式写：`{id, resumeText, expectedSkills, expectedBulletCount, note}`。`expectedSkills` 一律标"标准技能名"（`t_skill_alias.standard_name` 口径），期望经历条数人工数
2. **补字典**：跑 `mvn test`，若 `EvalRunnerTest` 召回率跌破阈值（阈值硬编码在测试里），按报告 `missedSkills` 往 schema.sql 加 `INSERT IGNORE` 别名片段——**加别名永远不加期望**，字典是系统的嘴，金标准是裁判的嘴，混改就是作弊
3. **换被测通道**：当前指标测的是确定性规则通道（`channel=RULE-ONLY(deterministic)`）。配 key 评 LLM 抽取版本时：`POST /api/resumes`（PDF）或 raw 接口不带 profile 改为调用 `ResumeExtractionService`，把抽出的 `profile.skills` 归一后对照同一份 golden 算同样的精确/召回——被测函数可换，指标口径与阈值不变
4. **版本化**：每次改 prompt 在 `t_prompt_version` 加一行（scene, version, note=badcase 出处），代码常量里的 SYSTEM_PROMPT 同步；跑 `POST /api/eval/run` 时 body `{"promptVersion":"v2"}`，两次版本报告对照——简历上"x%→y%"的出处就是这两份 JSON
5. **阈值上调**：合成集上规则通道指标是 1.0；换真实标注集后把 `EvalRunnerTest` 的阈值调到人工评审可接受的基线（改代码=改验收，评审留痕）

## 6. 目录速读

```
parse/    PdfTextExtractor            PDFBox 抽文 + 扫描件拒收
extract/  ResumeExtractionService     LLM 结构化抽取（temperature=0.1 + entity() JSON Schema）
jd/       JdIngestService/JdParsing   LLM 解析 或 结构化直投
match/    SkillDictionary             别名归一（规则通道心脏）
          ScoreEngine                 三态判定 + 五维公式（纯 Java，单测手算对分）
          EvidenceValidator           幻觉后置校验：evidence 必须是原文子串（空白归一化比对）
          LexicalResumeExtractor      无 key 降级抽取（也是评测被测通道）
advise/   AdviceService               SSE 流式改写建议 + 逐条落库
eval/     EvalRunner                  golden.jsonl 跑批：精确/召回/evidence 双向指标
config/   LlmAvailability             占位 key 判定：响亮 503，不打网络
controller/ …                         REST + 全局错误体 {code,message,hint}
src/test/resources/eval/golden.jsonl  5 份合成简历 + 人工标注（规则通道阈值测试的底座）
```

## 已知待办
- ~~GET /api/resumes/{id} 对不存在的 id 目前返回 500（IOException 未映射），应改为 404 + 友好错误体（启动验证时发现，2026-10-04）~~ 已修复（2026-10-04 QA）：新增 `NotFoundException`，`ApiExceptionAdvice` 统一映射 404 + `{code:"NOT_FOUND", message, hint}`（SSE 请求同样保住 JSON 错误体）；同类路径一并收口——`PUT /api/resumes/{id}/profile` 由 500 改 404，`GET /api/jds/{id}`、`GET /api/match/{id}`、`POST /api/match`（不存在的 resumeId/jdId）由语义错档的 400 改 404。收口单测见 `ApiExceptionAdviceTest` 与 `ResourceNotFoundContractTest`。
