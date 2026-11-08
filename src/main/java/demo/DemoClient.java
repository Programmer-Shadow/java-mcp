package demo;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** 运行这个类即可：它会启动另一个 Java 进程作为 MCP 服务端。 */
public class DemoClient {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "server".equals(args[0])) {
            DemoServer.main(new String[0]);
            return;
        }
        boolean errors = args.length == 1 && "--errors".equals(args[0]);
        if (!errors && args.length != 0 && args.length != 2) {
            throw new IllegalArgumentException("Usage: java -jar target/java-mcp-demo.jar [a b | --errors]");
        }
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // 同时支持 IDEA 运行 main，以及打包后的 java -jar 运行。
        var parameters = ServerParameters.builder(java)
                .args("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-Duser.language=en", "-Duser.country=US", "-cp", System.getProperty("java.class.path"),
                        DemoServer.class.getName()).build();
        var transport = new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
        var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build();
        try {
            var initialized = client.initialize(); // 握手，SDK 自动处理协议消息。
            System.out.println("Connected: " + initialized.serverInfo().name());
            var tools = client.listTools(); // 询问：你有哪些工具，参数规则是什么？
            tools.tools().forEach(tool -> System.out.println("Tool: " + tool.name()
                    + ", inputSchema=" + tool.inputSchema()));

            Map<String, Object> arguments = Map.of(
                    "a", new BigDecimal(args.length == 2 ? args[0] : "2"),
                    "b", new BigDecimal(args.length == 2 ? args[1] : "3"));
            System.out.println("Sending: " + arguments);
            // ★ Java 传参就在这里！工具名 + Map。
            var result = client.callTool(McpSchema.CallToolRequest.builder()
                    .name("add").arguments(arguments).build());
            print(result);
            if (Boolean.TRUE.equals(result.isError())) {
                throw new IllegalStateException("Tool returned an error");
            }
            if (errors) {
                System.out.println("Missing b:");
                var missing = client.callTool(McpSchema.CallToolRequest.builder()
                        .name("add").arguments(Map.of("a", 2)).build());
                print(missing);
                System.out.println("Wrong type:");
                var wrong = client.callTool(McpSchema.CallToolRequest.builder()
                        .name("add").arguments(Map.of("a", "hello", "b", 3)).build());
                print(wrong);
                if (!Boolean.TRUE.equals(missing.isError()) || !Boolean.TRUE.equals(wrong.isError())) {
                    throw new IllegalStateException("Expected validation errors");
                }
            }
        } finally {
            client.closeGracefully(); // 释放连接和服务端子进程。
        }
    }

    private static void print(McpSchema.CallToolResult result) {
        System.out.println("isError: " + result.isError());
        // ★ 收到响应后，先检查错误标志，再按 content 类型读取。
        for (var content : result.content()) {
            if (content instanceof McpSchema.TextContent text) {
                System.out.println("Result: " + text.text());
            }
        }
    }
}

