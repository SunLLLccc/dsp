# DSP 项目待办事项

## P0 -- 致命（必须立即修复）

- [ ] **P0-1: SpEL表达式注入修复**
  - DynamicSqlHandler/HttpExecutor/DubboExecutor使用StandardEvaluationContext解析用户输入，可导致远程代码执行
  - 修复：改用SimpleEvaluationContext，限制只能访问变量和属性

- [ ] **P0-2: 管理端JWT字段名不匹配修复**
  - LoginController:49 写入claim `appId`，AdminAuthInterceptor:47 读取 `adminUser`，导致登录后所有请求被拒
  - 修复：统一字段名

- [ ] **P0-3: XXE外部实体注入修复**
  - XmlConfigParser:17 使用DocumentHelper.parseText()未禁用外部实体
  - 修复：使用SAXReader并设置 disallow-doctype-decl

- [ ] **P0-4: MongoDB JSON注入修复**
  - MongoExecutor resolveParams()直接字符串替换#{param}，可注入恶意JSON
  - 修复：对字符串值做JSON转义

- [ ] **P0-5: PaginationHandler SQL拼接注入修复**
  - orderBy直接拼接到SQL字符串，可通过篡改XML注入
  - 修复：白名单校验（只允许字母数字下划线）

- [ ] **P0-6: 导出下载绕过鉴权修复**
  - 前端window.open不带token，后端拦截器拒绝
  - 修复：改用axios请求Blob或URL传token

## P1 -- 严重（应尽快修复）

- [ ] **P1-1: 管理员默认密码admin/admin123硬编码** → 移除默认值，启动时未配置则报错
- [ ] **P1-2: AES/ECB加密模式不安全** → 改用AES/GCM或AES/CBC+随机IV
- [ ] **P1-3: 数据源列表返回密码字段** → password字段加@TableField(select=false)或@JsonIgnore
- [ ] **P1-4: AppSecret通过API泄露** → 返回时脱敏
- [ ] **P1-5: 导出文件路径穿越风险** → 校验transno和filePath，禁止../
- [ ] **P1-6: CORS allowedOriginPatterns("*")过宽** → 限制为具体域名
- [ ] **P1-7: SSRF风险** → HttpExecutor对URL做白名单校验，禁止内网地址
- [ ] **P1-8: DubboExecutor ReferenceConfig缓存不释放** → 数据源变更时清除缓存
- [ ] **P1-9: QueryOrchestrator线程池无优雅关闭** → 实现@PreDestroy
- [ ] **P1-10: 审批流程并发竞态** → approveAndPublish加@Transactional
- [ ] **P1-11: Druid监控页面无鉴权** → 配置login-username/login-password
- [ ] **P1-12: Redis无密码保护** → 添加password配置
- [ ] **P1-13: JWT两处默认密钥不一致** → JwtUtil和application.yml统一
- [ ] **P1-14: 登录无暴力破解防护** → 基于Redis实现失败计数+锁定

## P2 -- 中等

- [ ] **P2-1: 前端操作人/审批人硬编码"admin"** → 改用authStore.username
- [ ] **P2-2: 前端多处API调用缺try-catch** → 失败也弹成功提示
- [ ] **P2-3: 离线导出未校验transno权限** → Controller中添加校验
- [ ] **P2-4: 导出下载不校验任务归属** → 校验task.getApplyUser()
- [ ] **P2-5: 接口删除未检查发布状态** → 已发布接口禁止删除
- [ ] **P2-6: 数据源删除未检查接口关联** → 查interface_datasource表
- [ ] **P2-7: debug接口异常信息泄露SQL** → 返回通用错误信息
- [ ] **P2-8: DataQueryServiceImpl绕过缓存重新解析XML** → 统一用CacheManager
- [ ] **P2-9: CacheManager高并发重复加载** → 用computeIfAbsent
- [ ] **P2-10: validateParams不校验参数类型** → 根据ParamConfig.type校验
- [ ] **P2-11: 前端路由无角色权限控制** → 路由meta加权限定义

## P3 -- 建议

- [ ] **P3-1: SQL日志StdOutImpl生产环境应关闭** → 用Spring Profile区分
- [ ] **P3-2: FunctionRegistry用HashMap非线程安全** → 改ConcurrentHashMap
- [ ] **P3-3: 分页参数无上限限制** → 全局pageSize上限校验
- [ ] **P3-4: update接口允许修改任意字段** → 应用DTO接收参数
- [ ] **P3-5: X-Forwarded-For可伪造** → 配置可信代理
- [ ] **P3-6: 审计日志可能记录敏感信息** → 脱敏处理
- [ ] **P3-7: HelloWorld.vue残留未清理** → 删除
- [ ] **P3-8: Spring Boot 2.7已停止维护** → 规划升级到3.x
