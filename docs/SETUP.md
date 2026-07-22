# 环境准备与依赖清单

> 面向小白,每一步都写清楚怎么装、怎么验证。  
> 请按顺序从上到下执行。

---

## 一、需要安装的软件清单

| 软件 | 版本要求 | 用途 | 验证命令 |
|---|---|---|---|
| JDK | 21+ | 编译运行 Java 项目 | `java -version` |
| Maven | 3.9+ | 项目构建工具 | `mvn -version` |
| MySQL | 8.0+ | 持久化存储 | `mysql --version` |
| Redis | 7.0+ | 中期记忆/缓存 | `redis-cli --version` |
| Node.js | 18+ (可选) | MCP Server / 前端 demo | `node -version` |
| ripgrep | 任意 | Grep 工具底层依赖 | `rg --version` |

---

## 二、逐个安装

### 2.1 安装 JDK 21

**macOS (推荐用 Homebrew):**

```bash
# 如果没装过 Homebrew,先装它(已装过跳过)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安装 JDK 21
brew install openjdk@21

# 配置环境变量(把下面两行加到 ~/.zshrc 末尾)
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

**验证:**

```bash
java -version
# 应该输出类似: openjdk version "21.0.x"
```

### 2.2 安装 Maven

```bash
brew install maven
```

**验证:**

```bash
mvn -version
# 应该输出 Maven 版本和 Java 版本
```

### 2.3 安装 MySQL 8

```bash
brew install mysql
brew services start mysql

# 首次设置 root 密码(下面的命令会引导你设置)
mysql_secure_installation
# 按提示操作: 设置密码级别选 0(低),然后设一个密码,比如 root123
```

**验证:**

```bash
mysql -u root -p
# 输入密码后进入 mysql> 提示符,输入 exit 退出
```

**创建项目数据库:**

```bash
mysql -u root -p
```

在 MySQL 里执行:

```sql
CREATE DATABASE react_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'react_agent'@'localhost' IDENTIFIED BY 'react_agent123';
GRANT ALL PRIVILEGES ON react_agent.* TO 'react_agent'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

> 记住这组账号密码,后面配置文件要用:
> - 地址: `localhost:3306`
> - 数据库: `react_agent`
> - 用户名: `react_agent`
> - 密码: `react_agent123`

### 2.4 安装 Redis

```bash
brew install redis
brew services start redis
```

**验证:**

```bash
redis-cli ping
# 应该输出: PONG
```

### 2.5 安装 ripgrep (Grep 工具依赖)

```bash
brew install ripgrep
```

**验证:**

```bash
rg --version
```

### 2.6 安装 Node.js (可选,用于 MCP Server)

```bash
brew install node
```

**验证:**

```bash
node -version
npm -version
```

---

## 三、准备模型 API

你需要一个 OpenAI 兼容的模型 API。以下任选其一:

### 选项 A: 使用 OpenAI 官方

- 注册 https://platform.openai.com
- 创建 API Key
- base-url: `https://api.openai.com/v1`
- model: `gpt-4o`

### 选项 B: 使用国内兼容服务 (如 OneAPI / 通义 / 智谱)

任意支持 OpenAI `/v1/chat/completions` 格式的服务均可,记下:

- `base-url`: 服务的 API 地址
- `api-key`: 你的密钥
- `model`: 模型名称

> 后面会在 `application.yml` 和环境变量中用到这三个值。

---

## 四、IDE 准备

推荐使用 **IntelliJ IDEA** (Community 版免费):

1. 下载: https://www.jetbrains.com/idea/download/
2. 安装后打开,选择 "Open",选择项目目录 `/Users/user/Desktop/react-agent`
3. 等待 IDE 识别 Maven 项目并自动下载依赖

如果你用 VS Code:

1. 安装 "Extension Pack for Java" 扩展
2. 打开项目目录

---

## 五、环境检查清单

全部完成后,在终端逐条验证:

```bash
java -version          # 21+
mvn -version           # 3.9+
mysql --version        # 8.0+
redis-cli ping         # PONG
rg --version           # 有输出即可
```

全部通过 → 环境就绪,可以开始按 `EXECUTION_PLAN.md` 执行了。

---

## 六、常见问题

**Q: `brew install` 很慢怎么办?**  
A: 换国内镜像:

```bash
# 设置 Homebrew 国内镜像
export HOMEBREW_BREW_GIT_REMOTE="https://mirrors.tuna.tsinghua.edu.cn/git/homebrew/brew.git"
export HOMEBREW_BOTTLE_DOMAIN="https://mirrors.tuna.tsinghua.edu.cn/homebrew-bottles"
brew update
```

**Q: Maven 下载依赖很慢怎么办?**  
A: 在 `~/.m2/settings.xml` 中配置阿里云镜像 (EXECUTION_PLAN 第一步会做)。

**Q: MySQL 连接报错怎么办?**  
A: 确认服务已启动 `brew services list | grep mysql`,确认密码正确。
