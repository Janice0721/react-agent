# react-agent-tools 工具生态

> 模块路径: `react-agent-tools/`  
> 包名: `com.reactagent.tools`

## 职责

管理工具的注册、查询、审批标记和执行。提供 7 个内置工具。

## 类结构

```
com.reactagent.tools
├── ToolKit.java            # 工具箱(注册/查询/审批/执行)
├── ToolConfig.java         # Spring 配置,自动注册内置工具
└── impl/
    ├── BashTool.java       # Shell 命令执行 [需审批]
    ├── ReadTool.java       # 文件读取
    ├── WriteTool.java      # 文件写入 [需审批]
    ├── EditTool.java       # 文件编辑 [需审批]
    ├── GrepTool.java       # 内容搜索(ripgrep/grep)
    ├── GlobTool.java       # 文件名匹配
    └── ScriptTool.java     # Python/Node 脚本 [需审批]
```

## ToolKit 核心方法

| 方法 | 说明 |
|---|---|
| `register(Tool)` / `registerAll(List)` | 注册工具 |
| `unregister(name)` | 注销工具 |
| `get(name)` / `contains(name)` | 查询 |
| `listNames()` / `listAll()` | 列举 |
| `markApprovalRequired(name)` / `needsApproval(name)` | 审批管理 |
| `toFunctionSpecs()` | 转换为 OpenAI Function Calling 格式 |
| `invoke(ToolCallBlock, ToolContext)` | 执行工具(异常安全) |

## 内置工具一览

| 工具 | 参数 | 审批 | 安全措施 |
|---|---|---|---|
| bash | command, timeout | ✅ | 命令黑名单(rm -rf /, fork bomb 等), 超时强杀 |
| read | path, offset, limit | ❌ | 最多 2000 行 |
| write | path, content | ✅ | 自动创建父目录 |
| edit | path, oldString, newString | ✅ | 要求 oldString 唯一匹配 |
| grep | pattern, path, include, caseSensitive | ❌ | 优先 rg 回退 grep, 最多 500 行 |
| glob | pattern, path | ❌ | 递归搜索, 最多 500 结果 |
| script | language, code/scriptPath, timeout | ✅ | 临时文件自动清理, 超时控制 |

## 依赖
- react-agent-core
- react-agent-model
- spring-boot-starter
