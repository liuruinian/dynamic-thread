# Dynamic Thread Pool

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-blue" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.x-green" alt="Spring Boot 3.2.x">
  <img src="https://img.shields.io/badge/Vue-3.4-brightgreen" alt="Vue 3.4">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License">
</p>

<p align="center">
  <strong>基于配置中心构建的动态可观测 Java 线程池框架</strong>
</p>

<p align="center">
  <a href="#特性">特性</a> •
  <a href="#架构设计">架构设计</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#dashboard-前端">Dashboard 前端</a> •
  <a href="#rest-api">REST API</a> •
  <a href="#监控告警">监控告警</a>
</p>

---

## 简介

Dynamic Thread Pool 是一个基于配置中心构建的**动态可观测** Java 线程池框架，弥补了 JDK 原生线程池参数配置不灵活的不足。

支持核心参数的**在线动态调整**、**运行时状态监控**与**阈值告警**，有效提升系统的稳定性与可运维性。

框架提供完整的**前后端解决方案**，包含 Vue3 Dashboard 管理界面和 Spring Boot 后端服务。

## 特性

### 核心功能
- **动态调参**：运行时动态调整线程池核心参数，无需重启应用
- **实时监控**：实时采集线程池运行状态，支持 Prometheus + Grafana 可视化
- **阈值告警**：队列使用率、活跃线程率超阈值自动告警，支持钉钉通知
- **拒绝策略统计**：实时统计任务拒绝情况，支持多种拒绝策略
- **可扩展队列**：提供 `ResizableCapacityLinkedBlockingQueue` 支持队列容量动态调整
- **优雅关闭**：支持分布式环境下线程池的优雅关闭，确保任务不丢失

### 多配置源支持
- **Nacos Cloud**：基于 Nacos 配置中心的动态配置
- **Apollo**：基于 Apollo 配置中心的动态配置
- **ETCD**：基于 ETCD 的实时配置监听
- **JDBC**：基于数据库轮询的配置管理
- **本地文件**：基于文件系统的配置监听

### Web 容器支持
- **Tomcat**：支持 Tomcat 线程池动态管理
- **Jetty**：支持 Jetty 线程池动态管理
- **Undertow**：支持 Undertow 线程池动态管理

### Dashboard 前端
- **Vue 3 + Element Plus** 构建的现代化管理界面
- **多应用管理**：统一管理多个应用实例的线程池
- **实时监控**：ECharts 图表展示线程池运行状态
- **告警管理**：规则配置、历史记录、通知平台管理

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 后端开发语言 |
| Spring Boot | 3.2.x | 应用框架 |
| Vue | 3.4.x | 前端框架 |
| Element Plus | 2.6.x | UI 组件库 |
| ECharts | 5.5.x | 图表库 |
| Pinia | 2.1.x | 状态管理 |
| Vite | 5.2.x | 前端构建工具 |
| Netty | 4.1.x | 网络通信框架 |
| Sa-Token | 1.37.x | 认证授权 |
| Micrometer | 1.12.x | 指标采集 |

## 架构设计

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            Dashboard 前端 (Vue3)                         │
│  ┌─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬────────┐ │
│  │Dashboard│ThreadPool│ Monitor │  Alarm  │ Reject  │   Web   │ Server │ │
│  │         │  Detail  │         │         │  Stats  │Container│ Monitor│ │
│  └─────────┴─────────┴─────────┴─────────┴─────────┴─────────┴────────┘ │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ HTTP REST API
┌───────────────────────────────────┴─────────────────────────────────────┐
│                        Dashboard Server (Spring Boot)                    │
│  ┌────────────────┬────────────────┬────────────────┬─────────────────┐ │
│  │ ThreadPool API │   Alarm API    │  WebContainer  │  Server Metrics │ │
│  │                │                │      API       │       API       │ │
│  └────────────────┴────────────────┴────────────────┴─────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                      Netty Server (TCP 9527)                        │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ Netty TCP
┌───────────────────────────────────┴─────────────────────────────────────┐
│                          应用实例 (App Instance)                          │
│  ┌────────────────┬────────────────┬────────────────┬─────────────────┐ │
│  │  Agent Client  │ Config Listener│ Thread Pools   │  Web Container  │ │
│  │  (Netty连接)    │ (配置中心监听)  │ (动态线程池)    │   (Tomcat等)    │ │
│  └────────────────┴────────────────┴────────────────┴─────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────┴─────────────────────────────────────┐
│                             配置中心                                      │
│  ┌────────┬────────┬────────┬────────┬────────┐                         │
│  │ Nacos  │ Apollo │  ETCD  │  JDBC  │  File  │                         │
│  └────────┴────────┴────────┴────────┴────────┘                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## 模块说明

