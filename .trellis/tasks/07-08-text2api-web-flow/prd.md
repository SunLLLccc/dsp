# Text2API Web 流程

## Goal

实现管理后台 Text2API Web 流程，让已登录用户从自然语言、粘贴文本或受限需求文档生成 SQL 类联机接口配置，并在最终确认后导入发布。

## Requirements

- 后端能力归属 `dsp-admin-service`，前端在智能助手页面的 `text2api` 工作区承载。
- 输入支持直接输入/粘贴需求文本，也支持上传 Markdown/HTML 需求文档，单文件不超过 30KB。
- Text2API 草稿需要服务端持久化，包含需求原文、当前阶段、接口输入输出、SQL、模板选择、XML、导入 JSON、修正记录和逻辑删除标记。
- 中间步骤只保存草稿，不创建已生效的半成品联机接口。
- 只有最终“导入并发布”动作会调用兼容现有配置导入格式的发布能力。
- Text2SQL 生成 SQL 前必须具备表结构/字段说明依据；依据可来自当前会话、知识库或受控数据源元数据读取。
- 缺少表结构依据时，AI 必须追问，不能生成无依据 SQL。
- 一期支持受控读取 `MYSQL`、`DORIS`、`ORACLE`、`POSTGRESQL` 数据源元数据，只读结构信息，不读业务数据行。
- XML 生成必须模板驱动；生成前展示模板选择结果并获得用户确认。
- 用户指出错误时，Web 页面保存修正记录/学习记录，不自动更新 RAG 或项目知识库。
- 草稿生成对所有登录用户开放；元数据读取限制为 `DEPT_MANAGER` 或 `ADMIN`；最终导入发布限制为 `IMPORTER` 或 `ADMIN`。

## Acceptance Criteria

- [ ] 用户可以创建、恢复、逻辑删除 Text2API 草稿。
- [ ] 用户可以粘贴需求文本或上传不超过 30KB 的 Markdown/HTML 需求文档。
- [ ] 接口定义、SQL、模板选择、XML/导入 JSON 都有明确用户确认点。
- [ ] 中间确认点只更新草稿，不发布半成品接口。
- [ ] 缺少表结构依据时，Text2SQL 追问而不是生成 SQL。
- [ ] 用户可在权限允许时选择已配置 SQL 数据源读取结构元数据。
- [ ] XML 生成前展示模板文件、适用场景、选择理由、填充点和复核项。
- [ ] 最终导入 JSON 兼容现有配置导入导出格式。
- [ ] 最终导入并发布复用 `IMPORTER`/`ADMIN` 权限边界。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
