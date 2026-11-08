# Java MCP：从传两个数字开始

这是第一课：一个 Maven 项目、两个 Java 类、一个 `add` 工具。没有大模型、API Key、Spring 容器、数据库。

## 1. 先把 MCP 理解成什么？

**MCP 是一套让应用发现和调用工具的通信协议。** 它不是某个 AI 模型，也不是一个单独的产品。

你熟悉的 Java 调用可能是 `calculatorService.add(2, 3)`。使用 MCP 后，调用跨过了进程边界：

```text
Java 客户端                       Java MCP 服务端
DemoClient                        DemoServer
   | initialize：建立连接、协商能力     |
   |-------------------------------->|
   | tools/list：你有哪些工具？         |
   |-------------------------------->|
   |<----- add 的名字、说明、参数规则 ---|
   | tools/call：add，{a:2,b:3}        |
   |-------------------------------->|
   |                        收参 → 相加 |
   |<---------- content 文本 "5" -----|
```

本项目用 **STDIO**：客户端启动服务端子进程，通过标准输入/输出通信，不监听端口。跨机器对接时，可以换成 Streamable HTTP。

Java 有两个角色：调用别人提供的 MCP，写客户端；把自己的业务开放为 MCP，写服务端。本项目两个都演示。

## 2. 怎么运行？

要求：**JDK 17+、Maven 3.9+**。Java 8 不能运行本示例。

在本目录执行，先确认 `java -version` 和 `mvn -version` 指向正确的 JDK：

```powershell
mvn clean package
java -jar target/java-mcp-demo.jar
```

只运行客户端即可，它自动启动服务端。不需要开第二个终端。

也可以 IDEA 打开 `pom.xml`，把项目 SDK、Maven runner JDK 都设为 17+，运行 `demo.DemoClient.main()`。
如果 IDEA 的缩短命令行设置导致子进程找不到类，请使用上面的 jar 命令运行。

输出包含（Schema 和 SDK 日志可能额外显示）：

```text
Connected: simple-java-server
Tool: add, inputSchema=...
Sending: {a=2, b=3}
isError: false
Result: 5
```

更换参数、观察错误：

```powershell
java -jar target/java-mcp-demo.jar 10 20
java -jar target/java-mcp-demo.jar 1.5 2.25
java -jar target/java-mcp-demo.jar --errors
```

前两次分别返回 `30` 和 `3.75`。`--errors` 先正常调用，再演示缺少 b、a 传字符串；后两次应返回 `isError: true`。

本机已有 JDK 21，但默认 `java` 指向 Java 8。可直接运行已构建的 jar：

```powershell
& 'D:/app/Java/jdk-21.0.10/bin/java.exe' -jar F:/codex/MCP/target/java-mcp-demo.jar
```

Windows 上 SDK 关闭子进程时可能输出 `MCP server process failed with code 1`，本次实测发生在结果返回后，客户端退出码为 0；本课用完即关闭子进程。若握手阶段或调用阶段失败，则需要检查真正的连接错误。

## 3. Java 怎么传参数？

打开 `src/main/java/demo/DemoClient.java`，找带 ★ 的注释。真正需要你关心的是：

```java
var result = client.callTool(McpSchema.CallToolRequest.builder()
        .name("add")                       // 调哪个工具
        .arguments(Map.of("a", 2, "b", 3)) // 参数名 → 参数值
        .build());
```

SDK 把 Java 对象编码为协议消息。线上请求大致是下面这样，`id` 由 SDK 生成：

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "add",
    "arguments": {"a": 2, "b": 3}
  }
}
```

**不是把字符串 `"帮我计算 2+3"` 直接当作工具入参。** 工具期待什么参数，由它公开的 `inputSchema` 定义；客户端按参数名传 JSON 对象。

常用映射：字符串 → String，数字 → Number，布尔值 → Boolean，对象 → Map，数组 → List。嵌套对象也可以传，本课先只用数字。

## 4. 服务端怎么接收、处理？

打开 `src/main/java/demo/DemoServer.java`，`callHandler` 就类似你熟悉的接口处理方法：

```java
Map<String, Object> arguments = request.arguments();
// 读取 a 和 b → 校验、转换成 BigDecimal → 执行业务
// 本例业务：a.add(b)
return McpSchema.CallToolResult.builder()
        .addTextContent(sum)
        .isError(false)
        .build();
