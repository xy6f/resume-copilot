-- resume-copilot 表（库需先建：CREATE DATABASE IF NOT EXISTS resume_copilot DEFAULT CHARSET utf8mb4;）
-- W1 只建 t_resume；t_jd/t_match_report/t_eval_* 按计划书在 W2-W4 逐周补齐
CREATE TABLE IF NOT EXISTS t_resume (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_name      VARCHAR(256) NOT NULL,
  raw_text       MEDIUMTEXT   NOT NULL COMMENT 'PDF 原文，唯一事实源',
  profile_json   JSON         NOT NULL COMMENT 'ResumeProfile 序列化，MySQL JSON 类型可做 JSON_EXTRACT 调试',
  prompt_version VARCHAR(16)  NOT NULL COMMENT '可追溯性：哪版 prompt 抽的',
  model          VARCHAR(64)  NULL,
  token_cost     INT          NULL COMMENT '成本意识：每份简历烧了多少 token',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='简历记录';

-- ============================ W3：JD 录入（计划书 6 章 t_jd） ============================
-- 幂等说明：本脚本由 spring.sql.init.mode=always 每次启动执行，所有语句必须可重复跑，
-- 因此一律 CREATE TABLE IF NOT EXISTS / INSERT IGNORE，绝不使用 ALTER/DROP。
CREATE TABLE IF NOT EXISTS t_jd (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  title      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '岗位名（解析或录入时给出，可空串）',
  company    VARCHAR(128) NOT NULL DEFAULT '' COMMENT '公司名',
  raw_text   MEDIUMTEXT   NOT NULL COMMENT 'JD 原文，唯一事实源（与简历同权对待，可重解析）',
  req_json   JSON         NOT NULL COMMENT 'JdRequirements 序列化：要求条目数组（skill/description/minYears）',
  source     VARCHAR(32)  NOT NULL DEFAULT 'TEXT' COMMENT '录入渠道：TEXT 文本粘贴 / URL（W4 再说）',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='JD 记录';

-- ============================ W3：规则通道字典（计划书 5.2/6 章 t_skill_alias） ============================
-- 技能别名映射：JD 写"精通 Java 并发"、简历写"JUC"——纯字符串必漏，规则通道靠这张表对齐。
-- 唯一键(standard_name, alias) 让 INSERT IGNORE 天然幂等；种子数据即评测的"人工维护字典"，
-- 单测从本文件解析出同样这批数据，保证"生产字典"与"测试字典"永不漂移（见 SkillDictionaryTest）。
CREATE TABLE IF NOT EXISTS t_skill_alias (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  standard_name VARCHAR(64) NOT NULL COMMENT '标准技能名（字典的"值"，匹配都归一到它）',
  alias         VARCHAR(64) NOT NULL COMMENT '别名/变体写法（含标准名自身在代码里补，不必重复插）',
  channel       VARCHAR(16) NOT NULL DEFAULT 'RULE' COMMENT '通道标记：预留 SEMANTIC 扩展，当前只用 RULE',
  UNIQUE KEY uk_alias (standard_name, alias)
) ENGINE=InnoDB COMMENT='技能别名字典（规则通道）';

-- 种子数据 23 组常见前端方向实习 JD 技能别名（INSERT IGNORE 幂等）。
-- 取舍说明：故意不收"MP""MQ""ES"这类两三个字母的超短别名——归一化后是普通文本子串，
-- 会误命中"sample""RabbitMQ"等无关词（别名表宁缺毋滥，误报比漏报更难解释）。
INSERT IGNORE INTO t_skill_alias (standard_name, alias) VALUES
  ('Java', 'JavaSE'),
  ('Java', 'Core Java'),
  ('Java', 'Java 基础'),
  ('Java并发', 'JUC'),
  ('Java并发', 'Java并发编程'),
  ('Java并发', '多线程与并发'),
  ('Java并发', '并发编程'),
  ('Java并发', '高并发编程'),
  ('MyBatis', 'MyBatis-Plus'),
  ('MyBatis', 'MybatisPlus'),
  ('Redis', 'Spring Data Redis'),
  ('Redis', 'Redis缓存'),
  ('Spring', 'Spring Boot'),
  ('Spring', 'SpringBoot'),
  ('Spring', 'SpringMVC'),
  ('Spring', 'Spring框架'),
  ('Spring Cloud', 'SpringCloud'),
  ('Spring Cloud', '微服务组件'),
  ('MySQL', 'MariaDB'),
  ('MySQL', 'MySQL数据库'),
  ('JVM', 'Java虚拟机'),
  ('JVM', 'JVM调优'),
  ('Docker', '容器化Docker'),
  ('Kubernetes', 'K8s'),
  ('Kubernetes', '容器编排'),
  ('Linux', 'Linux基础'),
  ('Netty', 'NIO框架Netty'),
  ('Kafka', 'Apache Kafka'),
  ('Kafka', 'Kafka消息队列'),
  ('RabbitMQ', 'AMQP'),
  ('RocketMQ', 'Apache RocketMQ'),
  ('Elasticsearch', '全文检索Elasticsearch'),
  ('MongoDB', 'Mongo数据库'),
  ('Dubbo', 'Apache Dubbo'),
  ('Dubbo', 'RPC框架Dubbo'),
  ('Sentinel', '限流降级Sentinel'),
  ('Nginx', '反向代理Nginx'),
  ('计算机网络', 'TCP/IP'),
  ('计算机网络', 'HTTP协议'),
  ('数据结构与算法', '算法与数据结构'),
  ('数据结构与算法', '数据结构');

-- ============================ W3：匹配报告（计划书 6 章 t_match_report） ============================
CREATE TABLE IF NOT EXISTS t_match_report (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  resume_id      BIGINT      NOT NULL COMMENT '外键语义指向 t_resume.id（不建真外键：单体项目够用，删除更灵活）',
  jd_id          BIGINT      NOT NULL COMMENT '外键语义指向 t_jd.id',
  total_score    INT         NOT NULL COMMENT '五维加权总分 0~100',
  grade          VARCHAR(16) NOT NULL COMMENT '评级：优秀/良好/一般/较差',
  detail_json    JSON        NOT NULL COMMENT '覆盖矩阵（逐条要求状态+证据）+ 五维分 + 权重 + 语义通道开关',
  model          VARCHAR(64) NULL COMMENT '打分时配置的模型名（纯规则通道时为声明式记录）',
  token_cost     INT         NULL COMMENT '匹配环节 token（当前引擎纯规则=0，语义通道启用后回填）',
  prompt_version VARCHAR(16) NOT NULL COMMENT '报告出身：与简历/字典版本一起构成可复现三元组',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='匹配报告';

-- ============================ W4：改写建议（计划书 6 章 t_rewrite_advice） ============================
CREATE TABLE IF NOT EXISTS t_rewrite_advice (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id      BIGINT       NOT NULL COMMENT '属于哪份匹配报告',
  target_bullet  VARCHAR(256) NOT NULL DEFAULT '' COMMENT '针对的 JD 低分项（要求条目原文）',
  original_text  MEDIUMTEXT   NULL COMMENT '简历原句/证据；缺失项可为空',
  suggested_text MEDIUMTEXT   NULL COMMENT 'LLM 改写建议全文（流式完成后整段落库）',
  reason         VARCHAR(512) NOT NULL DEFAULT '' COMMENT '改动的考察点依据（计划书 5.3：每条建议必须给依据）',
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '采纳/忽略：PENDING/ACCEPTED/IGNORED——采纳率本身是产品指标',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='改写建议';

-- ============================ W2/W4：Prompt 版本化（计划书 6 章 t_prompt_version） ============================
-- Prompt 是"代码之外的事实源"：同版本 prompt + 同输入才可复现，评测跑批结果都要挂版本。
CREATE TABLE IF NOT EXISTS t_prompt_version (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  scene      VARCHAR(32) NOT NULL COMMENT '场景：EXTRACT 简历抽取 / JD_PARSE / ADVICE 改写建议',
  version    VARCHAR(16) NOT NULL COMMENT '版本号，与配置 resume-copilot.prompt-version 对应',
  content    MEDIUMTEXT  NOT NULL COMMENT 'prompt 全文留档（当前种子先存"出处指认"，正文在代码常量）',
  note       VARCHAR(512) NOT NULL DEFAULT '' COMMENT '这版为什么这么改（badcase 驱动的版本叙事）',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_scene_version (scene, version)
) ENGINE=InnoDB COMMENT='Prompt 版本表';

INSERT IGNORE INTO t_prompt_version (scene, version, content, note) VALUES
  ('EXTRACT', 'v1', '基线：整篇一次抽取，system 见 ResumeExtractionService.SYSTEM_PROMPT', 'W1 基线。分块抽取/few-shot 迭代在 W2，届时新增 v2 行'),
  ('JD_PARSE', 'v1', 'JD 要求条目抽取，system 见 JdParsingService.SYSTEM_PROMPT', '结构化输出 JdRequirements；temperature 复用低温度配置'),
  ('ADVICE', 'v1', '低分项改写，system 见 AdviceService.SYSTEM_PROMPT', '约束：只用简历既有事实、不得虚构、必须给依据');

-- ============================ W2：评测用例（计划书 6 章 t_eval_case） ============================
-- 标注集落库形态。当前跑批 EvalRunner 直接读 eval/golden.jsonl（文件即金标准、进 git 好 review），
-- 本表为 W4 "批量导入/版本对比"预留：case 内容稳定后可整表导入，指标口径不变。
CREATE TABLE IF NOT EXISTS t_eval_case (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_key      VARCHAR(64)  NOT NULL COMMENT '用例编号（与 golden.jsonl 的 id 一致）',
  resume_text   MEDIUMTEXT   NOT NULL COMMENT '标注用简历文本（合成样例为脱敏模拟数据）',
  expected_json JSON         NOT NULL COMMENT '人工金标准：期望技能标准名列表/期望经历数等',
  note          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '标注备注（哪些点故意埋了坑）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_case_key (case_key)
) ENGINE=InnoDB COMMENT='评测标注用例';
