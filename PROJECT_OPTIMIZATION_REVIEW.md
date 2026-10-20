# Dynamic Thread 项目深度优化审查记录

> 本文记录当前项目在安全、正确性、性能、依赖治理及生产实用性方面的审查结论，作为后续优化与验收依据。
> 文档创建不代表问题已修复；下方实施清单均为待办。

## 1. 总体结论

项目已经具备动态调参、多配置源、Agent 上报、监控告警、Web 容器适配及集群转发等主要能力。当前最需要补齐的是：

1. 安全配置真正接入通信链路。
2. 配置变更具备端到端生效确认，而非仅确认已发送。
3. 线程池、重试缓冲和队列的生命周期与并发语义完整。
4. 集群及配置监听在断连、漏消息和重启后能够恢复一致状态。
5. 依赖升级、性能优化具有可重复执行的验证机制。

建议顺序：**安全与正确性 → 依赖治理 → 性能优化 → 产品化能力**。

### 1.1 审查范围与证据边界

- 审查对象：`JDK21_SpringBoot3.2.5` 分支的当前工作树，不是某个提交的差异。
- 覆盖模块：Core、Spring Base、配置源 Starter、Web 容器适配、Agent、Server/集群、Dashboard 及构建依赖配置。
- 执行方式：源码静态审查、Maven 有效 POM 与依赖树解析、Core 现有测试、npm 官方安全审计。
- Core 测试结果：6 个测试通过，0 失败、0 错误、0 跳过。
- npm 审计结果：15 个受影响依赖节点，其中 11 个 high、4 个 moderate、0 个 critical。包含传递影响，不等于 15 个独立且已可利用的项目漏洞。
- 未执行：完整后端 SCA、渗透测试、性能压测、完整集群故障注入、全模块端到端验证。
- 审查未修改源码或配置；测试生成了常规构建产物。本次额外创建本记录文档。

### 1.2 优先级定义

| 等级 | 含义 |
| --- | --- |
| P0 | 在相应生产部署或不可信访问条件下，应阻断上线的问题 |
| P1 | 应优先修复的安全、正确性、任务可靠性及一致性问题 |
| P2 | 集成、观测、可维护性与产品能力方面的改进 |
| 待测量 | 风险结构已确认，但影响程度和收益需要压测验证 |

“已确认”主要指静态代码路径已确认，并非所有问题均完成运行复现。没有证据表明项目已经遭受入侵或发生所述性能事故。

## 2. 安全与通信入口

### SEC-01：TLS、Agent 认证配置未完整接入链路

