# 知识与模板资产实施计划

1. 盘点现有知识文档和 XML 模板，确认 Web 侧资产目录需要收纳或引用哪些内容。
2. 定义仓库根目录 `ai-assets/` 的默认样例资产目录结构，采用 Markdown + JSON 双轨格式，并说明通过环境变量读取外部资产目录的约定。
3. 定义源码兜底检索白名单、忽略规则和引用展示规则。
4. 提炼 `template/` 的模板选择规则，说明每个模板适用场景和不适用场景，并在 `ai-assets/` 中通过索引引用模板，不复制模板文件。
5. 从现有配置导入导出接口格式整理导入 JSON 样例。
6. 定义 Web 修正记录与 agent skills 记忆文件的目录、格式和更新规则。
7. 创建 `ai-assets/README.md`、`ai-assets/retrieval-sources.json`、`ai-assets/template-index.json`、`ai-assets/import-json-example.json`、`ai-assets/correction-memory.md`。
8. 定义 `.agents/skills/dsp-*/` 下 skills 资产放置约定。
9. 验证本地默认样例资产和环境变量外部目录两种模式都能被后续实现任务采用。
10. 复核本任务产物是否能支撑 chat、Text2API Web 和 agent skills 三个后续子任务。