```
dynamic-thread/
├── dynamic-thread-core                    # 核心模块（线程池、队列、拒绝策略）
├── dynamic-thread-spring-base             # Spring 基础模块（自动配置、注解）
├── dynamic-thread-agent                   # Agent 模块（连接 Dashboard Server）
├── dynamic-thread-server                  # Dashboard Server（后端服务）
├── dynamic-thread-dashboard-dev           # Dashboard 前端（Vue3）
├── dynamic-thread-starter/
│   ├── dynamic-thread-common-spring-boot-starter      # 公共组件
│   ├── dynamic-thread-nacos-cloud-spring-boot-starter # Nacos 集成
│   ├── dynamic-thread-apollo-spring-boot-starter      # Apollo 集成
│   ├── dynamic-thread-etcd-spring-boot-starter        # ETCD 集成
│   ├── dynamic-thread-file-spring-boot-starter        # 本地文件集成
│   ├── dynamic-thread-jdbc-spring-boot-starter        # JDBC 数据库集成
│   └── dynamic-thread-adapter/
│       └── dynamic-thread-web-spring-boot-starter     # Web 容器适配
├── dynamic-thread-example/                # 示例项目
│   ├── nacos-cloud-example                # Nacos 示例
│   ├── apollo-example                     # Apollo 示例
│   ├── etcd-example                       # ETCD 示例
│   ├── jdbc-example                       # JDBC 示例
│   └── file-example                       # 本地文件示例
└── metrics/                               # 监控配置
    ├── prometheus/                        # Prometheus 配置
    └── grafana/                           # Grafana 仪表盘
```

## 快速开始

### 1. 启动 Dashboard Server

```bash
# 编译打包
cd dynamic-thread-server
mvn clean package -DskipTests

# 启动服务（默认端口：HTTP 8080, TCP 9527）
java -jar target/dynamic-thread-server-1.0.0.jar
```

### 2. 启动 Dashboard 前端

```bash
cd dynamic-thread-dashboard-dev

# 安装依赖
npm install

# 开发模式启动（默认端口 5173）
npm run dev

# 生产构建
npm run build
```

访问 `http://localhost:5173` 打开 Dashboard，默认账号：`admin` / `admin123`

### 3. 应用接入

**引入依赖（以 Nacos 为例）：**

```xml
<!-- 基础依赖 -->
<dependency>
    <groupId>com.dynamic.thread</groupId>
    <artifactId>dynamic-thread-nacos-cloud-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Agent 连接 Dashboard Server -->
<dependency>
    <groupId>com.dynamic.thread</groupId>
    <artifactId>dynamic-thread-agent</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Web 容器适配（可选） -->
<dependency>
    <groupId>com.dynamic.thread</groupId>
    <artifactId>dynamic-thread-web-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**应用配置：**

```yaml
spring:
  application:
    name: your-app-name

dynamic-thread:
  enabled: true
  banner: true
  config-file-type: YAML
  
  # Agent 配置（连接 Dashboard Server）
  agent:
    enabled: true
    server:
      host: 127.0.0.1
      port: 9527
    report:
      interval: 5
  
  # Nacos 配置
  nacos:
    data-id: your-app-dynamic-thread.yaml
    group: DEFAULT_GROUP
```

**定义线程池 Bean：**

```java
@Configuration
public class ThreadPoolConfig {