```

接下来你完全可以把加法换成 `orderService.findById(orderId)`。MCP 负责通信，业务逻辑依然是普通 Java。

`inputSchema` 声明 a、b 必须是数字且必填。本例 SDK 默认会在处理器前进行 Schema 校验，处理器也保留数字检查。
不要盲目把参数强转为 `Integer`：JSON 数字可能被解码为不同的 Number 类型。本例转换为 BigDecimal 计算。

## 5. 客户端怎么处理返回值？

本例响应大致如下：

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [{"type": "text", "text": "5"}],
    "isError": false
  }
}
```

先检查 `Boolean.TRUE.equals(result.isError())`，再读取 `result.content()` 中的 `TextContent.text()`。
示例为了观察协议会同时打印错误标志和内容，正常业务调用遇到错误则停止处理。

`content` 是内容列表，协议也允许其他内容类型；本项目只返回文本。这里的 `"5"` 是字符串，不是自动反序列化后的业务 DTO。
以后要返回订单对象，可以约定结构化结果并映射为自己的 DTO。

连接失败、超时等可能表现为 SDK 异常；业务或参数错误可能表现为 `isError=true`。两者都要处理。
本课设定请求超时为 15 秒，不做自动重试，避免将来替换成写操作后重复执行。

**STDIO 服务端不要 `System.out.println` 打业务日志**，否则会污染协议通道。使用 stderr 或写入 stderr 的日志框架。客户端控制台可以正常打印。

## 6. AI 在哪里？Spring AI 在哪里？

第一课没有 AI：你自己指定工具名和参数，所以最容易调试。

接入模型后的典型流程：

```text
用户说“2 加 3 是多少”
 → 应用把工具说明交给模型
 → 模型建议调用 add，给出 {a:2,b:3}
 → 应用中的 MCP 客户端发起调用
 → MCP 服务端计算，返回 5
 → 应用把结果交给模型组织回答
```

模型通常负责建议调用哪个工具、生成参数；实际调用由宿主应用执行，服务端仍需要校验参数和权限。

建议按下面顺序学，每一步只增加一个概念：

| 阶段 | 增加什么 | 要理解什么 |
|---|---|---|
| ① 本项目 | 官方 SDK + STDIO | 工具发现、Map 传参、处理器收参、结果读取 |
| ② Spring AI | 用 Spring Bean/工具注解注册同一个 add | 自动配置如何代替手工注册；Java 方法参数如何映射为 Schema |
| ③ HTTP | 服务端独立运行，客户端通过 URL 连接 | 本地子进程与远程服务的区别 |
| ④ 模型调用 | Spring AI ChatClient 或 Java 的 LangChain4j | 自然语言如何变成工具名和参数 |
| ⑤ 真实业务 | 查询订单等 | DTO、鉴权、超时、日志及写操作幂等性 |

Spring AI 的工具注解本身不等于 MCP 服务：还需要 MCP Server 的注册和传输配置。本项目不混入第二套依赖，以免掩盖第一课重点。

## 7. 官方资料与版本

- 本项目固定使用官方 Java SDK **2.0.1**，不宣称是最新版本。
- [SDK 2.0.1 源码](https://github.com/modelcontextprotocol/java-sdk/tree/v2.0.1)
- [官方服务端说明](https://java.sdk.modelcontextprotocol.io/latest/server/)
- [官方客户端说明](https://java.sdk.modelcontextprotocol.io/latest/client/)
- 文档 latest 会更新，查 API 时请与项目固定版本区分。

项目记忆：`docs/project-memory/20261108153249-java-mcp-demo.md`。
