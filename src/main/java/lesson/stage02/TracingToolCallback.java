package lesson.stage02;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** 只负责展示调用过程；实际参数由模型生成，实际计算由 MCP 服务端执行。 */
public class TracingToolCallback implements ToolCallback {
    private final ToolCallback delegate;
    private final AtomicInteger calls;

    public TracingToolCallback(ToolCallback delegate, AtomicInteger calls) {
        this.delegate = delegate;
        this.calls = calls;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext context) {
        // ★ 在这里下断点：toolInput 是模型输出的 JSON，并非本地拼出来的 Map。
        calls.incrementAndGet();
        System.out.println("[模型选择的工具] " + getToolDefinition().name());
        System.out.println("[模型生成的参数] " + toolInput);
        String result = context == null ? delegate.call(toolInput) : delegate.call(toolInput, context);
        System.out.println("[MCP 返回结果] " + result);
        return result; // Spring AI 会把它作为 tool message 再发给模型。
    }
}