- **优先级：P0（端口可被不可信客户端访问时）。**
- **现状：**Server Netty pipeline 未接入 `SslHandler`；注册处理未调用已创建的认证组件；集群消息分发前缺少节点身份验证。
- **影响：**开启安全配置不等于实际获得保护。HTTP 登录认证不能保护独立的 Agent/集群 TCP 端口。
- **建议：**完成 Agent/Server 双端 TLS 和认证；集群优先采用 mTLS；安全配置不完整时启动失败。
- **验收：**未认证连接不能注册、上报或操作；身份不匹配的节点不能处理控制消息；缺少证书或密钥时不能以明文方式静默启动。
- **证据：**[DashboardNettyServer.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/netty/DashboardNettyServer.java#L54-L68)、[ServerChannelHandler.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/handler/ServerChannelHandler.java#L147-L202)、[ClusterChannelHandler.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/cluster/transport/ClusterChannelHandler.java#L45-L59)。

### SEC-02：后续消息未绑定连接身份，旧连接可能删除新会话

- **优先级：P1。**
- **现状：**后续心跳、上报和注销继续信任报文中的 `instanceId`，未与 Channel 会话身份核对。同 ID 注册覆盖旧记录后，旧连接断开仍可能按 ID 删除新记录。
- **影响：**跨实例状态污染、错误注销，以及重连后新连接的注册信息消失。
- **建议：**建立“已连接 → 已认证 → 已注册 → 已关闭”状态机；后续身份取自已认证会话；通过 Channel/会话版本条件比较完成替换和注销。
- **验收：**注册前上报被拒绝；不能使用其他实例 ID；旧连接断开不能删除新会话。
- **证据：**[ServerChannelHandler.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/handler/ServerChannelHandler.java#L89-L138)、[ClientRegistry.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/registry/ClientRegistry.java#L86-L152)。

### SEC-03：连接配额计数增加与释放不配对

- **优先级：P1。**
- **现状：**成功注册才增加计数，未注册连接断开也减少计数；重复注册可能重复计数。
- **影响：**连接数可能为负，未注册连接不占配额，总连接/IP/应用限额失真。
- **建议：**TCP 接纳时原子预占总量与 IP 配额，注册时增加应用配额；通过 Channel 属性记录配额获取状态，关闭时幂等释放。
- **验收：**反复连接但不注册、重复注册、并发关闭后，所有计数保持正确且不为负。
- **证据：**[ConnectionRateLimiter.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/security/ConnectionRateLimiter.java#L78-L149)。

### SEC-04：协议解码入口缺少帧长上限

- **优先级：P1。**
- **现状：**直接采用报文声明长度，缺少负值与最大帧检查；独立的消息大小校验未接入解码入口。
- **影响：**异常长度触发解码错误，超大长度可能持续累积数据或造成大额分配。这里确认的是无界入口，而非已经发生 OOM。
- **建议：**等待 body、分配数组之前检查长度；限制条目数量与消息频率；大快照采用分页和有界传输。
- **验收：**负长度、超限长度、截断报文和持续分片输入不能造成无界累积；合法最大报文仍能正常处理。
- **证据：**[MessageCodec.java](dynamic-thread-core/src/main/java/com/dynamic/thread/core/protocol/MessageCodec.java#L64-L83)。

### SEC-05：默认凭据、通知密钥与出站边界需要收紧

- **优先级：P1。**
- **现状：**应用配置包含默认管理凭据及非空通知密钥默认值；通知测试接口接受 Webhook/SMTP 目标；登录限流直接采用代理头中的客户端 IP。
- **影响：**直接运行且未覆盖配置时，默认值可能进入实际路径。通知测试属于受登录保护的出站请求/SSRF 边界风险；不代表匿名可利用。代理不清洗头或直连时，限流身份可能被伪造。
- **建议：**移除敏感默认值，生产环境强制注入；若密钥对应真实资源则轮换并检查历史；通知采用目标允许清单与网络出站策略；仅信任指定反向代理。
- **边界：**未验证仓库通知凭据的有效性，也未向相关地址发送请求。文档不复制任何凭据值。
- **证据：**[application.yml](dynamic-thread-server/src/main/resources/application.yml#L29-L64)、[AlarmController.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/controller/AlarmController.java#L573-L638)、[AuthController.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/auth/AuthController.java#L107-L119)。

## 3. 配置更新、并发与生命周期

### REL-01：配置命令缺少端到端结果闭环

- **优先级：P1。**
- **现状：**Server 调用 `writeAndFlush()` 后即返回已发送；Agent 的 RESPONSE 没有对应处理分支；集群转发缺少贯穿全链路的关联；部分前端页面未检查业务 `success` 就提示成功。
- **影响：**无法可靠区分已受理、发送失败、应用失败、超时和真实生效。
- **建议：**统一命令 ID、逐实例 ACK、有界 pending 表和超时清理；状态分为“已受理 → 已发送 → 已应用/已失败/已超时”；应用后回读真实参数。
- **验收：**离线、写失败、Agent 校验失败、跨节点超时等场景不显示成功；重复 ACK 和超时响应不会污染其他命令。
- **证据：**[ServerChannelHandler.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/handler/ServerChannelHandler.java#L293-L317)、[Config.vue](dynamic-thread-dashboard/src/views/Config.vue#L223-L231)。

### REL-02：局部更新被默认值扩展为意外覆盖

- **优先级：P1。**
- **现状：**Agent 使用带默认值的 `ThreadPoolConfig.builder()` 解析局部变更；前端未提交的保活时间、超时开关等字段仍可能被默认值填充。
- **影响：**仅修改线程数或队列容量，也可能重置未编辑的参数。
- **建议：**分离创建与更新 DTO；更新字段默认表示“未提供”；在逐池锁内合并当前状态，形成完整目标配置。
- **验收：**单字段更新不改变其他字段；所有可配置参数具备端到端传输与校验。
- **证据：**[ThreadPoolConfig.java](dynamic-thread-core/src/main/java/com/dynamic/thread/core/config/ThreadPoolConfig.java#L28-L65)、[AgentChannelHandler.java](dynamic-thread-agent/src/main/java/com/dynamic/thread/agent/handler/AgentChannelHandler.java#L147-L170)。

### REL-03：更新缺少整体预校验，异常后可能部分生效

- **优先级：P1。**
- **现状：**逐个执行 setter，前面修改成功后，后续非法参数可能抛异常；部分合法目标配置也可能因 setter 顺序失败。
- **影响：**请求返回失败，但线程池已经变化，注册表配置与实际状态可能不一致。
- **建议：**完整目标预校验、按池串行应用；根据扩缩容和超时开关安排顺序；失败时回滚可回滚参数，并返回实际生效状态。
- **验收：**非法组合不会留下未报告的部分变化；允许核心线程超时与零保活时间的联合更新顺序正确；并发更新结果可解释。
- **证据：**[DynamicThreadPoolExecutor.java](dynamic-thread-core/src/main/java/com/dynamic/thread/core/executor/DynamicThreadPoolExecutor.java#L258-L315)、[ThreadPoolRegistry.java](dynamic-thread-core/src/main/java/com/dynamic/thread/core/registry/ThreadPoolRegistry.java#L126-L142)。

### REL-04：RetryBuffer 未纳入完整关闭和策略替换流程

- **优先级：P1。**
- **现状：**主执行器关闭后，缓冲任务可能继续被取出并交给 `CallerRunsPolicy`；该策略在 executor 已关闭时不执行任务。替换拒绝策略也未完整关闭旧调度器。
- **影响：**缓冲任务可能静默丢弃，Future 未完成且未取消，旧重试调度器残留。
- **建议：**把重试策略作为执行器受管资源，统一停止接纳与调度、处理主队列和缓冲队列；为未执行任务明确完成、失败或取消结果。
- **验收：**停机时每个已接纳任务都有可追踪结果；Future 不无限等待；策略反复替换不增加残留线程。
- **边界：**有界内存缓冲及关闭回调不是持久化，不保证进程崩溃时任务不丢失。
- **证据：**[RetryBufferPolicy.java](dynamic-thread-core/src/main/java/com/dynamic/thread/core/reject/RetryBufferPolicy.java#L212-L285)、[GracefulShutdownManager.java](dynamic-thread-core/src/main/java/com/dynamic/thread/core/shutdown/GracefulShutdownManager.java#L95-L149)。

### REL-05：可变队列缩容后批量移除可能遗漏唤醒

- **优先级：P1。**
- **触发示例：**容量和元素数均为 10，生产者阻塞于 `put()`；缩容到 5 后执行 `clear()` 或批量 `drainTo()`。唤醒条件仍比较旧元素数与当前容量，`10 != 5` 时可能不发出信号。
- **影响：**队列已可写，阻塞生产者仍等待。
- **建议：**按照“从满或超容量状态跨越到可写状态”判断唤醒，协调容量修改与队列操作锁协议。
- **验收：**覆盖缩容、清空、批量移除、阻塞生产者组合及并发回归。
- **证据：**[VariableLinkedBlockingQueue.java](dynamic-thread-core/src/main/java/com/dynamic/thread/core/queue/VariableLinkedBlockingQueue.java#L509-L561)。

### REL-06：Web 容器适配器可能吞掉失败并返回成功

- **优先级：P1。**
- **现状：**Jetty 更新异常后尝试回滚但不向上抛出；Undertow 反射修改失败未完整传播；不支持的执行器路径存在静默返回。
- **影响：**即使 Server 正确处理 ACK，ACK 本身也可能不代表真实应用成功。
- **建议：**统一返回结构化结果，区分已应用、不支持、失败、部分应用；失败向上传播，更新后回读真实参数。
- **验收：**针对 Tomcat、Jetty、Undertow 及不支持路径执行容器版本兼容测试。
- **证据：**[JettyThreadPoolAdapter.java](dynamic-thread-starter/dynamic-thread-adapter/dynamic-thread-web-spring-boot-starter/src/main/java/com/dynamic/thread/starter/adapter/web/JettyThreadPoolAdapter.java#L87-L117)、[UndertowThreadPoolAdapter.java](dynamic-thread-starter/dynamic-thread-adapter/dynamic-thread-web-spring-boot-starter/src/main/java/com/dynamic/thread/starter/adapter/web/UndertowThreadPoolAdapter.java#L112-L129)。

## 4. 集群与配置源恢复

### DIST-01：集群全量同步不能完整修复遗漏删除

- **优先级：P1。**
- **现状：**全量快照只应用已有条目，没有清除快照中已不存在的实例；部分状态数据未完整应用；节点 DOWN 后远端实例可能继续显示在线。
- **影响：**遗漏 UNREGISTER 后产生幽灵实例、陈旧监控和错误路由；周期全量同步不能完全兜底。
- **建议：**按来源节点、会话代际和快照版本完成新增/更新/删除对账；在线状态由租约决定；防止旧增量覆盖新快照。
- **验收：**丢弃注销消息、节点退出、断链重连后，各节点实例列表和在线状态最终收敛。
- **证据：**[DataSyncer.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/cluster/sync/DataSyncer.java#L369-L382)、[ClientRegistry.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/registry/ClientRegistry.java#L243-L258)。

### DIST-02：etcd GET 与 Watch 缺少 revision 衔接

- **优先级：P2。**
- **现状：**先读取配置，再建立不指定 revision 的 Watch；错误恢复缺少已处理 revision 续订或快照补偿。
- **影响：**初始读取和监听之间、断线期间的最后一次更新可能遗漏，应用长期保持旧配置。
- **建议：**保存读取与已应用 revision，从下一 revision 开始监听；遇到压缩重新获取一致快照；统一 GET/Watch 的精确键或前缀范围。
- **验收：**覆盖 GET/Watch 间更新、监听断线、revision 压缩和恢复后无新事件的场景。
- **证据：**[EtcdConfigChangeListener.java](dynamic-thread-starter/dynamic-thread-etcd-spring-boot-starter/src/main/java/com/dynamic/thread/starter/etcd/listener/EtcdConfigChangeListener.java#L109-L151)。

## 5. 性能与容量优化

以下为已确认的结构性风险，尚未量化业务影响，不提供未经压测的收益百分比。

| 编号 | 当前结构与风险 | 优化方向 | 验证指标 |
| --- | --- | --- | --- |
| PERF-01 | 业务任务执行前采样、检查告警，命中通知时存在同步 HTTP 路径 | 热路径只记录轻量计数，定时采样，通知异步隔离 | 吞吐、p99、分配率、锁竞争、慢通知影响 |
| PERF-02 | Server 告警任务使用无界队列，慢通知可造成积压 | 有界队列、按实例合并最新状态、通知超时与独立并发限制 | 队列长度、最老任务年龄、堆占用 |
| PERF-03 | 网络发送主要检查 active，缺少应用层在途配额 | 写水位、可写性检查、监控合并与控制命令可靠确认 | outbound bytes、直接内存、命令延迟 |
| PERF-04 | 多个 Gauge 重复采完整快照 | 同周期共享采样快照 | 高池数采样耗时、CPU 与对象分配 |
| PERF-05 | 拒绝记录使用 CopyOnWriteArrayList | 有界环形缓冲、记录采样与日志限流 | 高拒绝率下复制成本、GC、日志吞吐 |

证据：[执行器热路径](dynamic-thread-core/src/main/java/com/dynamic/thread/core/executor/DynamicThreadPoolExecutor.java#L462-L480)、[Server 告警处理](dynamic-thread-server/src/main/java/com/dynamic/thread/server/handler/ServerChannelHandler.java#L229-L234)、[Agent 发送](dynamic-thread-agent/src/main/java/com/dynamic/thread/agent/client/AgentNettyClient.java#L179-L184)、[指标采集](dynamic-thread-starter/dynamic-thread-common-spring-boot-starter/src/main/java/com/dynamic/thread/starter/common/metrics/ThreadPoolMetricsCollector.java#L119-L125)、[拒绝记录](dynamic-thread-core/src/main/java/com/dynamic/thread/core/reject/RejectedExecutionHandlerProxy.java#L144-L152)。

建议建立四组基线：关闭/开启观测对比、持续饱和与大量拒绝、通知长期慢响应、Agent 大规模重连与集群断链恢复。监控状态允许合并，控制命令不能因背压策略静默丢失。

## 6. 依赖与构建治理

### 6.1 后端版本现状

| 组件 | 当前配置或解析结果 | 建议 |
| --- | --- | --- |
| Spring Boot | 3.2.5；3.2 系列已结束开源支持 | 制定受支持版本迁移路线，而非停在旧分支末版 |
| Spring Cloud / Alibaba | 2023.0.1 / 2023.0.1.0 | 按官方兼容矩阵与 Boot 成组迁移 |
| Netty | netty-all 4.1.108.Final，Core 实际子模块 4.1.109.Final | 统一 BOM，核验有效 POM 与完整依赖树 |
| Micrometer | 1.12.5 | 随兼容 Boot 版本升级，回归指标与 Prometheus 输出 |
| Nacos / Apollo / jetcd | 2.3.2 / 2.2.0 / 0.7.7 | 验证服务端兼容、认证、重连与监听恢复 |
| Sa-Token / Hutool / Guava | 1.37.0 / 5.8.27 / 33.1.0-jre | 纳入持续 SCA，不以版本旧直接判定可利用 |
| OSHI / Angus Mail | 6.4.0 / 2.0.3 | 纳入有效依赖清单与兼容升级评估 |

依据：[根 POM](pom.xml#L59-L131)、[Core POM](dynamic-thread-core/pom.xml)、[Server POM](dynamic-thread-server/pom.xml)及 Maven 实际解析。

本次确认的 Netty 解析结果：

```text
netty-all       4.1.108.Final
netty-handler   4.1.109.Final
netty-codec     4.1.109.Final
netty-buffer    4.1.109.Final
```

聚合包与子模块版本不同不直接证明运行错误，但说明修改单个版本属性不足以控制整组依赖。引入 BOM 的方式和管理优先级必须一起检查。

实际 Netty 版本处于 CVE-2025-24970 的影响范围；该公告涉及特定 SslHandler/native SSL 使用条件，不能据此认定当前自定义 TCP 链路可直接利用。公告修复版本也不是所有后续漏洞的安全终点。

建议短期统一 BOM、修补高风险依赖并生成 SBOM；中期评估受支持的 Boot 4.x 和匹配的 Cloud/Alibaba 组合。具体补丁版本、支持期限及容器兼容性须在实施时重新确认，不能默认 Boot 3.5 仍处于社区支持期。

### 6.2 前端以锁文件为准

| 依赖 | package-lock.json 实际版本 |
| --- | --- |
| Vue | 3.5.31 |
| Axios | 1.14.0 |
| Element Plus | 2.13.6 |
| ECharts | 5.6.0 |
| Pinia | 2.3.1 |
| Vite | 5.4.21 |
| esbuild | 0.21.5 |

`package.json` 中的版本范围不是实际安装版本。依据：[package.json](dynamic-thread-dashboard/package.json)、[package-lock.json](dynamic-thread-dashboard/package-lock.json)。

安全审计结果及解释：

- 官方 npm 审计返回 15 个受影响依赖节点：11 high、4 moderate、0 critical。
- 当前 npm 镜像不提供审计接口；本次仅在命令中临时指定官方 registry，没有修改 registry 配置。
- Vite 5 不在查阅到的当前支持范围，应同步规划 Vite 和 Vue 插件迁移。
- Vite 开发服务器问题不等于静态部署后的后端漏洞；Vue server-renderer 命中不等于当前 SPA 有相同 SSR 利用路径；Axios Node 代理相关公告不能直接套用浏览器请求。
- 先更新同大版本补丁，再独立验证跨大版本迁移；不建议直接执行强制自动修复。
- 审计数字是本次结果快照，依赖与公告数据库变化后需要重新运行。

### 6.3 构建与依赖边界

1. 根 POM、IDE 和 Maven 使用统一 Java Toolchain。本次 IDE 上下文显示 Java 17，Maven 实际使用 Java 21.0.10，项目目标为 Java 21。
2. Core 直接引入 netty-all、邮件实现及 Hutool 全量包，基础线程池使用方也承担传递依赖。逐步拆分通信与通知实现，核心保留执行器、队列、配置模型及必要 SPI。
3. Web starter 避免强制引入用户不需要的容器；当前 spring-boot-starter-web 为非 optional 依赖。
4. Surefire/Failsafe、Enforcer 和覆盖率检查统一在根 POM 管理，并增加持续 SCA/SBOM。
5. Docker 构建宜进入独立 profile；当前 Server 的 package 阶段默认绑定镜像构建。
6. 前端先输出到构建目录，再复制到打包资源；当前输出目录直接指向后端源码资源目录且启用 emptyOutDir。

证据：[Core POM](dynamic-thread-core/pom.xml#L33-L65)、[Web Starter POM](dynamic-thread-starter/dynamic-thread-adapter/dynamic-thread-web-spring-boot-starter/pom.xml#L33-L64)、[Server 构建配置](dynamic-thread-server/pom.xml#L109-L139)、[Vite 构建配置](dynamic-thread-dashboard/vite.config.js#L28-L32)。

## 7. 实用性与产品闭环

### PROD-01：应用、实例、线程池身份应贯穿观测链路

- **优先级：P1/P2。**
- 告警规则/限流缺少应用和实例维度，同名池可能互相抑制通知，记录难以定位故障实例。
- 前端历史按池名保存，多副本同名池会混成一条序列，一次时间戳对应多个值，导致时间轴错位。
- 统一使用 `appId + instanceId + threadPoolId`；应用级聚合必须显式定义。
- **验收：**两个应用或两个副本使用相同池名时，告警与历史互不污染；缺失采样显式填空。
- **证据：**[AlarmManager.java](dynamic-thread-core/src/main/java/com/dynamic/thread/core/alarm/AlarmManager.java#L266-L297)、[threadPool.js](dynamic-thread-dashboard/src/stores/threadPool.js#L95-L121)。

### PROD-02：区分临时调参与持久配置发布

Dashboard 直推当前属于运行时变更，不等于写回 Nacos/etcd/JDBC。重启或配置源再次推送后可能被覆盖。

建议明确展示临时调整、持久发布、期望配置、实际配置，以及配置版本、操作者、变更原因、逐实例结果和回滚记录。选择唯一权威配置源，定义冲突和覆盖规则。

### PROD-03：明确历史、通知配置和授权的能力边界

- 浏览器中的少量历史点不是长期监控；已有 Prometheus/Grafana 不代表 Dashboard 已接入历史查询。
- 通知平台修改需补齐重启恢复与集群同步；不能将当前节点内存更新解释为持久一致配置。
- 当前 HTTP 已有登录保护，但属于单管理员模型。面向多团队前，应补应用范围授权、审计和批量变更预览，不能只增加前端筛选。
- 按池名广播跨应用查找，在单管理员模型下不直接判定为越权；多团队开放时需增加明确资源范围。

证据：[SaTokenConfig.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/auth/SaTokenConfig.java#L18-L25)、[ThreadPoolController.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/controller/ThreadPoolController.java#L186-L217)、[AlarmController.java](dynamic-thread-server/src/main/java/com/dynamic/thread/server/controller/AlarmController.java#L469-L561)。

## 8. 测试与验证记录

### 8.1 已执行

```powershell
mvn -version
mvn -B -f E:/dynamic-thread/pom.xml -pl dynamic-thread-core -am test -Ddocker.skip=true
mvn -B -o -f E:/dynamic-thread/pom.xml -pl dynamic-thread-core -am dependency:tree "-Dincludes=io.netty:*,org.slf4j:*,org.junit.jupiter:*"
npm --prefix E:/dynamic-thread/dynamic-thread-dashboard audit --package-lock-only --ignore-scripts --registry=https://registry.npmjs.org --json
```

- Maven 3.8.2，Java 21.0.10。
- 初始离线依赖解析因本地缓存不完整失败；联网补齐依赖后，Core 测试和所述 Core 依赖树解析成功。
- Core：6 tests，0 failures，0 errors，0 skipped。
- npm：15 个受影响依赖节点；含开发与传递依赖，需进一步做使用路径分类。
- 未执行会清空后端静态源码资源目录的前端构建。

### 8.2 当前测试缺口

审查时仅发现一个 Java 测试类，未发现前端测试脚本或仓库内 GitHub Actions。现有关闭测试主要断言策略运行状态，没有验证缓冲任务全部完成或取消。

证据：[RetryBufferPolicyTest.java](dynamic-thread-core/src/test/java/com/dynamic/thread/core/reject/RetryBufferPolicyTest.java#L149-L172)、[前端 scripts](dynamic-thread-dashboard/package.json#L6-L9)。

建议补充：

- 协议：长度边界、截断、非法类型、注册前消息、身份冒用与重复注册。
- 配置：局部更新、非法组合、并发修改、异常后实际状态、重复命令和超时 ACK。
- 并发：队列缩容与批量移除、重试与停机竞争、策略切换及 Future 终结。
- 集群：消息遗漏、旧快照、节点退出、断链恢复和幽灵实例清理。
- 配置源：etcd revision、重连补偿及服务端兼容。
- 前端：失败结果不提示成功、多实例历史隔离、告警维度及配置回读。
- 性能：使用 JMH/JFR 等建立可重复基线，验收目标由基线和容量需求确定。

## 9. 分阶段实施清单

### 第一阶段：安全入口与基础防护

- [ ] SEC-01：接通 TLS、Agent 认证及集群身份校验。
- [ ] SEC-02：连接身份绑定与会话条件清理。
- [ ] SEC-03：连接配额原子预占、幂等释放。
- [ ] SEC-04：解码入口帧长及消息边界限制。
- [ ] SEC-05：移除敏感默认值，明确代理信任与出站策略。
- [ ] 补齐未认证连接、异常报文及并发连接生命周期测试。

### 第二阶段：配置与任务正确性

- [ ] REL-01：全链路命令 ID、ACK、超时及真实前端反馈。
- [ ] REL-02/03：更新 DTO、完整预校验与逐池串行应用。
- [ ] REL-04：RetryBuffer 停机、替换及任务结果终结。
- [ ] REL-05：缩容后批量移除唤醒修复与并发回归。
- [ ] REL-06：容器适配结果传播和参数回读。

### 第三阶段：故障恢复与实例隔离

- [ ] DIST-01：集群快照完整对账、会话代际及租约失效。
- [ ] DIST-02：etcd revision 续订与快照补偿。
- [ ] PROD-01：告警与历史统一复合标识。
- [ ] 验证断网、漏消息、节点重启和重复事件后的收敛。

### 第四阶段：持续治理与产品化

- [ ] 统一 BOM、生成 SBOM、建立持续依赖审计。
- [ ] 锁定兼容升级组合并覆盖各容器、配置源与协议回归。
- [ ] PERF-01～05：建立基线后实施采样、背压及存储优化。
- [ ] PROD-02/03：持久发布、审计回滚、历史查询和应用级授权。
- [ ] 统一 Toolchain、测试插件及构建产物目录，解耦默认镜像构建。

## 10. 已有保护与保留项

项目并非缺少全部保护，优化时应保留并完善：

- HTTP 登录保护及登录失败限制。
- Agent 心跳、读空闲超时、重连及多地址切换。
- 已接入的注册 ID 格式、线程池数量校验。
- 队列锁、条件变量和原子计数机制。
- RetryBuffer 的容量、批次、次数和时间限制设计。
- 主工作队列关闭等待、超时强制关闭和未完成任务回调。
- 告警规则启停、限流及历史条数限制。
- Server 指标的应用/实例/线程池标签和覆盖更新机制。

不能把局部缺陷泛化为“完全没有认证”“整个队列不线程安全”或“所有历史永久无界增长”。

## 11. 外部参考与使用限制

1. [Spring Boot 3.2.12 发布说明](https://spring.io/blog/2024/11/21/spring-boot-3-2-12-available-now)：3.2 系列开源支持结束信息。
2. [Spring Cloud Supported Versions](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions)：发布列车与 Boot 版本兼容关系，实施前重新确认。
3. [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba)：对应分支、版本与使用说明。
4. [Spring Boot 生命周期汇总](https://endoflife.date/spring-boot)：第三方生命周期信息，重要升级决策需结合官方支持政策核验。
5. [Netty CVE-2025-24970 公告](https://github.com/netty/netty/security/advisories/GHSA-4g8c-wm8x-jfhw)：受影响范围及 native SSL 触发条件。
6. [Vite 支持版本](https://vite.dev/releases)：维护与安全补丁支持范围。
7. [Vite Windows 路径相关公告](https://github.com/advisories/GHSA-fx2h-pf6j-xcff)：本次 npm 审计命中之一；涉及开发服务器场景。
8. [Vue server-renderer 公告](https://github.com/advisories/GHSA-g2v6-rqmx-r4w6)：本次 npm 审计命中之一；需区分 SSR 与浏览器 SPA。

外部版本、支持期与审计结论会变化，本文不构成“升级到某一版本即可永久安全”的承诺。源码链接对应审查时的工作树，后续修改后行号也可能变化。
