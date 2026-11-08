package demo;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** MCP 服务端：声明参数规则，再把参数交给普通 Java 业务逻辑。 */
public class    DemoServer {
    public static void main(String[] args) throws InterruptedException {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name("add")
                .description("Add two numbers")
                // 类似接口入参文档：a、b 都是必填数字。
                .inputSchema(mapper, """
                        {"type":"object","properties":{
                          "a":{"type":"number"},"b":{"type":"number"}},
                         "required":["a","b"],"additionalProperties":false}
                        """)
                .build();

        var specification = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    // ★ 收参就在这里！SDK 已把 JSON 转成 Map。
                    Map<String, Object> arguments = request.arguments();
                    try {
                        BigDecimal a = number(arguments, "a");
                        BigDecimal b = number(arguments, "b");
                        // ★ 此处可替换成你的 service、数据库查询或其他业务。
                        String sum = a.add(b).stripTrailingZeros().toPlainString();
                        // ★ 返回 MCP 结果，本例放在文本 content 中。
                        return McpSchema.CallToolResult.builder()
                                .addTextContent(sum).isError(false).build();
                    } catch (IllegalArgumentException e) {
                        return McpSchema.CallToolResult.builder()
                                .addTextContent(e.getMessage()).isError(true).build();
                    }
                }).build();

        var server = McpServer.sync(new StdioServerTransportProvider(mapper))
                .serverInfo("simple-java-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(specification)
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        // STDIO 的 stdout 是协议通道，禁止在这里 System.out.println 业务日志。
        new CountDownLatch(1).await();
    }

    private static BigDecimal number(Map<String, Object> arguments, String key) {
        if (arguments == null || !(arguments.get(key) instanceof Number value)) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return new BigDecimal(value.toString());
    }
}
