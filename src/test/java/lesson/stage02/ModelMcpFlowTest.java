package lesson.stage02;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** 模拟的是模型 HTTP 响应；MCP 连接、工具计算、Spring AI 调用链都是真实的。 */
class ModelMcpFlowTest {
    @ParameterizedTest
    @CsvSource({"7,8,15", "1.5,2.25,3.75"})
    void forwardsSchemaAndModelArgumentsAndReturnsRealMcpResult(String a, String b, String sum) throws Exception {
        var mapper = new ObjectMapper();
        var requests = new AtomicInteger();
        var failure = new AtomicReference<Throwable>();
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/v1/chat/completions", exchange -> {
            try {
                var body = mapper.readTree(exchange.getRequestBody());
                int count = requests.incrementAndGet();
                Object message;
                if (count == 1) {
                    assertEquals("none", body.path("reasoning_effort").asText());
                    assertEquals(1.0, body.path("temperature").asDouble());
                    var definition = body.path("tools").get(0).path("function");
                    assertEquals("number", definition.path("parameters").path("properties").path("a").path("type").asText());
                    assertTrue(body.path("messages").toString().contains("请计算"));
                    // 这是测试夹具，不是真实 AI 推理。生产代码不拼工具参数。
                    message = Map.of("role", "assistant", "tool_calls", List.of(Map.of(
                            "id", "call_1", "type", "function", "function", Map.of(
                                    "name", definition.path("name").asText(),
                                    "arguments", "{\"a\":" + a + ",\"b\":" + b + "}"))));
                } else {
                    assertEquals(2, count, "应只有工具调用前、后两次模型请求");
                    boolean found = false;
                    for (var item : body.path("messages")) {
                        if ("tool".equals(item.path("role").asText())) {
                            assertEquals("call_1", item.path("tool_call_id").asText());
                            assertTrue(item.path("content").asText().contains(sum));
                            found = true;
                        }
                    }
                    assertTrue(found, "第二次模型请求必须包含真实 MCP 工具结果");
                    message = Map.of("role", "assistant", "content", "MCP result verified: " + sum);
                }
                byte[] response = mapper.writeValueAsBytes(Map.of("id", "test", "object", "chat.completion",
                        "created", 1, "model", "test-model", "choices", List.of(Map.of(
                                "index", 0, "message", message, "finish_reason", count == 1 ? "tool_calls" : "stop"))));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (Throwable error) {
                failure.set(error);
                byte[] response = "{\"error\":{\"message\":\"test fixture failed\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        http.start();
        try (var context = new SpringApplicationBuilder(Stage02Application.class).run(
                "--stage02.run=false",
                "--spring.ai.openai.api-key=test-only-not-a-real-key",
                "--spring.ai.openai.chat.options.model=test-model",
                "--spring.ai.openai.base-url=http://127.0.0.1:" + http.getAddress().getPort())) {
            String answer = context.getBean(ChatClient.class).prompt().user("请计算 " + a + " 加 " + b).call().content();
            if (failure.get() != null) throw new AssertionError("HTTP fixture assertion failed", failure.get());
            assertEquals("MCP result verified: " + sum, answer);
            assertEquals(2, requests.get());
        } finally {
            http.stop(0);
        }
        assertTrue(ProcessHandle.current().children().noneMatch(process -> process.info().arguments()
                .map(args -> java.util.Arrays.asList(args).contains("demo.DemoServer")).orElse(false)),
                "关闭上下文后不应留下 DemoServer 子进程");
    }
}
