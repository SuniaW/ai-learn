🌐 AI 应用开发示例：Spring AI 2.x + DeepSeek + 工具调用 + Advisor 增强

统一接口 · 自由切换 · 一次编码 · 驱动全球大模型

本项目演示如何基于 Spring AI 2.x（Milestone 版本） 构建企业级 AI 应用，集成 DeepSeek 大模型，并通过 @Tool 实现工具调用，利用 Advisor 机制 实现日志记录、自我修正评估等增强能力。

✨ 核心特性

✅ 基于 Spring Boot 4.0 + Java 21（启用虚拟线程）

✅ 使用 Spring AI 2.0.0-M2 最新 Milestone 版本

✅ 接入 DeepSeek（通过 OpenAI 兼容 API 代理）

✅ 工具调用：@Tool 注解自动注册天气查询工具

✅ Advisor 增强：

SelfRefineEvaluationAdvisor：LLM 自我评估与重试
MyLoggingAdvisor：自定义请求/响应日志

✅ 支持多客户端配置（通用助手 vs 气象专家）

✅ 流式响应接口（SSE）

🛠️ 技术栈
组件   版本
Java   21

Spring Boot   4.0.0

Spring Framework   7.0

Spring AI   2.0.0-M2

DeepSeek Model   deepseek-chat

构建工具   Maven

🚀 快速启动

获取 DeepSeek API Key

前往 DeepSeek 官网 注册并获取 API Key。

配置环境变量

export OPENAI_API_KEY="your-deepseek-api-key"

💡 本项目通过 base-url: https://api.deepseek.com 将 OpenAI 客户端代理至 DeepSeek。

启动应用

./mvnw spring-boot:run

调用示例

查询天气（带工具调用）

curl "http://localhost:8080/ai/weather?city=Beijing"

流式生成（SSE）

curl "http://localhost:8080/ai/generateStream?message=讲个笑话"

📂 项目结构

src/

├── main/

│   ├── java/

│   │   └── com.wx.ai.learn/

│   │       ├── config/      # AI 客户端与 Advisor 配置

│   │       ├── tool/        # @Tool 工具类（如 WeatherTool）

│   │       ├── advisor/     # 自定义 Advisor（日志、评估等）

│   │       ├── service/     # 业务服务层

│   │       └── controller/  # REST 接口

│   └── resources/

│       └── application.yml  # 模型与 API 配置

└── pom.xml                  # Maven 依赖

⚙️ 配置说明 (application.yml)

spring:
ai:
openai:
api-key: ${OPENAI_API_KEY}
base-url: https://api.deepseek.com  # 关键：代理到 DeepSeek
chat:
options:
model: deepseek-chat
temperature: 0.7  # 必须显式配置（Spring AI 2.x 要求）

🔒 安全提示：请勿将 API Key 硬编码在代码中！

🔁 模型切换指南

得益于 Spring AI 的统一抽象，只需修改一行配置即可切换模型：

切换至 Qwen-Max（阿里云）
model: qwen-max

切换至 GLM-4（智谱）
model: glm-4

切换回 OpenAI
base-url: https://api.openai.com/v1
model: gpt-5-mini

所有工具调用与 Advisor 逻辑无需改动！

🧪 扩展建议

添加 Redis 向量存储 实现长期对话记忆
集成 RAG（检索增强生成） 连接企业知识库
使用 Micrometer + Prometheus 监控每个 Advisor 性能
部署到 GraalVM Native Image 实现毫秒级启动

📚 参考资料

Spring AI 官方文档
DeepSeek API 文档
JSpecify 空安全规范

📄 许可证

Apache License 2.0  
Copyright © 2026 Wang Xu

💡 让 AI 应用开发回归本质：专注业务，而非模型细节。  
欢迎提交 Issue 或 PR！