# 使用指南 - Coverage Flow Tool v1.2.0

## 快速开始

### 前置要求
- Java 11 或更高版本
- Maven 3.6+
- 一个待分析的Java项目（可选的执行追踪文件）

### 1. 编译项目

```bash
cd coverage-flow-tool
mvn clean package
```

编译成功后会生成两个 JAR 文件：
- `target/coverage-flow-tool.jar` - 主程序（用于生成流程图）
- `tracer-agent/target/tracer-agent.jar` - Java Agent（用于动态追踪）

---

## 三种使用模式

### 模式 1️⃣ : 交互式模式（推荐新手使用）

**命令：**
```bash
java -jar target/coverage-flow-tool.jar
```

**使用流程：**

1. **选择追踪数据源**
   ```
   [Step 1/2] Select your trace file:
   
   Found 3 trace file(s):
   
   [1] C:\Users\qingdongm-v\Desktop\trace.txt
   [2] C:\Users\qingdongm-v\Desktop\trace.log
   [3] C:\Users\qingdongm-v\Desktop\trace.bin
   [0] Enter path manually
   
   Select [1-3] or 0 for manual: 1
   ```
   - 系统会自动扫描常见位置
   - 选择编号或输入自定义路径

2. **选择输出目录**
   ```
   [Step 2/2] Output directory for the flow report:
   Press ENTER to use default: C:\Users\qingdongm-v\Desktop\flow-report
   > 
   ```
   - 按 ENTER 使用默认目录
   - 或输入自定义路径

3. **自动生成报告**
   ```
   [1/3] Parsing trace data...
         Found 156 method calls.
   [2/3] Generating HTML flow report...
   [3/3] Done!
   
   ✅ Report saved to:
      C:\Users\qingdongm-v\Desktop\flow-report\index.html
   
   Open report in browser now? [Y/n]: y
   ✅ Opened in browser!
   ```

---

### 模式 2️⃣ : 直接模式（命令行传参）

**命令格式：**
```bash
java -jar coverage-flow-tool.jar <trace-file> <source-root> <output-dir>
```

**参数说明：**
- `<trace-file>` - 追踪数据文件路径（必需）
- `<source-root>` - 源代码根目录路径（可选）
- `<output-dir>` - 报告输出目录（可选，默认：flow-report）

**示例 1 - 最简单的用法：**
```bash
java -jar coverage-flow-tool.jar C:\trace\app.trace
```
输出目录默认为当前目录下的 `flow-report/`

**示例 2 - 指定源代码路径：**
```bash
java -jar coverage-flow-tool.jar C:\trace\app.trace C:\project\src\main\java C:\output\report
```

**示例 3 - Linux/Mac：**
```bash
java -jar target/coverage-flow-tool.jar /home/user/trace.txt /home/user/project/src /tmp/report
```

---

### 模式 3️⃣ : Java Agent 动态追踪（高级用法）

> 用于在程序运行时动态捕获执行路径，无需预先生成追踪文件

**步骤 1: 编译 tracer-agent**
```bash
cd tracer-agent
mvn clean package
```

**步骤 2: 使用 Agent 运行目标程序**
```bash
java -javaagent:tracer-agent/target/tracer-agent.jar \
     -jar your-application.jar
```

**步骤 3: 追踪数据会自动保存到文件，然后生成报告**
```bash
java -jar target/coverage-flow-tool.jar trace-output.txt output-dir
```

---

## 生成的报告文件

### 报告结构

```
flow-report/
├── index.html          # 📄 主页 - 执行摘要和导航
├── flow-diagram.html   # 🔄 流程图 - 类调用关系可视化
├── details.html        # 📝 详细信息 - 所有方法调用列表
├── statistics.html     # 📈 统计数据 - 性能指标
├── flow.dot            # 🎨 Graphviz 格式
└── style.css           # 🎨 样式表
```

### 各个报告说明

#### 1. **index.html** - 主页
```
📊 Coverage Flow Report

执行摘要：
- Entry Point: com.example.OrderService.processOrder()
- Total Methods: 156
- Unique Classes: 12
- Duration: 1234ms
```

#### 2. **flow-diagram.html** - 交互式流程图

显示：
- 所有被调用的类（蓝色方框）
- 类之间的调用关系（箭头）
- 每个类的调用次数

```
┌─────────────────┐
│ OrderService    │ (24 calls)
│ (Entry Point)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ PaymentService  │ (18 calls)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ PaymentDAO      │ (15 calls)
└─────────────────┘
```

#### 3. **details.html** - 详细方法调用列表

```
#  | Class              | Method       | Duration(ms) | Depth
---|--------------------|--------------|--------------|----- 
1  | OrderService       | processOrder | 150          | 0
2  | PaymentService     | validate     | 80           | 1
3  | PaymentDAO         | query        | 45           | 2
...
```

#### 4. **statistics.html** - 统计信息

```
总执行时间: 1234ms
总方法调用: 156次
涉及类数: 12个
最大调用深度: 8层

前 5 个最常被调用的类:
1. OrderService - 24 calls (350ms)
2. PaymentService - 18 calls (450ms)
3. PaymentDAO - 15 calls (200ms)
4. Logger - 10 calls (50ms)
5. Validator - 8 calls (120ms)
```

#### 5. **flow.dot** - Graphviz 格式

可以用来生成高质量的图片：

```bash
# 生成 PNG 图片
dot -Tpng flow.dot -o flow-diagram.png

# 生成 PDF
dot -Tpdf flow.dot -o flow-diagram.pdf

# 生成 SVG（可缩放矢量图）
dot -Tsvg flow.dot -o flow-diagram.svg
```

