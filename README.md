# SVN Deploy Plugin

> IntelliJ IDEA 插件 —— 图形化 SVN 增量部署工具，按版本号或本地修改获取变更文件，勾选后一键生成部署脚本。

## 功能特性

- **两种获取方式**：本地修改（`svn st`）或 SVN 历史版本（`svn log -r`）
- **SVN 提交记录查询**：图形化展示最近提交（版本号 / 作者 / 提交信息），支持复选框多选 + 分页加载更多
- **文件勾选树**：按包/目录路径自动分组的可折叠树，父子节点联动勾选
- **全选 / 排除**：全选一键勾选；排除下勾选的文件将被排除
- **多项目独立配置**：每个项目使用 `{项目根}/svn-deploy/deploy.properties` 独立配置，首次使用自动从模板创建
- **部署脚本生成**：自动生成 `deploy.sh`（带备份+部署）和 `copy.sh`（带备份+打包）
- **上线申请表自动生成**：部署后可选生成「上线申请表」`.docx`。勾选「生成上线申请表」后弹窗填写功能名/上线内容描述/上线时间（均为非必填），移交位置与文件清单自动生成（基于 Apache POI 填充内置模板）
- **按需求目录归档**：勾选「生成上线申请表」时，源文件/编译文件/正式环境备份统一归档到「模板目录父级 / 上线时间(YYYY-MM-DD)+功能名称」需求目录下（内部结构同模板：`源文件/`、`上线发布文件/`、`正式环境文件备份(上线前)/`），申请表输出到 `相关文档/海口12345平台系统功能上线申请表.docx`；未勾选则不复制代码，发布仍走 `deployDir`

## 环境要求

| 项 | 要求 |
|---|---|
| IntelliJ IDEA | 2023.1+（build 231 ~ 243.*） |
| JDK | 17（编译插件用，运行时由 IDEA 提供） |
| SVN 客户端 | 系统已安装 `svn` 命令行（`/opt/homebrew/bin/svn` 或 PATH 中可查到） |
| 操作系统 | macOS / Linux / Windows（已在 macOS Apple Silicon 验证） |

## 安装

### 方式 1：从 Release 下载安装（推荐）

1. 前往 [Releases](../../releases) 下载最新版 `svn-deploy-plugin-x.x.x.zip`
2. 打开 IDEA → **Settings** → **Plugins**
3. 点击右上角 ⚙️ → **Install Plugin from Disk**
4. 选择下载的 zip 文件 → **OK** → **重启 IDEA**

### 方式 2：从源码构建

```bash
git clone https://github.com/FggMiaoWu/svn-deploy-plugin.git
cd svn-deploy-plugin
./build.sh
# 产物：build/distributions/svn-deploy-plugin-x.x.x.zip
```

然后在 IDEA 中按方式 1 的步骤 2-4 安装生成的 zip。

