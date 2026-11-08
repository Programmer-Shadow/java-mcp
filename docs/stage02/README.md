# 阶段02：Spring AI 根据自然语言调用 MCP

所有阶段共用仓库根目录的 **一个 pom.xml、一个 src、一个 target**。

```text
pom.xml
src/
  main/
    java/
      demo/                    第一阶段，保留原代码
      lesson/stage02/          第二阶段
    resources/
      application.yml          模型连接和第二阶段 Java 启动命令
      logback.xml              日志写 stderr，保护 STDIO 协议通道
  test/java/lesson/stage02/     第二阶段集成测试
target/
  java-mcp-demo.jar             两阶段共用的程序
  lib/                         两阶段共用的运行依赖
```

## 学习顺序

1. `Stage02Application`：获取工具说明，交给 ChatClient，传入自然语言。
2. `McpConfiguration`：启动同一项目中的 DemoServer 子进程，握手、发现工具，把工具 Schema 转成 Spring AI 的 ToolDefinition。
3. `TracingToolCallback`：打印模型实际生成的工具名和 JSON 参数。
4. `McpConfiguration` 的回调：将模型参数转为 Map，通过 MCP 调用服务端，返回实际结果。

```text
用户输入一句话
 → 客户端获取 MCP 工具及 inputSchema
 → Spring AI 把用户问题和工具说明发给 OpenAI
 → 模型返回 tool_calls：工具名 + JSON 参数
 → Spring AI 执行回调，经 MCP 发送参数
 → DemoServer 计算并返回真实结果
 → Spring AI 把结果发回模型
 → 模型组织回答
```

客户端没有写死 add、a、b。仍然需要服务端实现业务及参数规则，模型只能选择已经提供的工具。

## 构建与运行

以下命令全部在仓库根目录 `F:/codex/MCP` 执行。JDK17+，本机使用 JDK21。

```powershell
mvn clean verify
```

生成根目录 `target/java-mcp-demo.jar` 和 `target/lib`，两者需放在一起，不能只复制 jar。

第一阶段（无需 API Key）：

```powershell
java -jar target/java-mcp-demo.jar
java -jar target/java-mcp-demo.jar --errors
```

第二阶段（需要 OpenAI API Key）：

```powershell
$env:OPENAI_API_KEY = '填你的 OpenAI API 密钥'
java -cp target/java-mcp-demo.jar lesson.stage02.Stage02Application '请用工具计算 10 加 20'
```

第二阶段会自动启动第一阶段的 `demo.DemoServer`，不需要第二个 jar 或单独启动服务。
不传问题时，会提示在控制台输入一句话；目前单次问答结束即退出，不保存聊天记录。

本机 Java/Maven 尚未加入 PATH 时：

```powershell
$env:JAVA_HOME = 'D:/app/Java/jdk-21.0.10'
& '.tools/apache-maven-3.9.9/bin/mvn.cmd' '-Dmaven.repo.local=F:/codex/MCP/.tools/m2' clean verify
& "$env:JAVA_HOME/bin/java.exe" -cp target/java-mcp-demo.jar lesson.stage02.Stage02Application '请用工具计算 10 加 20'
```

## IDEA 操作

只导入根目录 `pom.xml`，项目/模块 SDK 选择17+（本机21）。
第一阶段运行 `demo.DemoClient`；第二阶段运行 `lesson.stage02.Stage02Application`。
第二阶段运行配置中设置环境变量 `OPENAI_API_KEY`，程序参数填写自然语言问题。
两个阶段都可以直接从 IDEA 运行 main；无需先手动打包另一个阶段。

观察参数时在 `TracingToolCallback.call()` 下断点，`toolInput` 就是模型生成的原始 JSON。
服务端在另一个进程中，普通客户端断点不会自动进入服务端；可先看控制台打印的 MCP 结果。

## 模型配置

用户选择接入 OpenAI API。默认 `gpt-5.6-terra`，`reasoning-effort=none`、`temperature=1`；模型与账号权限需要实际验证。
从 [OpenAI 平台](https://platform.openai.com/api-keys)获取 API Key，程序不使用 ChatGPT 网页登录状态。

可选环境变量：

| 变量 | 默认值/作用 |
|---|---|
| OPENAI_API_KEY | OpenAI API 密钥 |
| AI_API_KEY | 兼容原配置；同时设置时优先使用这一项 |
| AI_MODEL | gpt-5.6-terra |
| AI_BASE_URL | https://api.openai.com |
| AI_COMPLETIONS_PATH | /v1/chat/completions |
| MCP_JAVA_COMMAND | 当前 JVM 对应的 java，可设完整 JDK 路径 |

切换模型时按能力调整 application.yml 中的推理及采样选项。
切换到兼容服务时，避免 base URL 和 completions path 重复拼接 `/v1`。
原 `MCP_SERVER_JAR` 配置已移除，改为复用当前 classpath 启动服务端。

密钥只放环境变量，不写进源码、文档或 GitHub。用户问题、工具说明和工具结果会发送到所配置的模型服务。
模型请求不自动重试，连接超时10秒，读取超时60秒；MCP 请求超时20秒。

## 为什么有一层 McpConfiguration

第一阶段使用官方 MCP SDK2.0.1，Spring AI1.1.8 的 MCP starter 依赖旧版 SDK0.18.3，两者的 Schema API 有差异。
为保持同一 pom 和第一阶段源码，根项目统一用 SDK2.0.1，第二阶段用少量 ToolCallback 代码适配 Spring AI，避免混用两套 SDK。

日志统一写 stderr：STDIO 服务端 stdout 专门传协议消息，不能混入普通业务日志。

## 验证与排错

`mvn clean verify` 包含两项“模拟模型 HTTP + 真实 MCP 子进程”集成测试：验证 Schema 发送、参数流转、实际结果回传以及最终文本。
模型响应是测试夹具，不代表真实模型推理已验证；测试无需密钥，不调用外部模型。
真实 OpenAI 的鉴权、模型权限、工具选择效果需要配置密钥后验证。

- 缺密钥：启动会给出明确提示。
- 401/403：检查模型凭据或权限。
- 404：检查模型地址、请求路径及模型ID。
- 没有工具调用日志：模型未发起调用，最终回答不能作为 MCP 执行证明。
- 找不到类/依赖：先构建根项目，保留 jar 旁边的 lib；IDEA 重新加载根 pom。
- Windows 结束时可能出现子进程 code 1 的 SDK 警告，需结合前面的实际调用结果判断。

本阶段不演示动态工具变更和缓存刷新。
参考：[OpenAI 工具调用](https://developers.openai.com/api/docs/guides/function-calling)、[Spring AI ToolCallback](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html)。
