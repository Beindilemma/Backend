# 享迹 AI（XiangJi AI）— 后端服务

> 基于 Spring Boot 3 + AI 大模型的智能旅游行程规划后端，支持微信小程序一键登录、DeepSeek/OpenAI 大模型生成行程、高德地图路径规划与坐标解析。

[![java](https://img.shields.io/badge/JDK-17+-green.svg)](https://adoptium.net/)
[![spring-boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![mysql](https://img.shields.io/badge/MySQL-8.0+-orange.svg)](https://www.mysql.com/)
[![redis](https://img.shields.io/badge/Redis-7.0+-red.svg)](https://redis.io/)
[![license](https://img.shields.io/badge/License-MIT-blue.svg)](./LICENSE)

---

## 📖 目录

- [项目介绍](#项目介绍)
- [技术栈](#技术栈)
- [功能特性](#功能特性)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 概览](#api-概览)
- [数据库设计](#数据库设计)
- [部署指南](#部署指南)

---

## 项目介绍

享迹 AI 是一款面向自由行用户的智能旅游行程规划小程序，本仓库为其 **后端服务**。用户只需输入目的地、天数、偏好等信息，后端调用 DeepSeek（或 OpenAI 兼容）大模型自动生成完整的每日游玩路线，并结合高德地图 API 提供坐标、路径规划、耗时估算等实用信息。

### 为什么做这个项目？

- 🎯 **传统行程规划太耗时**：翻攻略 → 查路线 → 算时间 → 排顺序，动辄几小时
- 🤖 **AI 可以一键生成**：大模型天然擅长生成结构化文本，配合地图 API 即可输出可落地的行程
- 📱 **微信生态触手可及**：用户无需下载 App，微信小程序一键打开

### 谁适合参考这个项目？

- 学习 Spring Boot 3、MyBatis-Plus、JWT 等 Java Web 开发的开发者
- 对 AI 大模型 API 调用（OpenAI 兼容协议）感兴趣的同学
- 需要参考微信小程序后端登录实现的开发者
- 想了解高德地图 Web API 集成方式的同学

---

## 技术栈

| 类别            | 技术                           | 版本   | 说明                                           |
| --------------- | ------------------------------ | ------ | ---------------------------------------------- |
| **框架**        | Spring Boot                    | 3.2.0  | 主框架，提供 Web、Validation、Redis 等 starter |
| **ORM**         | MyBatis-Plus                   | 3.5.9  | 增强版 MyBatis，简化 CRUD                      |
| **数据库**      | MySQL                          | 8.0+   | 主数据库，存储用户、行程、路线节点             |
| **缓存**        | Redis                          | 7.0+   | 缓存行程详情，减轻重复查库                     |
| **认证**        | JWT（jjwt）                    | 0.12.3 | 无状态用户认证                                 |
| **微信登录**    | binarywang/weixin-java-miniapp | 4.6.0  | 微信小程序官方 SDK                             |
| **AI 大模型**   | DeepSeek / OpenAI 兼容         | —      | 行程生成引擎（HTTP 直调 Chat Completions API） |
| **地图服务**    | 高德地图 Web API               | —      | 坐标解析 + 路径规划 + 距离/耗时计算            |
| **HTTP 客户端** | Apache HttpClient 5            | —      | 调用外部 API（高德 / AI）                      |
| **JSON**        | Fastjson2                      | 2.0.43 | AI 返回 JSON 解析                              |
| **工具库**      | Hutool                         | 5.8.25 | 常用工具方法                                   |
| **简化代码**    | Lombok                         | —      | 注解自动生成 Getter/Setter/Builder             |
| **构建工具**    | Maven                          | 3.8+   | 依赖管理 + 构建打包                            |
| **语言**        | Java                           | 17     |                                                |

---

## 功能特性

- 🔐 **微信小程序一键登录**：对接 `wx.login`，自动注册/登录，返回 JWT Token
- 🤖 **AI 生成行程**：调用 DeepSeek（支持 OpenAI 兼容接口切换）生成多日游玩路线，含景点、建议到达时间、人均费用、避雷建议
- 🗺️ **高德地图集成**：路径规划（步行/驾车/公交）、经纬度解析、距离与耗时计算
- 📋 **行程管理**：创建/查看/编辑/删除/复刻行程，支持设为公开分享到社区
- 👥 **会员体系**：普通用户 / 月会员 / 季会员 / 年会员 / 终身会员，差异化行程生成额度
- 🔑 **JWT 无状态认证**：7 天有效期的 JWT Token，前后端完全分离
- ⚡ **Redis 缓存**：行程详情 VO 缓存，TTL 可配置，减轻数据库压力
- 🔄 **容错重试机制**：AI 调用失败自动重试（可配重试次数），高德 QPS 超限自动退避

---

## 项目结构

```
ai-trip-backend/
├── pom.xml                         # Maven 依赖与构建配置
├── application-example.yml         # 配置模板（去敏，可提交到 Git）
├── README.md                       # 项目说明文档
├── .gitignore                      # Git 忽略规则
├── docs/                           # 文档（API 文档、架构图等）
├── src/
│   ├── main/
│   │   ├── java/com/aitrip/
│   │   │   ├── AITripApplication.java    # 启动类
│   │   │   ├── config/                   # 配置类（JWT、跨域、Redis 等）
│   │   │   ├── controller/               # 控制层（HTTP 接口入口）
│   │   │   ├── service/                  # 业务逻辑层
│   │   │   │   └── impl/                 # 业务实现类
│   │   │   ├── mapper/                   # MyBatis Mapper 接口
│   │   │   ├── entity/                   # 数据库实体类
│   │   │   ├── dto/                      # 数据传输对象（请求/响应）
│   │   │   ├── vo/                       # 视图对象（前端展示用）
│   │   │   ├── enums/                    # 枚举类
│   │   │   ├── exception/               # 自定义异常 + 全局异常处理
│   │   │   ├── result/                   # 统一响应格式 R<T>
│   │   │   ├── prompt/                   # AI Prompt 模板
│   │   │   └── utils/                    # 工具类（JWT 工具 等）
│   │   └── resources/
│   │       ├── application.yml           # 应用配置（⚠ 在 .gitignore 中，不提交）
│   │       ├── application-local.yml     # 本地密钥配置（⚠ 不提交）
│   │       ├── init.sql                  # 数据库初始化脚本（示例）
│   │       ├── db/                       # 数据库迁移 SQL 脚本
│   │       └── mapper/                   # MyBatis XML 映射文件
│   └── test/
│       └── java/com/aitrip/             # 单元测试与集成测试
```

### 分层职责

| 层级           | 包名          | 职责                                                 |
| -------------- | ------------- | ---------------------------------------------------- |
| **Controller** | `controller/` | 接收 HTTP 请求，参数校验，调用 Service，返回统一响应 |
| **Service**    | `service/`    | 核心业务逻辑：行程生成、用户管理、AI 调用编排        |
| **Mapper**     | `mapper/`     | 数据库操作接口，对接 MyBatis-Plus                    |
| **Entity**     | `entity/`     | 数据库表对应的实体类                                 |
| **DTO**        | `dto/`        | 前端 → 后端的数据传输对象                            |
| **VO**         | `vo/`         | 后端 → 前端的数据视图对象（过滤敏感字段）            |
| **Config**     | `config/`     | Spring Bean 配置（跨域、JWT 拦截器、Redis 序列化等） |
| **Utils**      | `utils/`      | 通用工具类                                           |

---

## 快速开始

### 环境要求

| 软件  | 最低版本 | 安装指南                                                                                               |
| ----- | -------- | ------------------------------------------------------------------------------------------------------ |
| JDK   | 17+      | [Adoptium](https://adoptium.net/) 或 [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) |
| Maven | 3.8+     | [Maven 官方下载](https://maven.apache.org/download.cgi)                                                |
| MySQL | 8.0+     | [MySQL 官方下载](https://dev.mysql.com/downloads/mysql/)                                               |
| Redis | 7.0+     | [Redis 官方下载](https://redis.io/download/) 或 Windows 用 [Memurai](https://www.memurai.com/)         |

### 1. 克隆项目

```bash
git clone https://github.com/Beindilemma/ai-trip-backend.git
cd ai-trip-backend
```

### 2. 配置密钥

```bash
# 将示例配置复制为真实配置
cp application-example.yml src/main/resources/application.yml
```

然后编辑 `src/main/resources/application.yml`，将以下 `your_xxx_here` 替换为你的真实密钥：

> 具体获取方式见下方 [配置说明](#配置说明)

### 3. 初始化数据库

```bash
# 使用 MySQL 命令行导入建库建表脚本
mysql -h localhost -u root -p < src/main/resources/init.sql

# 数据库迁移脚本（如需执行增量 SQL）
# 文件位于 src/main/resources/db/ 目录下，按需执行
```

执行后会自动创建 `ai_trip` 数据库及以下表：

- `user` — 用户表
- `t_itinerary` — 行程主表
- `t_route_node` — 路线节点表
- `t_transport` — 节点间交通信息表

### 4. 启动项目

```bash
# 开发环境启动
mvn spring-boot:run

# 或者先打包再启动
mvn clean package -DskipTests
java -jar target/ai-trip-backend-1.0.0.jar
```

启动成功后访问：**http://localhost:8080/api**

### 5. 验证

```bash
# 健康检查（如有 Actuator）
curl http://localhost:8080/api/actuator/health
```

---

## 配置说明

以下是 `application.yml` 中关键配置项的说明与获取方式：

### 微信小程序

> 前往 [微信公众平台](https://mp.weixin.qq.com/) → 开发 → 开发管理 → 开发设置

```yaml
wechat:
  miniapp:
    app-id: wxXXXXXXXXXXXXXXXX # 小程序 AppID（用于 wx.login）
    app-secret: XXXXXXXXXXXXXXXXXX # 小程序 AppSecret（⚠ 切勿泄露）
```

### AI 大模型（DeepSeek / OpenAI）

> - DeepSeek：前往 [DeepSeek 开放平台](https://platform.deepseek.com/api_keys) 创建 API Key
> - OpenAI：前往 [OpenAI Platform](https://platform.openai.com/api-keys) 创建 API Key

```yaml
ai:
  llm:
    chat-url: https://api.deepseek.com/v1/chat/completions # 或 OpenAI 地址
    api-key: sk-xxxxxxxxxxxxxxxxxxxxxxxx # 你的 API Key
    model: deepseek-chat # 模型名称，支持切换
    max-retries: 3 # 失败最大重试次数
    temperature: 0.2 # 生成温度（0-1，越低越确定）
```

### 高德地图

> 前往 [高德开放平台](https://console.amap.com/dev/key/app) → 创建应用 → 添加 Key（选择「Web 服务」类型）

```yaml
amap:
  key: xxxxxxxxxxxxxxxxxxxxxxxx # 高德 Web 服务 Key
  request-interval-ms: 220 # 请求间隔（减轻 QPS 压力）
  qps-retry-max: 4 # QPS 超限额外重试次数
```

### JWT

> 生成安全的 JWT Secret（256 位以上）：
>
> - **Linux / macOS**：`openssl rand -base64 64`
> - **Windows PowerShell**：`[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))`

```yaml
jwt:
  secret: 生成的Base64密钥 # ⚠ 切勿使用示例值
  expire: 604800 # Token 有效期（秒），默认 7 天
```

### MySQL & Redis

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_trip?... # 数据库连接地址
    username: root # 数据库用户名
    password: your_password # 数据库密码
  data:
    redis:
      host: localhost # Redis 地址
      port: 6379 # Redis 端口
      password: # Redis 密码（无密码留空）
```

---

## API 概览

> 完整 API 文档详见 `docs/` 目录（待补充 Swagger/Knife4j）

### 用户模块（`/api/user`）

| 方法 | 路径              | 说明                           | 认证 |
| ---- | ----------------- | ------------------------------ | ---- |
| POST | `/api/user/login` | 微信小程序登录（`code` → JWT） | ✗    |
| GET  | `/api/user/info`  | 获取当前用户信息               | ✓    |

### 行程模块（`/api/itinerary`）

| 方法   | 路径                       | 说明             | 认证 |
| ------ | -------------------------- | ---------------- | ---- |
| POST   | `/api/itinerary`           | AI 生成新行程    | ✓    |
| GET    | `/api/itinerary/{id}`      | 查看行程详情     | ✓    |
| GET    | `/api/itinerary/my`        | 我的行程列表     | ✓    |
| PUT    | `/api/itinerary/{id}`      | 编辑行程         | ✓    |
| DELETE | `/api/itinerary/{id}`      | 删除行程         | ✓    |
| POST   | `/api/itinerary/{id}/copy` | 复刻他人公开行程 | ✓    |
| GET    | `/api/itinerary/public`    | 社区公开行程列表 | ✗    |

### 认证方式

所有需要认证的接口，请在请求头携带：

```
Authorization: Bearer <your_jwt_token>
```

---

## 数据库设计

### 核心表关系

```
┌──────────────┐       ┌──────────────────┐       ┌──────────────────┐
│    user      │       │   t_itinerary    │       │  t_route_node    │
│──────────────│       │──────────────────│       │──────────────────│
│ id (PK)      │──1:N──│ user_id (FK)     │──1:N──│ itinerary_id(FK) │
│ openid       │       │ title            │       │ day_number       │
│ nickname     │       │ city             │       │ spot_name        │
│ role         │       │ total_days       │       │ lat / lng        │
│ is_member    │       │ budget           │       │ cost             │
│ ...          │       │ route_type       │       │ note             │
└──────────────┘       │ is_public        │       └──────────────────┘
                       └──────────────────┘                │
                              │                        1:N │
                              │              ┌──────────────────┐
                              └──────────────│   t_transport    │
                                             │──────────────────│
                                             │ from_node_id(FK) │
                                             │ to_node_id(FK)   │
                                             │ mode (步行/驾车/公交)│
                                             │ distance / duration│
                                             └──────────────────┘
```

### 表说明

| 表名           | 说明       | 关键字段                                                                         |
| -------------- | ---------- | -------------------------------------------------------------------------------- |
| `user`         | 用户表     | `openid`, `is_member`, `member_type`, `member_expire_time`, `monthly_trip_limit` |
| `t_itinerary`  | 行程主表   | `user_id`, `city`, `total_days`, `budget`, `route_type`, `is_public`             |
| `t_route_node` | 路线节点表 | `day_number`, `sort_order`, `spot_name`, `lat`, `lng`, `cost`, `note`            |
| `t_transport`  | 交通信息表 | `from_node_id`, `to_node_id`, `mode`, `distance`, `duration`                     |

---

## 部署指南

### 方式一：jar 包部署

```bash
# 1. 打包（跳过测试）
mvn clean package -DskipTests

# 2. 上传到服务器
scp target/ai-trip-backend-1.0.0.jar user@your-server:/opt/aitrip/

# 3. 创建配置文件（参考 application-example.yml）
cp application-example.yml /opt/aitrip/application.yml
vim /opt/aitrip/application.yml

# 4. 启动
java -jar /opt/aitrip/ai-trip-backend-1.0.0.jar --spring.config.location=/opt/aitrip/application.yml

# 5. 后台运行（推荐使用 systemd 或 supervisor 管理）
nohup java -jar /opt/aitrip/ai-trip-backend-1.0.0.jar > /opt/aitrip/app.log 2>&1 &
```

### 方式二：Docker 部署（推荐）

```dockerfile
# Dockerfile（参考，根据需要调整）
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/ai-trip-backend-1.0.0.jar app.jar
COPY application.yml application.yml
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# 构建镜像
docker build -t ai-trip-backend:1.0.0 .

# 运行容器
docker run -d \
  --name ai-trip-backend \
  -p 8080:8080 \
  -v /opt/aitrip/application.yml:/app/application.yml \
  --restart=always \
  ai-trip-backend:1.0.0
```

### 前置依赖

确保服务器可以访问以下服务：

- 🗄️ MySQL 数据库（已初始化）
- 📦 Redis 缓存服务
- 🌐 外网（调用微信 API、DeepSeek API、高德地图 API）

---

## 安全提示

⚠️ **请勿将包含真实密钥的 `application.yml` 提交到 Git！**

本项目已配置 `.gitignore`，以下文件不会被提交：

- `application.yml` — 含真实密钥的应用配置
- `application-local.yml` — 本地密钥配置
- `*.env`, `*.env.*` — 环境变量文件
- `*.log` — 日志文件

如需向团队成员分享配置模板，请使用 `application-example.yml`（所有密钥已替换为占位符）。

---

## 许可证

本项目仅供学习交流使用。

---

## 联系方式

- Issues：[提交问题](https://github.com/Beindilemma/ai-trip-backend/issues)
- 微信小程序：享迹 AI