> 构建需要本机有 JDK 17（如通过 SDKMAN 安装：`sdk install java 17.0.x-tem`）。
> 如使用本机已安装的 IDEA 作为 SDK，需配置 `.idea-sdk` 包装目录（见 [开发文档](#开发指南)）。

## 使用指南

### 1. 触发插件

在 IDEA 项目视图（左侧 Project 面板）中**右键 → SVN 部署**。

### 2. 首次使用（自动创建配置）

首次在某个项目触发插件时，会自动在项目根目录创建配置文件：

```
{项目根}/svn-deploy/deploy.properties
```

并弹窗提示。请按当前项目实际情况修改其中的路径配置（详见 [配置说明](#配置说明)）。

### 3. 操作流程

```
┌─────────────────────────────────────────────────┐
│ SVN 部署对话框                                   │
├─────────────────────────────────────────────────┤
│ 部署环境:  ○ 测试环境  ○ 生产环境                │
│ 获取方式:  ○ 本地修改  ○ SVN 历史                │
│ SVN 版本号: [r1854,r1846________________]        │
│                                                  │
│ [获取 SVN 提交记录]  [加载更多]                  │
│ ┌──┬────────┬───────────┬──────────────────┐   │
│ │选│ 版本号 │ 作者      │ 提交信息          │   │
│ ├──┼────────┼───────────┼──────────────────┤   │
│ │☑│ r1854  │ wangcheng │ 代码合并          │   │
│ │☐│ r1853  │ wangcheng │ 优化              │   │
│ └──┴────────┴───────────┴──────────────────┘   │
│                          [获取修改文件]         │
│ ┌─────────────────────────────────────────┐    │
│ │ ▼ java.com.ideal.epi.entity  (3)        │    │
│ │   ☑ CallInfo.java                       │    │
│ │   ☑ User.java                           │    │
│ │ ▼ webapp.WEB-INF.view  (2)              │    │
│ │   ☑ index.jsp                           │    │
│ └─────────────────────────────────────────┘    │
│ ☑ 全选  ☐ 排除  ☐ 生成上线申请表              │
└─────────────────────────────────────────────────┘
```

**SVN 历史模式流程**：
1. 选「SVN 历史」→ 点「获取 SVN 提交记录」→ 表格加载最近 5 条提交
2. 勾选需要的提交（支持多选）→ 版本号自动填入输入框
3. 不够？点「加载更多」→ 追加 5 条更早的提交
4. 点「获取修改文件」→ 按勾选的版本号拉取变更文件
5. 在文件树中勾选要部署的文件 → 点「OK」执行部署

**本地修改模式流程**：
1. 选「本地修改」→ 直接点「获取修改文件」→ 基于 `svn st` 拉取改动文件
2. 勾选要部署的文件 → 点「OK」执行部署

### 4. 部署产物

部署成功后，在 `deploy.dir` 配置的目录下生成：

```
{deploy.dir}/
├── CallInfo.class          ← 复制的编译产物
├── User.class
├── deploy.sh               ← 部署脚本：备份旧文件 → 部署新文件
├── copy.sh                 ← 复制脚本：从服务器拉取旧文件打包备份
└── ...
```

同时复制到功能模板目录（`function.source.dir` / `function.compile.dir`）。

## 配置说明

配置文件路径：`{项目根}/svn-deploy/deploy.properties`

首次使用自动创建，直接编辑该文件即可修改配置。

| 配置项 | 说明 | 示例 |
|---|---|---|
| `src.dir` | 源码目录（相对项目根） | `src/main` |
| `src.ignore.Prefix` | 编译目录需忽略的源码前缀（逗号分隔） | `java,webapp,resources` |
| `build.dir` | 编译输出目录（相对项目根） | `target/workorder/WEB-INF/classes` |
| `web.dir` | Web 资源目录（相对项目根，JSP 等） | `target/workorder` |
| `deploy.dir` | 部署文件输出目录（相对项目根） | `A_compoes/localfile` |
| `test.dir` | 测试环境服务器路径（绝对路径） | `/mnt/tomcat/.../webapps/workorder` |
| `production.dir` | 生产环境服务器路径（绝对路径） | `/mnt/tomcat/.../webapps/workorder` |
| `function.source.dir` | 功能模板-源文件目录（本地路径） | `/Users/xxx/模板/源文件` |
| `function.compile.dir` | 功能模板-上线文件目录（本地路径） | `/Users/xxx/模板/上线发布文件` |
| `function.backup.dir` | 功能模板-备份目录（本地路径） | `/Users/xxx/模板/正式环境文件备份` |
| `svn.ignore.files` | SVN 忽略文件模式（分号分隔，支持 `**` 通配） | `**/*.tmp;**/*.log` |
| `function.dir` | 模板基础目录：勾选生成申请表时按「上线时间(YYYY-MM-DD)+功能名称」在其父目录下新建需求目录（结构同模板）；未勾选或字段未填则不做归档 | `/Users/xxx/模板` |
| `form.apply.unit` / `form.apply.contact` / `form.apply.phone` | 申请单位 / 联系人 / 联系电话 | `海口电信` / `蒙元纶` |
| `form.dev.*` / `form.coop.*` | 开发单位 / 配合单位负责人信息 | |
| `form.svn.base.dir` | SVN 集中升级发布文档根路径（后接 `/` + 需求目录名） | `svn://172.26.197.90:8000/.../2026升级发布文档` |
| `form.output.dir` | 申请表输出目录（能构成需求目录时随即输出到需求目录根；仅无法构成时才用此项，回退用户主目录） | |

> 提示：`deploy.properties` 可提交到 SVN 团队共享，新人检出项目后即用，无需重新配置。

## 项目结构

```
svn-deploy-plugin/
├── build.gradle.kts              ← IntelliJ Platform 插件构建配置
├── gradle.properties             ← 插件元数据 + JDK 配置
├── build.sh                      ← 一键构建脚本
└── src/main/
    ├── java/com/deploy/
    │   ├── DeployAction.java     ← 入口 Action（右键菜单触发）
    │   ├── DeployDialog.java     ← 图形界面（Swing + IntelliJ UI 组件）
    │   ├── DeployRequest.java    ← 参数化请求对象
    │   ├── DeployService.java    ← 核心逻辑（SVN 调用 + 文件操作 + 脚本生成 + 申请表生成）
    │   ├── UploadFormGenerator.java ← 上线申请表模板填充（Apache POI）
    │   └── DFile.java            ← 文件数据类
    └── resources/
        ├── META-INF/plugin.xml   ← 插件描述 + Action 注册
        ├── deploy/default.properties  ← 内置默认配置模板
        └── upload/上报申请模板.docx    ← 上线申请表内置模板
```

## 开发指南

### 环境准备

1. 安装 JDK 17（建议通过 SDKMAN）：
   ```bash
   sdk install java 17.0.x-tem
   ```

2. 配置 IntelliJ Platform SDK（使用本机 IDEA）：
   - macOS 的 `IntelliJ IDEA.app` bundle 结构与插件 SDK 预期不符
   - 需创建 `.idea-sdk` 包装目录，软链接 IDEA 组件并补 `build.txt`
   - 在 `build.gradle.kts` 中：`localPath.set(file(".idea-sdk").canonicalPath)`

3. 使用阿里巴巴云 Maven 镜像加速依赖下载（已在 `settings.gradle.kts` 配置）

### 构建与调试

```bash
# 一键构建（自动检查 JDK 17 + 输出插件包路径）
./build.sh

# 启动沙箱 IDEA 调试
./gradlew runIde

# 仅编译验证
./gradlew build
```

### 关键技术点

- **SVN 进程调用**：用 `ProcessBuilder` 执行 `svn` 命令，daemon 线程读取输出 + 8 秒超时 + `destroyForcibly` 防止挂起
- **中文 locale 处理**：设置 `LANG=zh_CN.UTF-8` 避免 SVN 把中文转义成 `{U+XXXX}`
- **SVN 认证缓存**：显式设置 `HOME` 环境变量，让 svn 能访问 `~/.subversion/auth/` 凭证
- **Swing 线程模型**：耗时 SVN 调用放后台线程，UI 更新用 `SwingUtilities.invokeLater` 切回 EDT
- **分页加载**：`svn log -l N -r startRev:1` 实现提交记录往前翻页

详细开发文档见 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)。

## 兼容性

- IntelliJ IDEA 2023.1 ~ 2024.3.*（build 231 ~ 243.*）
- 依赖 IntelliJ Platform 核心模块和 VCS 模块
- SVN 客户端需在系统 PATH 中可查到，或位于 `/opt/homebrew/bin/svn`（Apple Silicon）/ `/usr/local/bin/svn`（Intel）

## License

MIT

## 致谢

本项目源自控制台交互式的 SVN 部署工具，改造为 IntelliJ 插件 + 图形界面，支持多项目独立配置。
