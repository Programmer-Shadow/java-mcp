# 共用 Maven 项目结构

## 用户确认的范围与架构变更
- 用户要求所有阶段共用根 pom.xml、src、target，第二阶段 Java 代码放 src/main/java/lesson/stage02，与 demo 分离；第一阶段源码保留。
- 取代阶段2独立 Maven 设计（原文档迁移至本目录的 20261108223938-stage02-spring-ai.md），原因是教学项目统一管理构建和运行。
- Spring Boot3.5.15/Spring AI1.1.8 的 MCP starter 使用旧 SDK0.18.3，与第一阶段 SDK2.0.1 类型签名不同。统一保留 SDK2.0.1，使用少量 ToolCallback 适配代码连接 Spring AI，避免两版本混入同一 classpath。
- 两个阶段同一 Maven 项目；root target/java-mcp-demo.jar 与 target/lib 提供统一运行环境。jar manifest 引用 lib，第一阶段默认入口仍为 DemoClient，第二阶段通过 -cp 选择 Stage02Application。
- 第二阶段 MCP 客户端启动相同 classpath 下的 demo.DemoServer 子进程，不再依赖另一个阶段打包的 jar；Bean 负责握手、工具发现、回调适配和关闭。

## 数据与接口
- 自然语言及 MCP 工具 Schema 发给 OpenAI，模型返回工具名与 JSON 入参，由 ToolCallback 调用 SDK2.0.1。
- adapter 将 JSON 入参解码为 Map，执行 callTool，检查 isError，返回真实 content。不持久化。
- API Key 只从环境变量读取；不存储、打印或上传秘密值。模型配置和隐私约束沿用阶段2记忆。
- 异常通过启动提示与工具异常报告；无自动业务重试。退出上下文时释放 MCP 子进程；重启应用可恢复。
- 运行需 JDK17+（本机21）及 Maven3.9+；外部服务仅 OpenAI API，测试替换为本地模拟 HTTP。

## 实施计划、验收和当前状态
1. 先保存本记忆，迁移阶段2源码、测试、资源与文档，合并根 pom，移除旧子项目目录。
2. 改用共享 classpath 启动服务端，并增加适配器，更新测试和运行说明。
3. 根目录 mvn clean verify；两阶段真实运行检查和第一阶段源码散列比对。模型网络仍用本地模拟，真实 OpenAI 推理不宣称通过。
4. 验证仅根 pom、src 与 target，清理旧阶段构建产物并更新记忆。
- 当前尚未实施迁移或验证。
- 已知限制：Windows 关闭子进程的 SDK 警告沿用；发布时需同时携带 jar 与 target/lib。后续按新的根项目结构扩展。

## 实施完成与验证结果
- 第二阶段源码迁至 src/main/java/lesson/stage02，测试迁至 src/test/java/lesson/stage02，配置迁至 src/main/resources；教程在 docs/stage02/README.md。
- 仅保留根 pom.xml；旧 stage02-spring-ai 目录及其旧 target 已移除，文档与记忆已迁出。
- 根 pom 使用 Boot3.5.15、Spring AI1.1.8 模型 starter、SDK2.0.1；通过 maven-jar-plugin 的 manifest classpath 与 dependency copy-dependencies 将运行依赖统一放到 target/lib。
- 新增 McpConfiguration，负责 SDK 客户端 Bean、工具定义适配、参数 JSON/Map 转换及异常标志检查。
- 发现前次测试的4个无父进程服务端占用旧 jar，已清理；第二阶段新增专属子进程清理，测试断言关闭后无 DemoServer 遗留。
- logback.xml 将两阶段日志统一发送 stderr，防止新增 Spring 日志依赖污染服务端 stdout。
- 根目录 `mvn clean verify` 成功，2项集成测试通过（0失败、0错误），包括真实 MCP 调用和子进程清理断言。
- 实测根 jar 第一阶段 --errors：2+3=5，缺参及类型错误返回 isError=true；第二阶段通过同一 jar 的 -cp 入口初始化并发现工具成功，退出码0，无真实模型请求。
- 用户现有第一阶段源码保持不变，最终 SHA256 比对通过；确认仅一个项目 pom，旧目录不存在。真实 OpenAI 鉴权和推理仍未验证。
- 下一步：IDEA 重新加载根 pom.xml，直接运行 lesson.stage02.Stage02Application，配置 OPENAI_API_KEY 后观察模型工具调用。未上传 GitHub。

## 分支统一（2026-11-08）
- 用户确认以 master 的代码为准，将其合入 main，后续使用 main 作为主要开发分支。
- 原 main 与 master 分别初始化，没有共同祖先；合并保留两边历史，业务源码、配置及教程完整采用 master c777e80916735ccc32554421b71b2793fd10f5d2 的快照，master 分支保留。
- 实施前工作区干净；本地合并暂存区已与 master 完整比对一致，随后仅追加本节记忆。
- Git 命令直连 GitHub 失败，使用已连接 GitHub 插件读取和同步远端；不强制推送、不删除分支、不处理秘密值。
- 验证结果：JDK21 与项目独立 Maven 执行离线 verify 成功；首次沙箱运行因禁止本机回环连接失败，允许回环连接后现有2项集成测试全部通过。业务代码和构建配置未改动。
- 完成结果：合并提交 31b865a7054125d6d26a07ab85d6fa277575ba31 已同步 GitHub main，双父提交保留原 main 与 master 历史；通过远端读取核实。除本记忆外所有受跟踪文件均与 master 一致，本地 main 与 origin/main 同步，工作区干净。
- 下一步：继续在 main 开发；master 仍保留，无需重复合并。真实 OpenAI 推理未在本次验证范围内。
