# SVN Deploy Plugin 开发总结

> 本文档记录插件从构思到骨架完成的全过程，供后续新会话上下文参考。

## 一、项目背景

### 起源
原项目 `workorder12345-haikou-branch` 中有一个控制台交互式的 SVN 部署工具 [SvnDeployTool.java](file:///Users/Admin/work/workcode/workorder12345-haikou-branch/src/main/java/deploy/SvnDeployTool.java)，功能：
- 通过 SVN 版本号或本地修改获取改动文件
- 交互式勾选文件
- 复制到部署目录
- 生成 `deploy.sh` / `copy.sh` 部署脚本

### 痛点
- 纯控制台交互（`System.in`），体验差
- 路径写死（`/Users/Admin/...`），无法多项目复用
- 配置硬编码，不利于团队协作

### 目标
改造为 **IntelliJ 插件 + 图形界面**，支持多项目独立配置。

## 二、核心决策

### 决策 1：新建独立 Gradle 项目，不改动原代码
- 原项目 `workorder12345-haikou-branch` 是 Maven web 项目，与插件的 Gradle + IntelliJ Platform SDK 体系不兼容
- 原代码保持不动，逻辑**复制一份**到插件项目，两边独立演进
- 原项目继续用 `run.sh` 跑控制台版本，互不影响

### 决策 2：逻辑复用方式——复制
| 方案 | 优劣 |
|---|---|
| 复制逻辑到插件项目 ✅ | 两份独立代码，原项目零改动；缺点是逻辑要维护两份 |
| 原项目打 jar 插件依赖 | 逻辑只一份，但原项目要改打包配置 |
| 运行时反射调用 | 不破坏原代码，但交互改造受限 |

最终选择复制，保证原项目零风险。

### 决策 3：配置管理——项目级独立配置
多项目场景下配置管理是关键，采用方案：

**配置查找顺序**（优先级从高到低）：
1. 显式传入的 `configPath`（可选）
2. **项目级**：`{项目根}/svn-deploy/deploy.properties` ← 主方案
3. 内置兜底：插件自带 `default.properties`

**首次使用流程**：
- 插件检测到项目无 `svn-deploy/deploy.properties` → 自动从内置模板创建一份
- 弹提示告知用户已创建，建议按项目情况修改
- 对话框提供「编辑配置」按钮，点击直接在 IDEA 编辑器中打开

**多项目使用**：
- 每个项目配置独立，可提交到 SVN 团队共享
- 新人 clone 项目后即用，无需重新配置

**环境配置组织**：
- 单文件多环境：`test.dir` 和 `production.dir` 同存一个文件
- 对话框选环境时读对应字段，无需切换文件

## 三、架构设计

### 分层结构

```
┌─────────────────────────────────────┐
│  IntelliJ Action（入口）             │  右键菜单 / 工具栏
└──────────────┬──────────────────────┘
               ↓
┌─────────────────────────────────────┐
│  DeployDialog（图形界面）             │  ★ 焦点：替代 System.in
│  环境选择 · 版本号 · 文件勾选 · 部署预览  │
└──────────────┬──────────────────────┘
               ↓
┌─────────────────────────────────────┐
│  DeployService（核心服务）            │  复用原纯逻辑，剥离交互
│  loadConfig · getModifiedFiles       │
│  copyFiles · generateScripts         │
└──────────────┬──────────────────────┘
               ↓
┌──────────┬───────────┬──────────────┐
│ 部署目录  │ deploy.sh │   copy.sh    │
└──────────┴───────────┴──────────────┘
```

### 关键改造点

**剥离交互**：原 `SvnDeployTool` 中以下方法用 `System.in`，是插件化最大障碍：
- `selectEnvironment()`（[原 L184-L206](file:///Users/Admin/work/workcode/workorder12345-haikou-branch/src/main/java/deploy/SvnDeployTool.java#L184-L206)）
- `getModifiedFilesFromSvn()`（[原 L302-L339](file:///Users/Admin/work/workcode/workorder12345-haikou-branch/src/main/java/deploy/SvnDeployTool.java#L302-L339)）
- `selectFilesInteractively()`（[原 L102-L158](file:///Users/Admin/work/workcode/workorder12345-haikou-branch/src/main/java/deploy/SvnDeployTool.java#L102-L158)）

改造为**参数化**方法，接收 `DeployRequest` 对象，无 `System.in`。

**路径动态化**：
- `projectRoot` 从 `Project.getBasePath()` 取，不再写死 `/Users/Admin/...`
- `mkdirsFunction` 里写死的功能模板目录（[原 L162-L180](file:///Users/Admin/work/workcode/workorder12345-haikou-branch/src/main/java/deploy/SvnDeployTool.java#L162-L180)）改为从配置读取

**原控制台提问 → 图形控件映射**：

| 原控制台提问 | 插件控件 |
|---|---|
| 选环境 1/2 | `JRadioButton`（测试/生产） |
| 本地修改 / SVN历史 | `JRadioButton` |
| 输入版本号 r1562,r1565 | `JTextField` |
| 输入编号 1,3,5 / all / !1 | `CheckBoxList` + 全选 + 排除模式 |

## 四、项目结构

```
/Users/Admin/work/workcode/svn-deploy-plugin/
├── settings.gradle.kts
├── build.gradle.kts              ← IntelliJ Platform 插件构建
├── gradle.properties             ← 插件元数据 + JDK 配置
└── src/main/
    ├── java/com/deploy/
    │   ├── DeployAction.java     ← 入口 Action（右键菜单触发）
    │   ├── DeployDialog.java     ← 图形界面（替代 System.in）
    │   ├── DeployRequest.java    ← 参数化请求对象
    │   ├── DeployService.java    ← 核心逻辑（复制自 SvnDeployTool，去交互）
    │   └── DFile.java            ← 文件数据类
    └── resources/
        ├── META-INF/plugin.xml   ← 插件描述 + Action 注册
        └── deploy/default.properties  ← 内置默认配置模板
```

## 五、关键文件说明

### 1. [DeployService.java](file:///Users/Admin/work/workcode/svn-deploy-plugin/src/main/java/com/deploy/DeployService.java)

核心服务类，从原 `SvnDeployTool` 复制纯逻辑并参数化。

**对外接口**：
```java
// 构造：自动查找/创建项目级配置
DeployService service = new DeployService(projectRoot);

// 取改动文件
List<DFile> modified = service.getModifiedFiles(req);

// 按参数过滤
List<DFile> selected = service.selectFiles(modified, req);

// 执行部署
DeployService.DeployResult result = service.deploy(selected, req);
```

**配置加载核心逻辑**（[L196-L316](file:///Users/Admin/work/workcode/svn-deploy-plugin/src/main/java/com/deploy/DeployService.java#L196-L316)）：
- `resolveAndLoadConfig(customConfigPath)`：按优先级查找配置
- `createProjectConfigFromTemplate()`：首次使用时从内置模板自动创建项目级配置
- 模板内 `${project.root}` 占位符自动替换为实际项目路径

**常量定义**：
```java
public static final String CONFIG_DIR = "svn-deploy";
public static final String CONFIG_FILE = "deploy.properties";
public static final String DEFAULT_CONFIG_RESOURCE = "/deploy/default.properties";
```

**辅助方法**（供 UI 调用）：
- `isConfigJustCreated()`：本次是否自动创建了配置（UI 据此提示用户）
- `getConfigPath()`：实际使用的配置文件路径（供「编辑配置」按钮打开）
- `getSrcDir()` / `getDeployDir()`：供 UI 展示

### 2. [DeployDialog.java](file:///Users/Admin/work/workcode/svn-deploy-plugin/src/main/java/com/deploy/DeployDialog.java)

图形界面，`extends DialogWrapper`。

**布局**（`createCenterPanel`）：
- 部署环境：`JRadioButton`（测试/生产）
- 获取方式：`JRadioButton`（本地修改/SVN 历史）
- SVN 版本号：`JTextField`（支持多版本逗号分隔）
- 「获取修改文件」按钮
- 文件勾选列表：`CheckBoxList<DFileItem>`
- 全选 / 排除模式 / 编辑配置按钮
- 配置文件路径提示（灰色小字）

**关键方法**：
- `fetchFiles()`：调用 `service.getModifiedFiles()` 填充勾选列表
- `toggleSelectAll()`：全选/取消全选
- `openConfigFile()`：用 IDEA 编辑器打开当前项目配置文件
- `doOKAction()`：构建请求 → 调用 `service.deploy()` → 弹结果对话框

**首次配置提示**：构造函数中检测 `service.isConfigJustCreated()`，若 true 则弹提示告知用户已创建配置。

### 3. [DeployAction.java](file:///Users/Admin/work/workcode/svn-deploy-plugin/src/main/java/com/deploy/DeployAction.java)

入口 Action，`extends AnAction`。

```java
public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    DeployService service = new DeployService(project.getBasePath());
    DeployDialog dialog = new DeployDialog(project, service);
    dialog.show();
}
```

注册在 `plugin.xml` 中，挂在 `ProjectViewPopupMenu`（项目视图右键菜单）。

### 4. [default.properties](file:///Users/Admin/work/workcode/svn-deploy-plugin/src/main/resources/deploy/default.properties)

内置默认配置模板，首次使用时复制到项目 `svn-deploy/deploy.properties`。

**关键字段**：
```properties
# 项目根目录（占位符，插件自动替换）
project.root=${project.root}

# 源码/编译/Web 目录（相对路径）
src.dir=src/main
build.dir=target/workorder12345/WEB-INF/classes
web.dir=target/workorder12345

# 部署输出目录（相对路径）
deploy.dir=A_compoes/localfile

# 测试/生产环境目录（服务器绝对路径）
test.dir=/mnt/tomcat/tomcat7_workorder_25500/webapps/workorder12345
production.dir=/mnt/tomcat/tomcat7_workorder_25100/webapps/workorder12345

# 功能模板目录（本地路径）
function.source.dir=/Users/Admin/work/需求文档/模板/源文件
function.compile.dir=/Users/Admin/work/需求文档/模板/上线发布文件
function.backup.dir=/Users/Admin/work/需求文档/模板/正式环境文件备份(上线前)

# SVN 忽略文件
svn.ignore.files=**/*.tmp;**/*.bak;**/*.log;**/*.properties;...
```

### 5. [plugin.xml](file:///Users/Admin/work/workcode/svn-deploy-plugin/src/main/resources/META-INF/plugin.xml)

```xml
<idea-plugin>
    <id>com.deploy.svn-deploy</id>
    <name>SVN Deploy</name>
    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.vcs</depends>

    <actions>
        <action id="SvnDeploy.Deploy"
                class="com.deploy.DeployAction"
                text="SVN 部署">
            <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
        </action>
    </actions>
</idea-plugin>
```

### 6. [build.gradle.kts](file:///Users/Admin/work/workcode/svn-deploy-plugin/build.gradle.kts)

使用 `org.jetbrains.intellij` 1.17.0 插件，目标 IntelliJ 2023.1，需 JDK 17 编译。

## 六、构建与运行

### 环境要求
- **JDK 17**（IntelliJ 2023+ 强制要求，原项目用 JDK 8）
  ```bash
  sdk list java
  sdk install java 17.0.x-tem
  ```
- Gradle（项目自带 wrapper，无需单独装）

### 常用命令

```bash
cd /Users/Admin/work/workcode/svn-deploy-plugin

# 编译验证
./gradlew build

# 启动沙箱 IDEA 调试插件（首次会下载约 800MB IntelliJ SDK）
./gradlew runIde

# 打包成 zip 安装包
./gradlew buildPlugin
# 产物：build/distributions/*.zip
```

### IDEA 内操作
- File → Open → 选择 `/Users/Admin/work/workcode/svn-deploy-plugin`
- 等待 Gradle 同步完成
- 如需指定 JDK 17，在 `gradle.properties` 末尾加：
  ```properties
  org.gradle.java.home=/Users/Admin/.sdkman/candidates/java/17.0.x-tem
  ```
- Gradle 工具窗口 → `runIde` 双击启动调试

### 安装到正式 IDEA
- `./gradlew buildPlugin` 生成 zip
- 正式 IDEA → Settings → Plugins → ⚙️ → Install Plugin from Disk → 选 zip

## 七、当前完成状态

| 模块 | 状态 | 说明 |
|---|---|---|
| Gradle 构建配置 | ✅ | `build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` |
| 插件描述 | ✅ | `plugin.xml` 注册 Action 到右键菜单 |
| 内置默认配置 | ✅ | `default.properties` 模板，含 `${project.root}` 占位符 |
| 数据类 | ✅ | `DFile` / `DeployRequest` |
| 核心服务 | ✅ | `DeployService` 复制原逻辑 + 参数化 + 配置自动查找/创建 |
| 图形界面 | ✅ | `DeployDialog` 环境选择/版本号/勾选列表/编辑配置 |
| 入口 Action | ✅ | `DeployAction` 挂右键菜单 |

## 八、待办与增强方向

### 必做（验证阶段）
- [ ] 编译验证：移动项目后用 IDEA 打开，跑 `./gradlew build` 确认无编译错误
- [ ] `runIde` 调试：在沙箱 IDEA 中测试完整流程
- [ ] 真实项目测试：在 `workorder12345-haikou-branch` 项目中触发插件，验证配置自动创建 + 文件勾选 + 部署脚本生成

### 可选增强
- [ ] 配置校验：`.properties` 文件加 JSON Schema 或自定义校验，避免填错键名
- [ ] 「编辑配置」加「重置为默认」选项
- [ ] 自动把 `svn-deploy/` 加到 `.svnignore`（如果不想提交配置）
- [ ] SVN 调用走 IntelliJ VCS API（`VcsContextFactory`），替代 `ProcessBuilder("svn", ...)`，更稳定
- [ ] 部署前确认弹窗：`clearDirectory(deployDirFile)` 会删整个目录，建议加二次确认
- [ ] 历史版本号记忆：记住上次输入的版本号
- [ ] 文件预览：勾选列表项支持双击预览文件内容/差异

### 已知限制
- 当前 `function.*.dir` 默认值仍写死 `/Users/Admin/work/需求文档/模板/...`，其他机器需手动改配置
- SVN 调用依赖系统 `svn` 命令行，未走 IntelliJ SVN4 IDEA 插件 API
- 部署目录每次启动会清空（`clearDirectory`），需注意

## 九、关键代码位置索引

| 功能 | 文件 | 方法/位置 |
|---|---|---|
| 配置查找与自动创建 | `DeployService.java` | `resolveAndLoadConfig()` L196-L282 |
| 从模板创建项目配置 | `DeployService.java` | `createProjectConfigFromTemplate()` L284-L316 |
| 获取修改文件 | `DeployService.java` | `getModifiedFiles()` L88-L104 |
| 按参数筛选文件 | `DeployService.java` | `selectFiles()` L107-L132 |
| 执行部署 | `DeployService.java` | `deploy()` L138-L182 |
| 生成 deploy.sh | `DeployService.java` | `generateDeployScript()` + `appendDeployBlock()` |
| 生成 copy.sh | `DeployService.java` | `generateCopyScript()` + `appendCopyBlock()` |
| 图形界面布局 | `DeployDialog.java` | `createCenterPanel()` L77-L165 |
| 首次配置提示 | `DeployDialog.java` | 构造函数 L57-L74 |
| 编辑配置按钮 | `DeployDialog.java` | `openConfigFile()` L167-L178 |
| 执行部署（OK按钮） | `DeployDialog.java` | `doOKAction()` |
| 入口 Action | `DeployAction.java` | `actionPerformed()` |

## 十、与原项目的对应关系

| 原 SvnDeployTool.java | 插件项目 | 说明 |
|---|---|---|
| `main()` + `deploy()` | `DeployService.deploy()` | 入口参数化 |
| `loadConfig()` | `DeployService.resolveAndLoadConfig()` | 加自动查找/创建 |
| `selectEnvironment()` | `DeployDialog` 的 `JRadioButton` | 控制台→图形 |
| `getModifiedFilesFromSvn()` | `DeployService.getModifiedFiles()` | 接收 `DeployRequest` |
| `selectFilesInteractively()` | `DeployService.selectFiles()` + `DeployDialog` 勾选列表 | 控制台→图形 |
| `mkdirsFunction()` | `DeployService.mkdirsFunction()` | 路径从配置读 |
| `generateDeployScript()` | `DeployService.generateDeployScript()` | 逻辑不变 |
| `generateCopyScript()` | `DeployService.generateCopyScript()` | 逻辑不变 |
| `DFile` 内部类 | `DFile.java` 独立类 | 提取为顶层类 |

---

**文档生成时间**：2026-08-07
**原项目路径**：`/Users/Admin/work/workcode/workorder12345-haikou-branch`
**插件项目路径**：`/Users/Admin/work/workcode/svn-deploy-plugin`