    @Bean
    @DynamicThreadPool("order-executor")
    public DynamicThreadPoolExecutor orderExecutor() {
        return new DynamicThreadPoolExecutor(
                "order-executor",
                10, 20, 60, TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
```

**Nacos 配置内容：**

```yaml
dynamic-thread:
  executors:
    - thread-pool-id: order-executor
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1000
      keep-alive-time: 60
      rejected-handler: CallerRunsPolicy
      alarm:
        enable: true
        queue-threshold: 80
        active-threshold: 90
```

## Dashboard 前端

### 功能页面

| 页面 | 功能描述 |
|------|----------|
| **Dashboard** | 首页仪表盘，展示线程池总览和关键指标 |
| **线程池列表** | 查看所有应用实例的线程池列表和状态 |
| **线程池详情** | 线程池详细配置和实时状态，支持在线修改参数 |
| **实时监控** | ECharts 图表展示线程池运行趋势 |
| **告警管理** | 告警规则配置、历史记录、通知平台管理 |
| **拒绝统计** | 任务拒绝情况统计和详细记录 |
| **Web 容器** | Tomcat/Jetty/Undertow 线程池管理 |
| **服务器监控** | Dashboard Server 的 CPU、内存、磁盘监控 |

### 页面截图

```
┌─────────────────────────────────────────────────────────────┐
│  Dynamic Thread Pool Dashboard                          [admin] │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │
│  │ 应用数   │ │ 实例数   │ │ 线程池数 │ │ 告警数   │           │
│  │    5    │ │   12    │ │   36    │ │    3    │           │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                    线程池状态图表                         │ │
│  │  [活跃线程] [队列使用率] [完成任务] [拒绝任务]             │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## REST API

### 线程池管理 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/thread-pools/apps` | GET | 获取所有应用列表 |
| `/api/thread-pools/apps/{appId}/instances` | GET | 获取应用的所有实例 |
| `/api/thread-pools/states` | GET | 获取所有线程池状态 |
| `/api/thread-pools/instances/{instanceId}/states` | GET | 获取实例的线程池状态 |
| `/api/thread-pools/instances/{instanceId}/config` | POST | 更新线程池配置 |
| `/api/thread-pools/health` | GET | 健康检查 |

### 告警管理 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/alarm/rules` | GET | 获取所有告警规则 |
| `/api/alarm/rules` | POST | 添加告警规则 |
| `/api/alarm/rules/{id}` | PUT | 更新告警规则 |
| `/api/alarm/rules/{id}` | DELETE | 删除告警规则 |
| `/api/alarm/rules/{id}/toggle` | POST | 启用/禁用规则 |
| `/api/alarm/history` | GET | 获取告警历史 |
| `/api/alarm/statistics` | GET | 获取告警统计 |
| `/api/alarm/platforms` | GET | 获取已启用的通知平台 |
| `/api/alarm/platforms/{platform}/configure` | POST | 配置通知平台 |
| `/api/alarm/platforms/{platform}/test` | POST | 测试通知平台 |

### Web 容器 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/web-containers/states` | GET | 获取所有 Web 容器状态 |
| `/api/web-containers/states/grouped` | GET | 获取按应用分组的状态 |
| `/api/web-containers/apps/{appId}/states` | GET | 获取指定应用的状态 |
| `/api/web-containers/instances/{instanceId}/config` | POST | 更新 Web 容器配置 |
| `/api/web-containers/summary` | GET | 获取汇总统计 |

### 拒绝统计 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/reject/statistics` | GET | 获取全局拒绝统计 |
| `/api/reject/statistics/{threadPoolId}` | GET | 获取指定线程池的拒绝统计 |
| `/api/reject/records` | GET | 获取所有拒绝记录 |
| `/api/reject/summary` | GET | 获取综合摘要 |
| `/api/reject/reset` | POST | 重置所有统计 |

### 服务器监控 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/server/metrics` | GET | 获取完整服务器指标 |
| `/api/server/cpu` | GET | 获取 CPU 信息 |
| `/api/server/memory` | GET | 获取内存信息 |
| `/api/server/disk` | GET | 获取磁盘信息 |
| `/api/server/jvm` | GET | 获取 JVM 信息 |

## 配置说明

### 线程池配置参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `thread-pool-id` | 线程池唯一标识 | - |
| `core-pool-size` | 核心线程数 | 10 |
| `maximum-pool-size` | 最大线程数 | 20 |
| `queue-capacity` | 队列容量 | 1000 |
| `work-queue` | 工作队列类型 | ResizableCapacityLinkedBlockingQueue |
| `rejected-handler` | 拒绝策略 | AbortPolicy |
| `keep-alive-time` | 空闲线程存活时间（秒） | 60 |
| `allow-core-thread-time-out` | 是否允许核心线程超时 | false |

### 工作队列类型

- `ResizableCapacityLinkedBlockingQueue` - 可动态调整容量（推荐）
- `LinkedBlockingQueue`
- `ArrayBlockingQueue`
- `SynchronousQueue`
- `PriorityBlockingQueue`

### 拒绝策略

| 策略 | 说明 |
|------|------|
| `AbortPolicy` | 抛出 RejectedExecutionException 异常 |
| `CallerRunsPolicy` | 由调用者线程执行任务 |
| `DiscardPolicy` | 静默丢弃任务 |
| `DiscardOldestPolicy` | 丢弃队列中最旧的任务 |
| `RetryBufferPolicy` | 暂存任务并尝试重新投递（自定义） |

## 监控告警

### Prometheus 指标

启用后自动暴露以下指标：

```
# 线程池核心指标
dynamic_thread_pool_core_pool_size{pool="order-executor"}
dynamic_thread_pool_maximum_pool_size{pool="order-executor"}
dynamic_thread_pool_pool_size{pool="order-executor"}
dynamic_thread_pool_active_count{pool="order-executor"}
dynamic_thread_pool_queue_size{pool="order-executor"}
dynamic_thread_pool_queue_capacity{pool="order-executor"}
dynamic_thread_pool_queue_usage_percent{pool="order-executor"}
dynamic_thread_pool_active_percent{pool="order-executor"}
dynamic_thread_pool_completed_task_count{pool="order-executor"}
dynamic_thread_pool_rejected_count{pool="order-executor"}
```

### 告警配置示例

```yaml
dynamic-thread:
  executors:
    - thread-pool-id: order-executor
      alarm:
        enable: true
        queue-threshold: 80    # 队列使用率阈值 (%)
        active-threshold: 80   # 活跃线程率阈值 (%)
      notify:
        receives: user1,user2
        interval: 60           # 告警间隔 (秒)
  
  notify-platforms:
    - platform: DING
      url: https://oapi.dingtalk.com/robot/send?access_token=xxx
```

### Grafana 仪表盘

项目提供预配置的 Grafana 仪表盘，位于 `metrics/grafana/provisioning/dashboards/`。

## Docker 部署

### 使用 Docker Compose 启动基础设施

```bash
# 启动 Nacos + Prometheus + Grafana
docker-compose up -d

# 服务端口
# - Nacos: http://localhost:8848
# - Prometheus: http://localhost:9090
# - Grafana: http://localhost:3000 (admin/admin123)
```

### 启动 Dashboard Server

```bash
# 使用环境变量配置
java -jar dynamic-thread-server-1.0.0.jar \
  --server.port=8080 \
  --dynamic-thread.server.tcp-port=9527
```

## 构建项目

```bash
# 编译所有模块
mvn clean compile

# 打包所有模块
mvn clean package -DskipTests

# 安装到本地仓库
mvn clean install -DskipTests

# 前端构建
cd dynamic-thread-dashboard-dev
npm install
npm run build
```

## 示例项目

项目提供多个示例，演示不同配置源的集成方式：

| 示例 | 端口 | 说明 |
|------|------|------|
| nacos-cloud-example | 8082 | Nacos 配置中心集成示例 |
| apollo-example | 8083 | Apollo 配置中心集成示例 |
| jdbc-example | 8084 | JDBC 数据库配置示例（H2） |
| etcd-example | 8085 | ETCD 配置中心示例 |
| file-example | 8086 | 本地文件配置示例 |

```bash
# 运行示例（以 nacos-cloud-example 为例）
cd dynamic-thread-example/nacos-cloud-example
mvn spring-boot:run
```

## 许可证

[Apache License 2.0](LICENSE)

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'Add some feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request
