package lesson.stage02;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 统一使用 SDK 2.0.1，把 MCP 工具适配成 Spring AI 的 ToolCallback。 */
@Configuration
public class McpConfiguration {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Bean(destroyMethod = "")
    McpSyncClient mcpClient(@Value("${stage02.java-command}") String javaCommand) {
        // IDEA、测试和打包运行都复用当前 classpath，不需要另一个项目的 jar。
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var parameters = ServerParameters.builder(javaCommand)
                .args("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-Duser.language=en", "-Duser.country=US", "-cp", classpath, "demo.DemoServer")
                .build();
        var client = McpClient.sync(new StdioClientTransport(parameters, McpJsonDefaults.getMapper()))
                .requestTimeout(Duration.ofSeconds(20)).build();
        try {
            client.initialize();
            return client;
        } catch (RuntimeException error) {
            close(client, ownedServers());
            throw error;
        }
    }

    @Bean
    DisposableBean mcpShutdown(McpSyncClient client) {
        // Windows 上 SDK 的关闭可能留下进程；只追踪当前 JVM 直接启动的 DemoServer。
        List<ProcessHandle> servers = ownedServers();
        return () -> close(client, servers);
    }

    private static List<ProcessHandle> ownedServers() {
        return ProcessHandle.current().children().filter(process -> process.info().arguments()
                .map(args -> java.util.Arrays.asList(args).contains("demo.DemoServer")).orElse(false)).toList();
    }

    private static void close(McpSyncClient client, List<ProcessHandle> servers) {
        try {
            client.closeGracefully();
        } finally {
            for (ProcessHandle process : servers) {
                if (!process.isAlive()) continue;
                process.destroy();
                try {
                    process.onExit().get(2, TimeUnit.SECONDS);
                } catch (Exception error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    if (process.isAlive()) process.destroyForcibly();
                }
            }
        }
    }

    @Bean
    ToolCallbackProvider mcpTools(McpSyncClient client) {
        return () -> client.listTools().tools().stream().map(tool -> adapt(client, tool)).toArray(ToolCallback[]::new);
    }

    private static ToolCallback adapt(McpSyncClient client, McpSchema.Tool tool) {
        try {
            // 名称、描述、Schema 全部来自服务端，不写死 add 或 a/b。
            var definition = ToolDefinition.builder().name(tool.name()).description(tool.description())
                    .inputSchema(JSON.writeValueAsString(tool.inputSchema())).build();
            return new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return definition;
                }

                @Override
                public String call(String toolInput) {
                    try {
                        Map<String, Object> arguments = JSON.readValue(toolInput, new TypeReference<>() {});
                        var result = client.callTool(McpSchema.CallToolRequest.builder()
                                .name(tool.name()).arguments(arguments).build());
                        String content = JSON.writeValueAsString(result.content());
                        if (Boolean.TRUE.equals(result.isError())) {
                            throw new IllegalStateException("MCP 工具执行失败：" + content);
                        }
                        return content;
                    } catch (IOException error) {
                        throw new IllegalArgumentException("工具参数或结果 JSON 转换失败", error);
                    }
                }
            };
        } catch (IOException error) {
            throw new IllegalStateException("无法转换 MCP 工具 Schema", error);
        }
    }
}
