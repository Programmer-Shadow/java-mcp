# 阶段2实现计划

当前采用用户确认的共享项目结构：根 pom.xml、src/main/java/lesson/stage02、根 target。最初的独立子项目方案已被 docs/project-memory/20261108230049-shared-stages-layout.md 取代。

目标：独立 Spring AI 客户端，让模型读取 MCP Schema 并生成参数。
依据：docs/project-memory/20261108223938-stage02-spring-ai.md。

- [x] 添加独立 pom 与配置：Boot 3.5.15、Spring AI 1.1.8、MCP Client 与 OpenAI starter，测试依赖。
- [x] 编写集成测试：本地 HTTP 返回工具调用，真实 MCP 计算，验证第二次模型请求携带实际结果。
- [x] 编写主类与跟踪工具回调；不硬编码工具名或入参，不改第一阶段代码。
- [x] 运行 Maven verify，验证缺配置提示、打包和第一阶段源码散列。
- [x] 编写中文操作步骤、断点位置、模拟模型与真实模型的区别；更新记忆中的结果。
