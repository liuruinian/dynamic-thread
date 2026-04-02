# Dynamic Thread Pool

<p align="center">
  <strong>基于配置中心构建的动态可观测 Java 线程池框架</strong>
</p>

<p align="center">
  <a href="#特性">特性</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#配置说明">配置说明</a> •
  <a href="#模块说明">模块说明</a> •
  <a href="#监控告警">监控告警</a>
</p>

---

## 简介

Dynamic Thread Pool 是一个基于配置中心构建的**动态可观测** Java 线程池框架，弥补了 JDK 原生线程池参数配置不灵活的不足。

支持核心参数的**在线动态调整**、**运行时状态监控**与**阈值告警**，有效提升系统的稳定性与可运维性。

框架兼容主流配置中心如 **Nacos**、**Apollo**，实现线程池参数的热更新与统一管理。

## 特性

- **动态调参**：运行时动态调整线程池核心参数，无需重启应用
- **实时监控**：实时采集线程池运行状态，支持 Prometheus + Grafana 可视化
- **阈值告警**：队列使用率、活跃线程率超阈值自动告警，支持钉钉、企微等通知
- **配置中心**：兼容 Nacos Cloud、Apollo 等主流配置中心
- **Web 容器**：支持 Tomcat、Jetty 等 Web 容器线程池动态管理
- **可扩展队列**：提供 `ResizableCapacityLinkedBlockingQueue` 支持队列容量动态调整
- **Dashboard API**：提供 REST API 查询和管理线程池

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 3.2.x |
| Nacos Client | 2.3.x |
| Apollo Client | 2.2.x |
| Micrometer | 1.12.x |

## 快速开始

### 1. 引入依赖

**Nacos Cloud 方式：**

```xml
<dependency>
    <groupId>com.dynamic.thread</groupId>
    <artifactId>dynamic-thread-nacos-cloud-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Apollo 方式：**

```xml
<dependency>
    <groupId>com.dynamic.thread</groupId>
    <artifactId>dynamic-thread-apollo-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Web 容器适配（可选）：**

```xml
<dependency>
    <groupId>com.dynamic.thread</groupId>
    <artifactId>dynamic-thread-web-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置线程池

在 `application.yml` 中配置：

```yaml
dynamic-thread:
  enabled: true
  banner: true
  config-file-type: YAML
  
  # Nacos 配置（使用 Nacos 时配置）
  nacos:
    data-id: your-app-dynamic-thread.yaml
    group: DEFAULT_GROUP
  
  # Apollo 配置（使用 Apollo 时配置）
  apollo:
    namespace: application
  
  # Web 容器线程池配置
  web:
    core-pool-size: 20
    maximum-pool-size: 200
    keep-alive-time: 60
  
  # 通知平台配置
  notify-platforms:
    - platform: DING
      url: https://oapi.dingtalk.com/robot/send?access_token=xxx
  
  # 线程池配置
  executors:
    - thread-pool-id: order-executor
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1000
      work-queue: ResizableCapacityLinkedBlockingQueue
      rejected-handler: CallerRunsPolicy
      keep-alive-time: 60
      allow-core-thread-time-out: false
      alarm:
        enable: true
        queue-threshold: 80
        active-threshold: 80
      notify:
        receives: user1,user2
        interval: 60
```

### 3. 定义线程池 Bean

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

### 4. 使用线程池

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final DynamicThreadPoolExecutor orderExecutor;

    public void processOrder(Order order) {
        orderExecutor.execute(() -> {
            // 业务逻辑
        });
    }
}
```

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

- `AbortPolicy` - 抛出异常
- `CallerRunsPolicy` - 调用者线程执行
- `DiscardPolicy` - 静默丢弃
- `DiscardOldestPolicy` - 丢弃最旧任务

## 模块说明

```
dynamic-thread/
├── dynamic-thread-core                    # 核心模块
├── dynamic-thread-spring-base             # Spring 基础模块
├── dynamic-thread-agent                   # Agent 模块（连接 Dashboard Server）
├── dynamic-thread-server                  # Dashboard Server
├── dynamic-thread-starter/
│   ├── dynamic-thread-common-spring-boot-starter      # 公共组件（含指标采集、告警配置）
│   ├── dynamic-thread-nacos-cloud-spring-boot-starter # Nacos 集成
│   ├── dynamic-thread-apollo-spring-boot-starter      # Apollo 集成
│   └── dynamic-thread-adapter/
│       └── dynamic-thread-web-spring-boot-starter     # Web 容器适配
└── dynamic-thread-example/                # 示例项目
    ├── nacos-cloud-example
    └── apollo-example
```

## Dashboard API

启用 Dashboard 后，提供以下 REST API：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/dynamic-thread/list` | GET | 获取所有线程池列表 |
| `/dynamic-thread/states` | GET | 获取所有线程池状态 |
| `/dynamic-thread/{id}/state` | GET | 获取指定线程池状态 |
| `/dynamic-thread/{id}/config` | GET | 获取指定线程池配置 |
| `/dynamic-thread/{id}/update` | POST | 更新线程池配置 |
| `/dynamic-thread/web/state` | GET | 获取 Web 容器线程池状态 |
| `/dynamic-thread/health` | GET | 健康检查 |

**更新配置示例：**

```bash
curl -X POST http://localhost:8080/dynamic-thread/order-executor/update \
  -H "Content-Type: application/json" \
  -d '{
    "corePoolSize": 15,
    "maximumPoolSize": 30,
    "queueCapacity": 2000
  }'
```

## 监控告警

### Prometheus 指标

启用后自动暴露以下指标：

- `dynamic_thread_pool_core_pool_size` - 核心线程数
- `dynamic_thread_pool_maximum_pool_size` - 最大线程数
- `dynamic_thread_pool_pool_size` - 当前线程数
- `dynamic_thread_pool_active_count` - 活跃线程数
- `dynamic_thread_pool_queue_size` - 队列大小
- `dynamic_thread_pool_queue_capacity` - 队列容量
- `dynamic_thread_pool_queue_usage_percent` - 队列使用率
- `dynamic_thread_pool_active_percent` - 活跃线程率
- `dynamic_thread_pool_completed_task_count` - 已完成任务数
- `dynamic_thread_pool_rejected_count` - 拒绝任务数

### 告警配置

```yaml
dynamic-thread:
  executors:
    - thread-pool-id: order-executor
      alarm:
        enable: true
        queue-threshold: 80    # 队列使用率阈值 (%)
        active-threshold: 80   # 活跃线程率阈值 (%)
        interval: 60           # 告警间隔 (秒)
```

## 构建项目

```bash
# 编译
mvn clean compile

# 打包
mvn clean package -DskipTests

# 安装到本地仓库
mvn clean install -DskipTests
```

## 许可证

[Apache License 2.0](LICENSE)
