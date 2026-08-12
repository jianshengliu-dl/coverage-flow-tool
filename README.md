# Coverage Flow Tool v1.2.0

追踪 IntelliJ IDEA 中的完整代码执行路径，从程序开始到结束，记录所有经历过的 Java 类和方法，生成以 Class 为主的业务流程图。

## 功能概述

### 核心功能
1. **执行路径追踪** - 使用 Java Agent + ASM 字节码转换，在运行时动态捕获方法调用链
2. **调用关系分析** - 分析类之间的调用关系，构建类关系图
3. **流程图生成** - 生成可视化的 HTML 和 Graphviz 业务流程图

### 支持两种使用模式
- **交互模式**：无参运行，逐步引导用户选择配置
- **直接模式**：命令行参数传入，自动生成报告

## 架构说明

### 模块结构

```
coverage-flow-tool/
├── tracer-agent/               # Java Agent 模块（运行时追踪）
│   ├── pom.xml
│   └── src/main/java/com/tracer/agent/
│       ├── TracerAgent.java               # Agent 入口
│       ├── MethodTraceTransformer.java    # 字节码转换
│       ├── TraceRecorder.java             # 追踪记录器
│       └── AgentConfig.java               # 配置类
│
├── src/main/java/com/tracer/   # 主程序模块
│   ├── Main.java                          # 程序入口
│   ├── InteractiveMode.java              # 交互模式
│   ├── model/                            # 数据模型
│   │   ├── ExecutionTrace.java           # 完整执行追踪
│   │   ├── MethodCall.java               # 单个方法调用
│   │   ├── ClassNode.java                # 类节点
│   │   ├── ClassCoverage.java            # 类覆盖数据
│   │   ├── MethodCoverage.java           # 方法覆盖数据
│   │   └── LineCoverage.java             # 行覆盖数据
│   ├── parser/                           # 数据解析
│   │   ├── TraceDataParser.java          # 追踪数据解析
│   │   ├── TraceFileReader.java          # 文件读取
│   │   └── MethodCallParser.java         # 方法调用解析
│   ├── analyzer/                         # 数据分析
│   │   ├── CallChainAnalyzer.java        # 调用链分析
│   │   ├── ClassGraphBuilder.java        # 关系图构建
│   │   └── ExecutionFlowAnalyzer.java    # 流程分析
│   └── report/                           # 报告生成
│       ├── FlowReportGenerator.java      # 报告生成器
│       ├── HtmlFlowRenderer.java         # HTML 渲染
│       └── GraphVizRenderer.java         # Graphviz 渲染
│
└── pom.xml                     # 主 Maven 配置
```

## 使用方法

### 1. 编译
```bash
mvn clean package
```

### 2. 交互模式
```bash
java -jar coverage-flow-tool.jar
```

流程：
1. 选择追踪数据源（.ic 文件或 HTML 覆盖率报告）
2. 选择源代码根目录（用于显示源代码）
3. 输入包名过滤（可选，留空包含所有）
4. 选择输出目录
5. 自动生成 HTML 流程图报告

### 3. 直接模式

#### 使用 .ic 文件
```bash
java -jar coverage-flow-tool.jar --ic /path/to/file.ic /project/src/main/java /output/dir [com.example]
```

#### 使用 HTML 覆盖率报告
```bash
java -jar coverage-flow-tool.jar --html /path/to/report/dir /project/src/main/java /output/dir [com.example]
```

## 工作原理

### 执行流程

1. **数据收集** (TraceDataParser, TraceFileReader)
   - 解析 tracer-agent 生成的追踪数据
   - 支持文本、二进制、JSON 等格式
   - 构建 ExecutionTrace 对象

2. **关系分析** (CallChainAnalyzer, ClassGraphBuilder)
   - 分析方法调用链
   - 构建类节点和边关系
   - 计算调用频次和执行时间

3. **流程分析** (ExecutionFlowAnalyzer)
   - 找出关键执行路径
   - 识别性能瓶颈
   - 生成统计数据

4. **报告生成** (FlowReportGenerator)
   - 生成 HTML 报告
   - 生成 Graphviz DOT 格式
   - 包含交互式流程图

### 输出报告

生成的报告包含：
- **index.html** - 执行摘要和导航
- **flow-diagram.html** - 交互式流程图（显示类和方法调用关系）
- **details.html** - 详细的方法调用列表
- **statistics.html** - 统计信息（频度、耗时等）
- **flow.dot** - Graphviz 格式（可转换为 PNG、PDF、SVG）
- **style.css** - 样式表

## 主要类说明

### 数据模型
- **ExecutionTrace**: 包含完整执行追踪，包括所有方法调用、执行时间等
- **MethodCall**: 单个方法调用记录（类名、方法名、时间戳、调用深度等）
- **ClassNode**: 类节点，用于构建关系图（包含调用者、被调用者、方法列表）

### 分析器
- **CallChainAnalyzer**: 从 ExecutionTrace 构建类关系图
- **ClassGraphBuilder**: 构建有向无环图（DAG），表示类调用关系
- **ExecutionFlowAnalyzer**: 分析执行流，找出关键路径和瓶颈

### 报告生成
- **FlowReportGenerator**: 协调整个报告生成过程
- **HtmlFlowRenderer**: 使用 SVG 渲染交互式流程图
- **GraphVizRenderer**: 生成 Graphviz DOT 格式，可用第三方工具转换

## 依赖

- **Java 11+**
- **ASM 9.5** - 字节码操作
- **Jackson 2.15.2** - JSON 处理（可选）

## 配置

Agent 配置通过 AgentConfig 类管理，支持：n- 过滤包名（只追踪特定包下的类）
- 排除系统类和第三方库
- 设置追踪深度限制
- 配置输出格式

## 示例输出

### 流程图示例
```
OrderService → PaymentService → BankAPI
     ↓               ↓              ↓
  Process      Validate       Transfer
     ↓               ↓              ↓
OrderDAO    PaymentDAO        Log
```

### 统计数据示例
```
总执行时间: 1234ms
总方法调用: 156次
涉及类数: 12个
最大调用深度: 8层

最常被调用的类:
1. OrderService - 24次 (350ms)
2. PaymentService - 18次 (450ms)
3. PaymentDAO - 15次 (200ms)
```

## 性能考虑

- 追踪会带来性能开销（约 5-15%）
- 大型项目建议过滤特定包名
- 生成的报告文件随方法调用数增加而增大

## 故障排除

### 问题：没有找到 .ic 文件
**解决**: 在 IntelliJ IDEA 中运行代码并选择 "Run with Coverage"，.ic 文件会在 .idea 或系统临时目录中生成

### 问题：HTML 报告打不开
**解决**: 确保有足够的磁盘空间，检查输出目录权限

### 问题：流程图太复杂看不清
**解决**: 使用包名过滤功能，或查看详细列表报告

## 许可证

MIT License

## 作者

Coverage Flow Tool Contributors
