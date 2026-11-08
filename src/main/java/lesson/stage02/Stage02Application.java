package lesson.stage02;

import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** 第二阶段入口：第一阶段手写工具参数，这一阶段把用户原话交给模型。 */
@SpringBootApplication
public class Stage02Application {
    public static void main(String[] args) {
        String key = System.getenv("AI_API_KEY");
        if (key == null) key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("请先设置 OPENAI_API_KEY（或 AI_API_KEY），详见 docs/stage02/README.md。");
            System.exit(2);
        }
        // 单次问答结束即关闭上下文，McpConfiguration 释放连接和子进程。
        try (var context = SpringApplication.run(Stage02Application.class, args)) {
            // ApplicationRunner 在 run() 返回之前完成问答。
        }
    }

    @Bean
    AtomicInteger toolCallCount() {
        return new AtomicInteger();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider tools, AtomicInteger toolCallCount) {
        // ★ McpConfiguration 已启动服务端并初始化连接，获取工具说明及 Schema。
        ToolCallback[] callbacks = tools.getToolCallbacks();
        if (callbacks.length == 0) {
            throw new IllegalStateException("MCP 服务端没有提供工具，请检查连接配置。");
        }
        for (ToolCallback callback : callbacks) {
            var definition = callback.getToolDefinition();
            System.out.println("[发现工具] " + definition.name());
            System.out.println("[参数规则] " + definition.inputSchema());
        }
        ToolCallback[] traced = Arrays.stream(callbacks)
                .map(callback -> new TracingToolCallback(callback, toolCallCount))
                .toArray(ToolCallback[]::new);
        // ★ 把服务端提供的工具交给模型；这里没有写工具名，也没有 a、b。
        return builder.defaultToolCallbacks(traced)
                .defaultSystem("你是 MCP 学习助手。涉及可由已提供工具执行的计算时，请调用工具，"
                        + "根据工具的真实返回值用中文回答。缺少必要参数就向用户询问，不要编造参数或工具结果。")
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "stage02.run", havingValue = "true", matchIfMissing = true)
    ApplicationRunner askOnce(ChatClient chatClient, AtomicInteger toolCallCount) {
        return arguments -> {
            String question = String.join(" ", arguments.getNonOptionArgs());
            if (question.isBlank()) {
                System.out.println("请输入问题，例如：请用工具计算 10 加 20");
                var input = new Scanner(System.in);
                if (!input.hasNextLine()) {
                    throw new IllegalArgumentException("没有收到问题，请通过程序参数或控制台提供一句自然语言。");
                }
                question = input.nextLine();
            }
            if (question.isBlank()) throw new IllegalArgumentException("问题不能为空。");
            System.out.println("[用户问题] " + question);
            // ★ 自然语言 + 工具 Schema → 模型生成 tool_calls → Spring AI 自动调用 MCP。
            String answer = chatClient.prompt().user(question).call().content();
            if (answer == null || answer.isBlank()) throw new IllegalStateException("模型没有返回最终文本。");
            System.out.println("[AI 回答] " + answer);
            if (toolCallCount.get() == 0) {
                System.out.println("[提示] 本次模型没有调用 MCP 工具；上面的回答不能作为工具执行证明。");
            }
        };
    }
}