---

## 常见使用场景

### 场景 1️⃣ : 理解大型项目的代码执行流程

```bash
# 1. 用 Agent 运行程序
java -javaagent:tracer-agent/target/tracer-agent.jar \
     -Dtracer.filter=com.mycompany -jar myapp.jar

# 2. 生成报告
java -jar coverage-flow-tool.jar trace-output.txt .

# 3. 打开 index.html 在浏览器查看
```

### 场景 2️⃣ : 分析微服务间的调用关系

```bash
# 1. 在每个微服务启动时使用 Agent
java -javaagent:tracer-agent/target/tracer-agent.jar \
     -Dtracer.output=/shared/traces/service-a.trace \
     -jar service-a.jar

# 2. 合并追踪数据并生成报告
java -jar coverage-flow-tool.jar /shared/traces/service-a.trace .
```

### 场景 3️⃣ : 找出性能瓶颈

打开 `statistics.html`，查看：
- **最耗时的类** - 重点优化对象
- **调用最频繁的类** - 可能需要缓存
- **最长执行路径** - 关键路径优化

```
性能瓶颈:
❌ Database.query() - 450ms (18 calls)  ← 优化SQL查询
❌ HttpClient.request() - 380ms (12 calls)  ← 加超时控制
✅ Cache.get() - 5ms (50 calls)  ← 已优化
```

---

## 高级配置

### Agent 配置参数

```bash
# 只追踪特定包下的类
java -javaagent:tracer-agent/target/tracer-agent.jar \
     -Dtracer.filter=com.example.payment \
     -jar app.jar

# 排除某些包
java -javaagent:tracer-agent/target/tracer-agent.jar \
     -Dtracer.exclude=java.*,sun.*,org.springframework.* \
     -jar app.jar

# 限制追踪深度（避免无限递归）
java -javaagent:tracer-agent/target/tracer-agent.jar \
     -Dtracer.maxDepth=10 \
     -jar app.jar

# 指定输出文件位置
java -javaagent:tracer-agent/target/tracer-agent.jar \
     -Dtracer.output=/tmp/myapp-trace.txt \
     -jar app.jar
```

### 组合配置示例

```bash
java -javaagent:tracer-agent/target/tracer-agent.jar \
     -Dtracer.filter=com.mycompany \
     -Dtracer.exclude=java.*,javax.* \
     -Dtracer.maxDepth=15 \
     -Dtracer.output=/data/traces/app.trace \
     -jar myapplication.jar
```

---

## 故障排除

### ❌ 问题 1: "找不到追踪文件"

**解决方案：**
- 确保文件路径正确（Windows 用 `\\` 或正斜杠 `/`）
- 检查文件是否存在：`dir trace.txt` (Windows) 或 `ls trace.txt` (Linux)
- 尝试输入绝对路径而不是相对路径

```bash
# ✅ 好的做法
java -jar coverage-flow-tool.jar C:\\Users\\user\\trace.txt
java -jar coverage-flow-tool.jar /home/user/trace.txt

# ❌ 避免
java -jar coverage-flow-tool.jar .\trace.txt
```

### ❌ 问题 2: "报告无法打开"

**解决方案：**
- 检查输出目录权限
- 确保有足够的磁盘空间
- 用浏览器直接打开 `index.html`：
  ```bash
  # Windows
  start flow-report\index.html
  
  # Mac
  open flow-report/index.html
  
  # Linux
  firefox flow-report/index.html
  ```

### ❌ 问题 3: "Agent 启动失败"

**解决方案：**
- 确保 tracer-agent.jar 已编译：`mvn clean package -f tracer-agent/pom.xml`
- 检查 Agent JAR 路径是否正确
- 查看 Java 版本是否满足要求（11+）：`java -version`

### ❌ 问题 4: "报告文件过大"

**解决方案：**
- 使用 Agent 配置中的 `tracer.filter` 限制追踪范围
- 降低 `tracer.maxDepth` 值
- 运行更短的程序片段

---

## 输出示例

### 完整的运行日志

```
$ java -jar coverage-flow-tool.jar

╔══════════════════════════════════════════════╗
║   Coverage Flow Tool  v1.2.0                 ║
║   Execution Path Tracer                      ║
║   Generate Business Flow Diagrams            ║
╚══════════════════════════════════════════════╝

Welcome to Interactive Mode!
I will guide you step by step.

[Step 1/2] Select your trace file:

Found 2 trace file(s):

[1] C:\\Users\\qingdongm-v\\Desktop\\trace-2024-08-12.txt
[2] C:\\Users\\qingdongm-v\\Desktop\\trace-2024-08-11.txt
[0] Enter path manually

Select [1-2] or 0 for manual: 1

[Step 2/2] Output directory for the flow report:
Press ENTER to use default: C:\\Users\\qingdongm-v\\flow-report
> 

[1/3] Parsing trace data...
      Found 156 method calls.
[2/3] Generating HTML flow report...
[3/3] Done!

========================================
✅ Report: C:\\Users\\qingdongm-v\\flow-report\\index.html
   Open  : file://C:\\Users\\qingdongm-v\\flow-report\\index.html
========================================

Open report in browser now? [Y/n]: y
✅ Opened in browser!

Thank you for using Coverage Flow Tool! 🎉
```

---

## 更多帮助

- **查看项目结构：** 查看 [README.md](./README.md)
- **查看源代码：** 查看 `src/main/java/com/tracer/`
- **报告问题：** 在 GitHub Issues 中创建 issue

祝您使用愉快！🚀
