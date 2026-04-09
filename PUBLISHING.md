# 发布到 Maven 中央仓库指南

本文档介绍如何将 Dynamic Thread Pool 框架发布到 Maven 中央仓库。

## 前置准备

### 1. 注册 Sonatype 账号

访问 [central.sonatype.com](https://central.sonatype.com) 注册账号。

### 2. 验证 Namespace

在 Central Portal 中验证你的 namespace：

- **使用 GitHub**: 选择 `io.github.your-username` 作为 groupId
- **使用自有域名**: 验证 `com.dynamic.thread` 域名所有权（需要在 DNS 添加 TXT 记录）

> **重要**: 如果使用 GitHub，需要修改 pom.xml 中的 `groupId` 为 `io.github.your-username`

### 3. 生成 Central Portal Token

1. 登录 [central.sonatype.com](https://central.sonatype.com)
2. 点击右上角用户名 → "View Account"
3. 在 "User Token" 部分点击 "Generate User Token"
4. 保存生成的 username 和 password

### 4. 安装并配置 GPG

#### Windows
```powershell
# 下载安装 Gpg4win
# https://www.gpg4win.org/download.html

# 生成密钥
gpg --gen-key

# 查看密钥
gpg --list-keys

# 上传公钥到密钥服务器
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

#### Linux/Mac
```bash
# 安装 GPG
# Mac: brew install gnupg
# Ubuntu: apt install gnupg

# 生成密钥
gpg --gen-key

# 查看密钥
gpg --list-keys

# 上传公钥到密钥服务器
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

### 5. 配置 Maven settings.xml

复制 `settings.xml.template` 到 `~/.m2/settings.xml`，并填入：
- Central Portal 的 Token username 和 password
- GPG passphrase（如果需要）

```xml
<servers>
    <server>
        <id>central</id>
        <username>YOUR_TOKEN_USERNAME</username>
        <password>YOUR_TOKEN_PASSWORD</password>
    </server>
</servers>
```

## 发布前检查

### 1. 修改项目信息

在 `pom.xml` 中修改以下占位符：

```xml
<!-- 项目 URL -->
<url>https://github.com/your-username/dynamic-thread</url>

<!-- 开发者信息 -->
<developer>
    <id>your-id</id>
    <name>Your Name</name>
    <email>your-email@example.com</email>
</developer>

<!-- SCM 信息 -->
<scm>
    <connection>scm:git:git://github.com/your-username/dynamic-thread.git</connection>
    <developerConnection>scm:git:ssh://github.com:your-username/dynamic-thread.git</developerConnection>
    <url>https://github.com/your-username/dynamic-thread/tree/main</url>
</scm>
```

### 2. 确认版本号

确保版本号不是 SNAPSHOT（发布正式版时）：

```xml
<version>jdk8_springboot2.x</version>
```

### 3. 本地测试构建

```bash
# 测试完整构建流程（不实际发布）
mvn clean package -Prelease -DskipTests
```

## 执行发布

### 发布所有模块

```bash
mvn clean deploy -Prelease
```

### 仅发布 starter 模块

```bash
mvn clean deploy -Prelease -pl dynamic-thread-core,dynamic-thread-agent,dynamic-thread-spring-base,dynamic-thread-starter -am
```

### 跳过示例和 Server 模块

在 example 和 server 模块的 pom.xml 中添加：

```xml
<properties>
    <maven.deploy.skip>true</maven.deploy.skip>
</properties>
```

## 发布后验证

1. 访问 [central.sonatype.com](https://central.sonatype.com)
2. 查看 "Deployments" 或 "Published" 列表
3. 等待约 30 分钟后在 [search.maven.org](https://search.maven.org) 搜索你的包

## 常见问题

### Q: GPG 签名失败
```
A: 确保 gpg 在 PATH 中，或在 settings.xml 中配置 gpg.executable 路径
```

### Q: 401 Unauthorized
```
A: 检查 settings.xml 中的 server id 是否为 "central"，Token 是否正确
```

### Q: Namespace 未验证
```
A: 在 Central Portal 完成 namespace 验证后再发布
```

### Q: Javadoc 生成失败
```
A: 已配置 <doclint>none</doclint>，如仍失败检查代码中的 javadoc 注释格式
```

## 版本发布流程

1. 更新 `pom.xml` 中的版本号（如 `1.0.0` → `1.1.0`）
2. 更新所有子模块的版本引用
3. 提交代码并打 tag
4. 执行 `mvn clean deploy -Prelease`
5. 在 Central Portal 确认发布状态

## 相关链接

- [Central Portal](https://central.sonatype.com)
- [Maven Central 发布指南](https://central.sonatype.org/publish/)
- [GPG 签名指南](https://central.sonatype.org/publish/requirements/gpg/)
