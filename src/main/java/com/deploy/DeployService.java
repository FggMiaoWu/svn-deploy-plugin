package com.deploy;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 部署核心服务（从原 SvnDeployTool 复制纯逻辑并参数化，去除所有 System.in 交互）。
 * <p>
 * 配置查找顺序：
 * <ol>
 *   <li>项目级：{projectRoot}/svn-deploy/deploy.properties</li>
 *   <li>自定义路径：构造时传入 configPath（可选）</li>
 *   <li>内置兜底：classpath 内 /deploy/default.properties</li>
 * </ol>
 * 首次未找到项目级配置时，会自动从内置模板创建一份，方便用户按需修改。
 * <p>
 * 用法：
 * <pre>
 *   DeployService service = new DeployService(projectRoot);          // 自动查找/创建项目级配置
 *   List&lt;DFile&gt; modified = service.getModifiedFiles(req);   // 取改动文件
 *   List&lt;DFile&gt; selected = service.selectFiles(modified, req); // 按参数过滤
 *   DeployResult result = service.deploy(selected, req);     // 执行部署
 * </pre>
 */
public class DeployService {

    /** 项目级配置子目录名 */
    public static final String CONFIG_DIR = "svn-deploy";
    /** 项目级配置文件名 */
    public static final String CONFIG_FILE = "deploy.properties";
    /** 内置默认配置资源路径 */
    public static final String DEFAULT_CONFIG_RESOURCE = "/deploy/default.properties";

    /** SVN 可执行文件名 */
    private static final String SVN_EXECUTABLE = resolveSvnExecutable();

    /**
     * 解析 svn 可执行文件绝对路径。
     * GUI 启动的 IDEA 进程 PATH 通常不含 /opt/homebrew/bin，导致 "No such file or directory"。
     * 依次尝试：PATH 中查找 → Homebrew Apple Silicon 路径 → Homebrew Intel 路径 → 兜底 "svn"。
     */
    private static String resolveSvnExecutable() {
        // 1. 尝试从 PATH 查找（"which svn"）
        try {
            Process p = new ProcessBuilder("which", "svn").redirectErrorStream(true).start();
            String line = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();
            if (line != null && !line.isEmpty() && p.waitFor() == 0) {
                File f = new File(line.trim());
                if (f.canExecute()) return f.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        // 2. 常见 Homebrew 路径兜底
        String[] candidates = {
                "/opt/homebrew/bin/svn",                  // Apple Silicon
                "/usr/local/bin/svn",                     // Intel
                "/opt/homebrew/Cellar/subversion",        // 透传到 Cellar（极少数情况）
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.canExecute()) return f.getAbsolutePath();
        }
        // 3. 兜底（让原错误信息更明确，方便排查）
        return "svn";
    }

    private Properties config;
    private String projectRoot;
    private String configPath;          // 实际使用的配置文件路径
    private boolean configJustCreated;  // 标记本次是否自动创建了项目级配置
    private String srcDir;
    private String buildDir;
    private String webDir;
    private String deployDir;
    private String testDir;
    private String productionDir;
    private String deployScript;
    private String copyScript;
    private String rollbackScript;
    private List<String> ignoreFiles;
    private List<String> srcIgnorePrefixes;
    private String selectedEnvironment;

    private String functionSourceDir;
    private String functionCompileDir;
    private String functionBackupDir;

    /**
     * 构造服务：自动查找项目级配置 {projectRoot}/svn-deploy/deploy.properties。
     * 若不存在则从内置模板自动创建一份（configJustCreated 置 true，调用方可提示用户编辑）。
     *
     * @param projectRoot 项目根目录（来自 IntelliJ Project basePath）
     */
    public DeployService(String projectRoot) {
        this(projectRoot, null);
    }

    /**
     * 构造服务。
     *
     * @param projectRoot 项目根目录
     * @param configPath  自定义配置文件路径；
     *                    为 null 则查找 {projectRoot}/svn-deploy/deploy.properties，找不到则自动创建
     */
    public DeployService(String projectRoot, String configPath) {
        this.projectRoot = normalizePath(projectRoot);
        resolveAndLoadConfig(configPath);
    }

    /** 本次构造时是否自动创建了项目级配置文件（供 UI 提示用户编辑） */
    public boolean isConfigJustCreated() {
        return configJustCreated;
    }

    /** 实际使用的配置文件绝对路径（供「编辑配置」按钮打开） */
    public String getConfigPath() {
        return configPath;
    }

    /**
     * 获取最近 N 条 SVN 提交记录（用于版本号下拉框提示）。
     * <p>
     * 执行 {@code svn log -r HEAD:1 -l N}，分页由调用方递增 limit 实现（首页 limit=5，
     * 加载更多 limit=10、15...），UI 层对返回结果去重追加。
     * <p>
     * 工作目录优先用 srcDir，srcDir 为空或不存在时回退到 projectRoot。
     *
     * @param limit 最多获取多少条
     * @return 提交记录列表
     */
    public List<SvnLogEntry> getRecentSvnLogs(int limit) {
        File workDir;
        if (srcDir != null && new File(srcDir).isDirectory()) {
            workDir = new File(srcDir);
        } else {
            workDir = new File(projectRoot);
        }
        return runSvnLog(workDir, limit);
    }

    /**
     * 实际执行 svn log -r HEAD:1 -l limit 并解析结果。
     *
     * @param workDir svn log 执行的工作目录
     * @param limit   最多获取多少条
     * @return 提交记录列表
     */
    private List<SvnLogEntry> runSvnLog(File workDir, int limit) {
        List<SvnLogEntry> result = new ArrayList<>();
        Process process = null;
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(SVN_EXECUTABLE);
            cmd.add("log");
            // 显式指定 HEAD:1 范围，从仓库最新版本向前查询，
            // 否则 svn log 默认从本地工作副本 BASE revision 开始，
            // 本地未 svn update 时会漏掉仓库最新提交
            cmd.add("-r");
            cmd.add("HEAD:1");
            cmd.add("-l");
            cmd.add(String.valueOf(limit));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workDir);
            // 设置 locale 为 UTF-8，避免 SVN 把中文转义成 {U+XXXX}
            pb.environment().put("LANG", "zh_CN.UTF-8");
            pb.environment().put("LC_ALL", "zh_CN.UTF-8");
            // 显式设置 HOME，让 svn 能访问 ~/.subversion/auth/ 凭证缓存，
            // 否则在某些认证配置下进程会静默挂起
            pb.environment().put("HOME", System.getProperty("user.home"));
            // 合并 stderr 到 stdout，便于在非 SVN 目录等错误场景下拿到 svn 的报错信息
            pb.redirectErrorStream(true);
            process = pb.start();

            // 必须用 daemon 线程读取输出流，否则当 svn 进程挂起时 readLine() 会无限阻塞，
            // 导致 waitFor() 永远等不到、整个后台线程死锁
            final Process finalProcess = process;
            List<String> lines = new ArrayList<>();
            Thread readerThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(finalProcess.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (Exception ignored) {
                }
            }, "svn-log-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            // 8 秒超时，防止 svn 进程无限阻塞（认证挂起、网络不可达等）
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                // 等读取线程把已缓冲的输出读完
                readerThread.join(1000);
                String raw = lines.isEmpty() ? "(无输出)" : String.join("\n", lines);
                throw new RuntimeException("svn log 执行超时（8秒），已强制结束。部分输出:\n" + raw);
            }
            readerThread.join(2000);
            int exitCode = process.exitValue();

            // 头部行格式（英文 locale）: r1846 | wangcheng | 2026-08-04 10:16:43 +0800 (Tue, 04 Aug 2026) | 1 line
            // 头部行格式（中文 locale）: r1846 | wangcheng | 2026-08-04 10:16:43 +0800 (二, 2026-08-04) | 1 行
            // 正则只匹配到行数数字为止，不限定 line/行 单位词，兼容中英文 locale
            java.util.regex.Pattern headPattern = java.util.regex.Pattern.compile(
                    "^r(\\d+)\\s*\\|\\s*(.+?)\\s*\\|\\s*.+?\\|\\s*\\d+\\s*");
            // 解析：遇到头部行后，取其后第一个非空、非分隔线作为提交信息
            for (int i = 0; i < lines.size(); i++) {
                java.util.regex.Matcher m = headPattern.matcher(lines.get(i));
                if (!m.find()) continue;
                String revision = "r" + m.group(1);
                String author = m.group(2);
                String message = "";
                for (int j = i + 1; j < lines.size(); j++) {
                    String ml = lines.get(j).trim();
                    if (ml.isEmpty()) continue;
                    if (ml.startsWith("----")) break;
                    message = ml;
                    break;
                }
                result.add(new SvnLogEntry(revision, author, message));
            }

            // exitCode 非 0 且没解析到任何记录时，把 svn 的原始输出作为异常信息抛出
            if (result.isEmpty() && exitCode != 0) {
                String raw = lines.isEmpty() ? "(无输出)" : String.join("\n", lines);
                throw new RuntimeException("svn log 退出码=" + exitCode + "，输出:\n" + raw);
            }
            // 已翻到最早版本（无更多记录）时返回空列表，不抛异常，由调用方判断
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("获取 SVN 提交记录失败: " + e.getMessage(), e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return result;
    }

    /** SVN 提交记录（版本号 + 作者 + 提交信息） */
    public static class SvnLogEntry {
        private final String revision;
        private final String author;
        private final String message;

        public SvnLogEntry(String revision, String author, String message) {
            this.revision = revision;
            this.author = author;
            this.message = message;
        }

        public String getRevision() { return revision; }
        public String getAuthor() { return author; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            // 截断过长的提交信息，避免下拉框太宽
            String shortMsg = message.length() > 40 ? message.substring(0, 40) + "..." : message;
            return revision + " - " + author + " - " + shortMsg;
        }
    }

    // ===================== 对外入口 =====================

    /** 获取修改/新增的文件列表（本地或 SVN 历史） */
    public List<DFile> getModifiedFiles(DeployRequest req) {
        this.selectedEnvironment = req.getEnvironment();

        File srcDirFile = new File(this.srcDir);
        if (!srcDirFile.exists()) {
            throw new IllegalStateException(this.srcDir + " 目录不存在");
        }

        Map<String, DFile> fileMap = new HashMap<>();
        if ("1".equals(req.getMode())) {
            return getModifiedFilesFromLocal(srcDirFile, fileMap);
        } else {
            return getModifiedFilesFromSvnHistory(srcDirFile, fileMap, req.getRevisions());
        }
    }

    /** 按请求参数从修改文件列表中筛选出要部署的文件（替代原 selectFilesInteractively） */
    public List<DFile> selectFiles(List<DFile> modifiedFiles, DeployRequest req) {
        if (modifiedFiles == null || modifiedFiles.isEmpty()) {
            return new ArrayList<>();
        }
        if (req.isSelectAll()) {
            return new ArrayList<>(modifiedFiles);
        }

        List<DFile> selected;
        if (req.isExcludeMode()) {
            // 排除模式：先全选，再移除指定索引
            selected = new ArrayList<>(modifiedFiles);
            for (Integer idx : req.getSelectedIndices()) {
                if (idx >= 0 && idx < selected.size()) {
                    selected.remove(modifiedFiles.get(idx));
                }
            }
        } else {
            // 选中模式：只保留指定索引
            selected = new ArrayList<>();
            for (Integer idx : req.getSelectedIndices()) {
                if (idx >= 0 && idx < modifiedFiles.size()) {
                    selected.add(modifiedFiles.get(idx));
                }
            }
        }
        return selected;
    }

    /** 部署结果 */
    public static class DeployResult {
        public int successCount;
        public int failCount;
        public List<DFile> deployFiles = new ArrayList<>();
        // 源文件清单（文件名，含扩展名）
        public List<String> sourceFileNames = new ArrayList<>();
        // 结果文件清单（文件名，含扩展名，含内部类）
        public List<String> resultFileNames = new ArrayList<>();
        // 源文件分类统计
        public int sourceJavaCount;
        public int sourceJspCount;
        public int sourceXmlCount;
        // 结果文件分类统计
        public int resultClassCount;
        public int resultJspCount;
        public int resultXmlCount;
    }

    /**
     * 执行部署：复制文件到部署目录与功能模板目录，并生成 deploy.sh / copy.sh。
     */
    public DeployResult deploy(List<DFile> selectedFiles, DeployRequest req) {
        this.selectedEnvironment = req.getEnvironment();
        DeployResult result = new DeployResult();

        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return result;
        }

        mkdirsFunction();

        // 按 compilePath 去重，避免同一编译产物被重复部署（导致 deploy.sh 重复命令、统计虚高）
        Set<String> seenCompilePaths = new HashSet<>();

        for (DFile dFile : selectedFiles) {
            try {
                // 主文件去重：同一编译产物只处理一次
                if (dFile.getCompilePath() == null
                        || !seenCompilePaths.add(dFile.getCompilePath())) {
                    continue;
                }

                copyFileToDir(dFile.getCompilePath(), deployDir);
                copyFileToDir(dFile.getSourcePath(), functionSourceDir);
                copyFileToDir(dFile.getCompilePath(), functionCompileDir);

                // 收集源文件清单与统计
                String srcName = getFileNameWithExtension(dFile.getSourcePath());
                result.sourceFileNames.add(srcName);
                String srcType = dFile.getSourceFileType();
                if ("java".equalsIgnoreCase(srcType)) result.sourceJavaCount++;
                else if ("jsp".equalsIgnoreCase(srcType)) result.sourceJspCount++;
                else if ("xml".equalsIgnoreCase(srcType)) result.sourceXmlCount++;

                // 收集结果文件清单与统计（主编译产物）
                String cmpName = getFileNameWithExtension(dFile.getCompilePath());
                result.resultFileNames.add(cmpName);
                String cmpType = dFile.getCompileFileType();
                if ("class".equalsIgnoreCase(cmpType)) result.resultClassCount++;
                else if ("jsp".equalsIgnoreCase(cmpType)) result.resultJspCount++;
                else if ("xml".equalsIgnoreCase(cmpType)) result.resultXmlCount++;

                List<DFile> innerClassFiles = dFile.getInnerClassFiles();
                if (innerClassFiles != null) {
                    for (DFile innerFile : innerClassFiles) {
                        // 内部类去重：跳过与主文件或其他内部类 compilePath 重复的条目
                        if (innerFile.getCompilePath() == null
                                || !seenCompilePaths.add(innerFile.getCompilePath())) {
                            continue;
                        }
                        copyFileToDir(innerFile.getCompilePath(), deployDir);
                        copyFileToDir(innerFile.getCompilePath(), functionCompileDir);
                        // 收集内部类结果文件清单与统计
                        String innerName = getFileNameWithExtension(innerFile.getCompilePath());
                        result.resultFileNames.add(innerName);
                        result.resultClassCount++;
                    }
                }

                result.successCount++;
                result.deployFiles.add(dFile);
            } catch (IOException e) {
                result.failCount++;
                e.printStackTrace();
            }
        }

        if (!result.deployFiles.isEmpty()) {
            generateDeployScript(result.deployFiles);
            generateCopyScript(result.deployFiles);
            generateRollbackScript(result.deployFiles);
        }
        return result;
    }

    // ===================== 配置加载 =====================

    /**
     * 解析并加载配置文件。
     * <p>查找顺序：
     * <ol>
     *   <li>显式传入的 configPath（非空时优先）</li>
     *   <li>{projectRoot}/svn-deploy/deploy.properties</li>
     *   <li>内置默认配置 /deploy/default.properties</li>
     * </ol>
     * 项目级配置不存在时，自动从内置模板创建一份，便于用户编辑。
     */
    private void resolveAndLoadConfig(String customConfigPath) {
        config = new Properties();
        File configFile = null;

        try {
            // 1. 显式路径优先
            if (customConfigPath != null && !customConfigPath.trim().isEmpty()) {
                configFile = new File(customConfigPath);
                if (!configFile.exists()) {
                    throw new FileNotFoundException("指定的配置文件不存在: " + customConfigPath);
                }
            }

            // 2. 项目级配置 {projectRoot}/svn-deploy/deploy.properties
            if (configFile == null) {
                File projectConfigDir = new File(projectRoot + File.separator + CONFIG_DIR);
                File projectConfigFile = new File(projectConfigDir, CONFIG_FILE);
                if (projectConfigFile.exists()) {
                    configFile = projectConfigFile;
                } else {
                    // 3. 首次使用：从内置模板自动创建项目级配置
                    configFile = createProjectConfigFromTemplate(projectConfigDir, projectConfigFile);
                    configJustCreated = true;
                }
            }

            this.configPath = configFile.getAbsolutePath();

            try (InputStream is = new FileInputStream(configFile)) {
                config.load(new InputStreamReader(is, "UTF-8"));
            }

            srcDir = normalizePath(resolvePath(config.getProperty("src.dir", "."), projectRoot));
            buildDir = normalizePath(resolvePath(config.getProperty("build.dir", "."), projectRoot));
            webDir = normalizePath(resolvePath(config.getProperty("web.dir", "."), projectRoot));
            deployDir = normalizePath(resolvePath(config.getProperty("deploy.dir", "."), projectRoot));
            testDir = config.getProperty("test.dir", "");
            productionDir = config.getProperty("production.dir", "");
            deployScript = deployDir + File.separator + "deploy.sh";
            copyScript = deployDir + File.separator + "copy.sh";
            rollbackScript = deployDir + File.separator + "rollback.sh";

            File deployDirFile = new File(deployDir);
            if (deployDirFile.exists()) {
                clearDirectory(deployDirFile);
            }
            deployDirFile.mkdirs();

            // 功能模板目录（从配置读取，替代原写死路径）
            functionSourceDir = config.getProperty("function.source.dir",
                    "/Users/Admin/work/需求文档/模板/源文件");
            functionCompileDir = config.getProperty("function.compile.dir",
                    "/Users/Admin/work/需求文档/模板/上线发布文件");
            functionBackupDir = config.getProperty("function.backup.dir",
                    "/Users/Admin/work/需求文档/模板/正式环境文件备份(上线前)");

            // 忽略文件
            String ignoreFilesStr = config.getProperty("svn.ignore.files", "");
            ignoreFiles = new ArrayList<>();
            if (ignoreFilesStr != null && ignoreFilesStr.trim().length() > 0) {
                String[] patterns = ignoreFilesStr.split(";");
                for (String pattern : patterns) {
                    pattern = pattern.trim().replace("\\\\", "/").replace("\\", "/").toLowerCase();
                    if (pattern.startsWith("**/")) {
                        pattern = srcDir.replace("\\", "/").toLowerCase() + "/" + pattern;
                    }
                    if (pattern.length() > 0) {
                        ignoreFiles.add(pattern);
                    }
                }
            }

            // 源码前缀忽略
            String srcIgnorePrefixesStr = config.getProperty("src.ignore.Prefix", "");
            srcIgnorePrefixes = new ArrayList<>();
            if (srcIgnorePrefixesStr != null && srcIgnorePrefixesStr.trim().length() > 0) {
                String[] prefixes = srcIgnorePrefixesStr.split(",");
                for (String prefix : prefixes) {
                    prefix = prefix.trim();
                    if (prefix.length() > 0) {
                        srcIgnorePrefixes.add(prefix.toLowerCase());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("加载配置文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从内置默认模板创建项目级配置文件。
     * 模板内容写入后，将 project.root 占位符替换为当前项目根路径。
     */
    private File createProjectConfigFromTemplate(File configDir, File configFile) throws IOException {
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new IOException("无法创建配置目录: " + configDir.getAbsolutePath());
        }

        // 读取内置模板
        byte[] templateBytes;
        try (InputStream is = getClass().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (is == null) {
                throw new FileNotFoundException("内置默认配置模板不存在: " + DEFAULT_CONFIG_RESOURCE);
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) > 0) {
                bos.write(buf, 0, len);
            }
            templateBytes = bos.toByteArray();
        }

        String template = new String(templateBytes, "UTF-8");
        // 模板内若使用 ${project.root} 占位符则替换；否则保留原样（路径用相对值）
        template = template.replace("${project.root}", projectRoot.replace("\\", "/"));

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            fos.write(template.getBytes("UTF-8"));
        }
        return configFile;
    }

    // ===================== SVN 文件获取 =====================

    private List<DFile> getModifiedFilesFromLocal(File srcDirFile, Map<String, DFile> fileMap) {
        List<DFile> modifiedFiles = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(SVN_EXECUTABLE, "st", "-q");
            pb.directory(srcDirFile);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() > 0) {
                    String status = line.substring(0, 1);
                    String filePath = line.substring(1).replace("+", "").trim();
                    if (status.equals("M") || status.equals("A")) {
                        filePathConvertDfile(modifiedFiles, fileMap, filePath);
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("获取本地修改文件失败: " + e.getMessage(), e);
        }
        return modifiedFiles;
    }

    private List<DFile> getModifiedFilesFromSvnHistory(File srcDirFile, Map<String, DFile> fileMap,
                                                       List<String> revisions) {
        List<DFile> modifiedFiles = new ArrayList<>();
        if (revisions == null || revisions.isEmpty()) {
            return modifiedFiles;
        }

        for (String version : revisions) {
            version = version.trim();
            if (version.isEmpty()) {
                continue;
            }
            try {
                List<String> filePaths = getFilePathsForRevision(srcDirFile, version);
                for (String filePath : filePaths) {
                    filePathConvertDfile(modifiedFiles, fileMap, filePath);
                }
            } catch (Exception e) {
                throw new RuntimeException("获取版本 " + version + " 修改文件失败: " + e.getMessage(), e);
            }
        }
        return modifiedFiles;
    }

    private void filePathConvertDfile(List<DFile> modifiedFiles, Map<String, DFile> fileMap, String filePath) {
        if (isTargetFile(filePath)) {
            List<File> buildFiles = findFilesInBuildDir(filePath);
            if (buildFiles != null && !buildFiles.isEmpty()) {
                for (File sourceFile : buildFiles) {
                    String relativePath = getRelativePath(sourceFile);
                    String fileType = getFileType(filePath);
                    String webTargetPath = getWebTargetPath(relativePath, fileType);

                    DFile dFile = fileMap.get(filePath);
                    if (dFile == null) {
                        dFile = new DFile();
                        dFile.setFileName(getFileName(sourceFile.getAbsolutePath()));
                        dFile.setSourceFileType(fileType);
                        dFile.setCompileFileType(getCompileFileType(sourceFile.getAbsolutePath()));
                        dFile.setSourcePath(srcDir + "/" + filePath);
                        dFile.setCompilePath(sourceFile.getAbsolutePath());
                        dFile.setWebTargetPath(webTargetPath);
                        fileMap.put(filePath, dFile);
                        modifiedFiles.add(dFile);
                    } else {
                        DFile innerFile = new DFile();
                        innerFile.setFileName(getFileName(sourceFile.getAbsolutePath()));
                        innerFile.setSourceFileType(fileType);
                        innerFile.setCompileFileType(getCompileFileType(sourceFile.getAbsolutePath()));
                        innerFile.setSourcePath(filePath);
                        innerFile.setCompilePath(sourceFile.getAbsolutePath());
                        innerFile.setWebTargetPath(webTargetPath);
                        dFile.addInnerClassFile(innerFile);
                    }
                }
            }
        }
    }

    private List<String> getFilePathsForRevision(File srcDirFile, String revision) {
        List<String> filePaths = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(SVN_EXECUTABLE, "log", "-v", "-r", revision);
            pb.directory(srcDirFile);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("M ") || line.trim().startsWith("A ")) {
                    String filePath = line.trim().substring(2);
                    filePaths.add(getFilePathForSrcDir(filePath));
                }
            }
            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("获取版本 " + revision + " 文件列表失败: " + e.getMessage(), e);
        }
        return filePaths;
    }

    private String getFilePathForSrcDir(String filePath) {
        filePath = filePath.trim();
        String[] srcDirPathItems = srcDir.replace("\\", "/").split("/");
        String lastIndex = srcDirPathItems[srcDirPathItems.length - 1];
        StringBuilder pathBuilder = new StringBuilder();
        String[] pathItems = filePath.split("/");
        for (int i = pathItems.length - 1; i >= 0; i--) {
            String pathItem = pathItems[i];
            if (pathItem.equals(lastIndex)) {
                break;
            }
            pathBuilder.insert(0, "/" + pathItem);
        }
        if (pathBuilder.toString().startsWith("/")) {
            return pathBuilder.substring(1);
        }
        return pathBuilder.toString();
    }

    // ===================== 文件匹配 =====================

    private boolean matchesGlobPattern(String filePath, String pattern) {
        filePath = filePath.replace("\\", "/");
        pattern = pattern.replace("\\", "/");
        String[] pathParts = filePath.split("/");
        String[] patternParts = pattern.split("/");
        int pathIndex = 0, patternIndex = 0;
        while (pathIndex < pathParts.length && patternIndex < patternParts.length) {
            String pathPart = pathParts[pathIndex];
            String patternPart = patternParts[patternIndex];
            if (patternPart.equals("**")) {
                if (patternIndex + 1 == patternParts.length) return true;
                String nextPatternPart = patternParts[patternIndex + 1];
                while (pathIndex < pathParts.length) {
                    if (matchesWildcard(pathParts[pathIndex], nextPatternPart)) {
                        String subPath = joinArray(pathParts, pathIndex, pathParts.length);
                        String subPattern = joinArray(patternParts, patternIndex + 1, patternParts.length);
                        if (matchesGlobPattern(subPath, subPattern)) return true;
                    }
                    pathIndex++;
                }
                return false;
            } else if (matchesWildcard(pathPart, patternPart)) {
                pathIndex++;
                patternIndex++;
            } else {
                return false;
            }
        }
        if (patternIndex < patternParts.length) {
            String remainingPattern = joinArray(patternParts, patternIndex, patternParts.length);
            if (remainingPattern.equals("**")) return true;
        }
        return pathIndex == pathParts.length && patternIndex == patternParts.length;
    }

    private String joinArray(String[] array, int start, int end) {
        if (start >= end) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) sb.append("/");
            sb.append(array[i]);
        }
        return sb.toString();
    }

    private boolean matchesWildcard(String text, String pattern) {
        int textIndex = 0, patternIndex = 0;
        int starIndex = -1, matchIndex = 0;
        while (textIndex < text.length()) {
            if (patternIndex < pattern.length() &&
                    (pattern.charAt(patternIndex) == '?' || pattern.charAt(patternIndex) == text.charAt(textIndex))) {
                textIndex++;
                patternIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex;
                matchIndex = textIndex;
                patternIndex++;
            } else if (starIndex != -1) {
                patternIndex = starIndex + 1;
                matchIndex++;
                textIndex = matchIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') patternIndex++;
        return patternIndex == pattern.length();
    }

    private boolean isTargetFile(String filePath) {
        filePath = filePath.replace("\\", "/");
        if (!filePath.endsWith(".java") && !filePath.endsWith(".xml")
                && !filePath.endsWith(".jsp") && !filePath.endsWith(".js")) {
            return false;
        }
        String absolutePath = (srcDir + "/" + filePath).replace("\\", "/").toLowerCase();
        for (String ignoreFile : ignoreFiles) {
            if (matchesGlobPattern(absolutePath, ignoreFile)) {
                return false;
            }
        }
        return true;
    }

    // ===================== 编译产物查找 =====================

    private List<File> findFilesInBuildDir(String srcFilePath) {
        List<File> files = new ArrayList<>();
        srcFilePath = srcFilePath.replace("/", File.separator);
        String buildFilePath = srcFilePath;
        for (String prefix : srcIgnorePrefixes) {
            if (buildFilePath.startsWith(prefix + File.separator)) {
                buildFilePath = buildFilePath.substring(prefix.length() + 1);
                break;
            }
        }

        if (srcFilePath.toLowerCase().endsWith(".java")) {
            String classFilePath = buildFilePath.substring(0, buildFilePath.length() - 5) + ".class";
            File classFile = new File(buildDir + File.separator + classFilePath);
            if (classFile.exists()) files.add(classFile);

            String className = buildFilePath.substring(0, buildFilePath.length() - 5);
            File dir = new File(buildDir + File.separator + className.substring(0, className.lastIndexOf(File.separator)));
            if (dir.exists() && dir.isDirectory()) {
                File[] innerClassFiles = dir.listFiles();
                if (innerClassFiles != null) {
                    String baseClassName = new File(className).getName();
                    for (File innerFile : innerClassFiles) {
                        String innerFileName = innerFile.getName();
                        if (innerFileName.startsWith(baseClassName + "$") && innerFileName.endsWith(".class")) {
                            files.add(innerFile);
                        }
                    }
                }
            }
        } else if (srcFilePath.toLowerCase().endsWith(".xml")) {
            File xmlFile = new File(buildDir + File.separator + buildFilePath);
            if (xmlFile.exists()) files.add(xmlFile);
        } else if (srcFilePath.toLowerCase().endsWith(".jsp") || srcFilePath.toLowerCase().endsWith(".js")) {
            File jspFile = new File(webDir + File.separator + buildFilePath);
            if (jspFile.exists()) files.add(jspFile);
        }
        return files;
    }

    // ===================== 文件操作 =====================

    private void copyFile(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private void copyFileToDir(String sourcePath, String targetDirPath) throws IOException {
        File sourceFile = new File(sourcePath);
        String fileName = getFileNameWithExtension(sourcePath);
        File targetFile = new File(targetDirPath + File.separator + fileName);
        File targetDir = targetFile.getParentFile();
        if (targetDir != null && !targetDir.exists()) targetDir.mkdirs();
        copyFile(sourceFile, targetFile);
    }

    private void clearDirectory(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) clearDirectory(file);
                    file.delete();
                }
            }
        }
    }

    private void mkdirsFunction() {
        File dir = new File(functionSourceDir);
        if (dir.exists()) clearDirectory(dir);
        dir.mkdirs();

        dir = new File(functionCompileDir);
        if (dir.exists()) clearDirectory(dir);
        dir.mkdirs();

        dir = new File(functionBackupDir);
        if (dir.exists()) clearDirectory(dir);
        dir.mkdirs();
    }

    // ===================== 路径工具 =====================

    private String normalizePath(String path) {
        path = path.replace("/", File.separator).replace("\\", File.separator);
        while (path.contains(File.separator + File.separator)) {
            path = path.replace(File.separator + File.separator, File.separator);
        }
        return path;
    }

    private String resolvePath(String path, String basePath) {
        if (path == null || path.isEmpty()) return path;
        path = path.replace("\\", "/");
        basePath = basePath.replace("\\", "/");
        if (path.startsWith("/") || (path.length() >= 2 && path.charAt(1) == ':')) return path;
        return basePath.endsWith("/") ? basePath + path : basePath + "/" + path;
    }

    private String getRelativePath(File file) {
        String absolutePath = file.getAbsolutePath();
        String webBasePath = webDir.replace("\\", "/");
        String fileAbsolutePah = absolutePath.replace("\\", "/");
        if (fileAbsolutePah.startsWith(webBasePath)) {
            String relativePath = fileAbsolutePah.substring(webBasePath.length());
            if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                relativePath = relativePath.substring(1);
            }
            return relativePath;
        }
        return file.getName();
    }

    private String getFileName(String absolutePath) {
        String normalizedFilePath = absolutePath.replace("\\", "/");
        int lastSlashIndex = normalizedFilePath.lastIndexOf("/");
        String fileName = lastSlashIndex != -1 ? normalizedFilePath.substring(lastSlashIndex + 1) : normalizedFilePath;
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) fileName = fileName.substring(0, lastDotIndex);
        return fileName;
    }

    private String getFileNameWithExtension(String absolutePath) {
        String normalizedFilePath = absolutePath.replace("\\", "/");
        int lastSlashIndex = normalizedFilePath.lastIndexOf("/");
        return lastSlashIndex != -1 ? normalizedFilePath.substring(lastSlashIndex + 1) : normalizedFilePath;
    }

    private String getFileType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".jsp")) return "jsp";
        if (lower.endsWith(".js")) return "js";
        return "unknown";
    }

    private String getCompileFileType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".class")) return "class";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".jsp")) return "jsp";
        if (lower.endsWith(".js")) return "js";
        return "unknown";
    }

    private String getWebTargetPath(String relativePath, String fileType) {
        String targetDir = "1".equals(selectedEnvironment) ? testDir : productionDir;
        return targetDir + "/" + relativePath;
    }

    // ===================== 脚本生成 =====================

    private void generateDeployScript(List<DFile> deployFiles) {
        try (FileOutputStream fos = new FileOutputStream(deployScript)) {
            String targetDir = "1".equals(selectedEnvironment) ? testDir : productionDir;
            String envName = "1".equals(selectedEnvironment) ? "测试环境" : "生产环境";

            StringBuilder sb = new StringBuilder();
            sb.append("#!/bin/bash\n");
            sb.append("# 部署环境: ").append(envName).append("\n");
            sb.append("# 生成时间: ").append(new Date()).append("\n\n");
            sb.append("TARGET_DIR=\"").append(targetDir).append("\"\n");
            sb.append("BACKUP_SUFFIX=\"_bak_$(date +%Y%m%d%H%M%S)\"\n\n");
            sb.append("echo \"========================================\"\n");
            sb.append("echo \"  部署到: ").append(envName).append("\"\n");
            sb.append("echo \"  目标目录: $TARGET_DIR\"\n");
            sb.append("echo \"========================================\"\n\n");

            // 按 webTargetPath 去重输出，避免同一目标文件被生成重复部署块
            Set<String> emittedTargets = new HashSet<>();
            for (DFile dFile : deployFiles) {
                if (dFile.getWebTargetPath() == null
                        || !emittedTargets.add(dFile.getWebTargetPath())) {
                    continue;
                }
                appendDeployBlock(sb, dFile);
                List<DFile> innerClassFiles = dFile.getInnerClassFiles();
                if (innerClassFiles != null) {
                    for (DFile innerFile : innerClassFiles) {
                        if (innerFile.getWebTargetPath() == null
                                || !emittedTargets.add(innerFile.getWebTargetPath())) {
                            continue;
                        }
                        appendDeployBlock(sb, innerFile);
                    }
                }
            }

            sb.append("echo \"========================================\"\n");
            sb.append("echo \"部署完成!\"\n");
            sb.append("echo \"备份文件后缀: $BACKUP_SUFFIX\"\n");
            sb.append("echo \"========================================\"\n\n");

            String content = sb.toString();
            if (!content.endsWith("\n")) content += "\n";
            fos.write(content.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException("生成部署脚本失败: " + e.getMessage(), e);
        }
    }

    private void appendDeployBlock(StringBuilder sb, DFile dFile) {
        String productionPath = dFile.getWebTargetPath();
        String fileName = getFileNameWithExtension(dFile.getCompilePath());
        sb.append("# 部署文件: ").append(fileName).append("\n");
        sb.append("if [ -f '").append(productionPath).append("' ]; then\n");
        sb.append("    echo '备份文件:").append(fileName).append("'\n");
        sb.append("    cp '").append(productionPath).append("' '").append(productionPath).append("'\"$BACKUP_SUFFIX\"\n");
        sb.append("    if [ $? -eq 0 ]; then\n");
        sb.append("        echo '部署文件:").append(fileName).append("'\n");
        sb.append("        mv './").append(fileName).append("' '").append(productionPath).append("'\n");
        sb.append("    else\n");
        sb.append("        echo '备份失败，跳过部署'\n");
        sb.append("    fi\n");
        sb.append("else\n");
        sb.append("    target_dir=$(dirname '").append(productionPath).append("')\n");
        sb.append("    if [ ! -d \"$target_dir\" ]; then\n");
        sb.append("        mkdir -p \"$target_dir\"\n");
        sb.append("    fi\n");
        sb.append("    echo '新文件，直接部署:").append(fileName).append("'\n");
        sb.append("    mv './").append(fileName).append("' '").append(productionPath).append("'\n");
        sb.append("fi\n\n");
    }

    private void generateCopyScript(List<DFile> deployFiles) {
        try (FileOutputStream fos = new FileOutputStream(copyScript)) {
            String targetDir = "1".equals(selectedEnvironment) ? testDir : productionDir;
            String envName = "1".equals(selectedEnvironment) ? "测试环境" : "生产环境";

            StringBuilder sb = new StringBuilder();
            sb.append("#!/bin/bash\n");
            sb.append("CURRENT_DIR=$(pwd)\n");
            sb.append("BACKUP_SUFFIX=\"bak_$(date +%Y%m%d%H%M)\"\n");
            String localDir = "'./fileBak/'\"$BACKUP_SUFFIX\"";
            sb.append("if [ ! -d ").append(localDir).append(" ]; then\n");
            sb.append("    mkdir -p ").append(localDir).append("\n");
            sb.append("fi\n");

            // 按 webTargetPath 去重输出，避免同一目标文件被生成重复复制块
            Set<String> emittedTargets = new HashSet<>();
            for (DFile dFile : deployFiles) {
                if (dFile.getWebTargetPath() == null
                        || !emittedTargets.add(dFile.getWebTargetPath())) {
                    continue;
                }
                appendCopyBlock(sb, dFile, localDir);
                List<DFile> innerClassFiles = dFile.getInnerClassFiles();
                if (innerClassFiles != null) {
                    for (DFile innerFile : innerClassFiles) {
                        if (innerFile.getWebTargetPath() == null
                                || !emittedTargets.add(innerFile.getWebTargetPath())) {
                            continue;
                        }
                        appendCopyBlock(sb, innerFile, localDir);
                    }
                }
            }
            sb.append("zip -r ").append(localDir).append("'.zip' ").append(localDir).append("\n");

            String content = sb.toString();
            if (!content.endsWith("\n")) content += "\n";
            fos.write(content.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException("生成复制脚本失败: " + e.getMessage(), e);
        }
    }

    private void appendCopyBlock(StringBuilder sb, DFile dFile, String localDir) {
        String productionPath = dFile.getWebTargetPath();
        String fileName = getFileNameWithExtension(dFile.getCompilePath());
        sb.append("# 复制文件: ").append(fileName).append("\n");
        sb.append("if [ -f '").append(productionPath).append("' ]; then\n");
        sb.append("    cp '").append(productionPath).append("' ").append(localDir).append("'/").append(fileName).append("'\n");
        sb.append("    if [ $? -eq 0 ]; then\n");
        sb.append("        echo '复制成功: ").append(fileName).append("'\n");
        sb.append("    else\n");
        sb.append("        echo '复制失败: ").append(fileName).append("'\n");
        sb.append("    fi\n");
        sb.append("else\n");
        sb.append("    echo '文件不存在: ").append(fileName).append("'\n");
        sb.append("fi\n\n");
    }

    /**
     * 生成回滚脚本 rollback.sh。
     * <p>
     * 与 deploy.sh 一一对应：deploy.sh 在部署前用 cp 把目标文件备份成
     * {@code ${target}_bak_${时间戳}}，rollback.sh 据此恢复。
     * 支持两种模式：
     * <ul>
     *   <li>指定时间戳：{@code bash rollback.sh 20260827173350}</li>
     *   <li>自动查找最新备份：{@code bash rollback.sh}</li>
     * </ul>
     * 无备份的文件视为新文件部署，回退时直接删除。
     * <p>
     * 文件列表按 webTargetPath 去重输出，与 deploy.sh 保持一致。
     */
    private void generateRollbackScript(List<DFile> deployFiles) {
        try (FileOutputStream fos = new FileOutputStream(rollbackScript)) {
            String targetDir = "1".equals(selectedEnvironment) ? testDir : productionDir;
            String envName = "1".equals(selectedEnvironment) ? "测试环境" : "生产环境";

            StringBuilder sb = new StringBuilder();
            sb.append("#!/bin/bash\n");
            sb.append("# 回退脚本: ").append(envName).append("\n");
            sb.append("# 对应 deploy.sh 的回退操作\n");
            sb.append("# 用法: 在 deploy.sh 同目录下执行 bash rollback.sh\n");
            sb.append("#   或指定备份时间戳: bash rollback.sh 20260827173350\n\n");
            sb.append("TARGET_DIR=\"").append(targetDir).append("\"\n\n");
            sb.append("# 如果传入时间戳参数，使用指定时间戳；否则自动查找最新的备份文件\n");
            sb.append("BACKUP_TS=\"$1\"\n\n");
            sb.append("echo \"========================================\"\n");
            sb.append("echo \"  回退: ").append(envName).append("\"\n");
            sb.append("echo \"  目标目录: $TARGET_DIR\"\n");
            sb.append("if [ -n \"$BACKUP_TS\" ]; then\n");
            sb.append("    echo \"  指定备份时间戳: $BACKUP_TS\"\n");
            sb.append("else\n");
            sb.append("    echo \"  自动查找最新备份\"\n");
            sb.append("fi\n");
            sb.append("echo \"========================================\"\n\n");

            // 回退函数：查找备份文件并恢复，找不到备份则删除（新文件部署的情况）
            sb.append("rollback_file() {\n");
            sb.append("    local target_path=\"$1\"\n");
            sb.append("    local file_name\n");
            sb.append("    file_name=$(basename \"$target_path\")\n\n");
            sb.append("    if [ -n \"$BACKUP_TS\" ]; then\n");
            sb.append("        # 指定时间戳模式\n");
            sb.append("        local backup_path=\"${target_path}_bak_${BACKUP_TS}\"\n");
            sb.append("        if [ -f \"$backup_path\" ]; then\n");
            sb.append("            echo \"回退文件(指定备份): $file_name\"\n");
            sb.append("            cp \"$backup_path\" \"$target_path\"\n");
            sb.append("            if [ $? -eq 0 ]; then\n");
            sb.append("                echo \"  -> 回退成功\"\n");
            sb.append("            else\n");
            sb.append("                echo \"  -> 回退失败!\"\n");
            sb.append("            fi\n");
            sb.append("        else\n");
            sb.append("            echo \"警告: 备份文件不存在: $file_name (可能是新文件部署)\"\n");
            sb.append("            echo \"  -> 删除新部署的文件\"\n");
            sb.append("            rm -f \"$target_path\"\n");
            sb.append("        fi\n");
            sb.append("    else\n");
            sb.append("        # 自动查找最新备份模式\n");
            sb.append("        local backup_path\n");
            sb.append("        backup_path=$(ls -t \"${target_path}_bak_\"* 2>/dev/null | head -1)\n");
            sb.append("        if [ -n \"$backup_path\" ]; then\n");
            sb.append("            echo \"回退文件(自动备份): $file_name <- $(basename \"$backup_path\")\"\n");
            sb.append("            cp \"$backup_path\" \"$target_path\"\n");
            sb.append("            if [ $? -eq 0 ]; then\n");
            sb.append("                echo \"  -> 回退成功\"\n");
            sb.append("            else\n");
            sb.append("                echo \"  -> 回退失败!\"\n");
            sb.append("            fi\n");
            sb.append("        else\n");
            sb.append("            echo \"警告: 无备份文件: $file_name (可能是新文件部署)\"\n");
            sb.append("            echo \"  -> 删除新部署的文件\"\n");
            sb.append("            rm -f \"$target_path\"\n");
            sb.append("        fi\n");
            sb.append("    fi\n");
            sb.append("}\n\n");

            // 文件清单，按 webTargetPath 去重，与 deploy.sh 输出保持一致
            Set<String> emittedTargets = new HashSet<>();
            int index = 0;
            for (DFile dFile : deployFiles) {
                if (dFile.getWebTargetPath() == null
                        || !emittedTargets.add(dFile.getWebTargetPath())) {
                    continue;
                }
                appendRollbackEntry(sb, ++index, dFile);
                List<DFile> innerClassFiles = dFile.getInnerClassFiles();
                if (innerClassFiles != null) {
                    for (DFile innerFile : innerClassFiles) {
                        if (innerFile.getWebTargetPath() == null
                                || !emittedTargets.add(innerFile.getWebTargetPath())) {
                            continue;
                        }
                        appendRollbackEntry(sb, ++index, innerFile);
                    }
                }
            }

            sb.append("\necho \"========================================\"\n");
            sb.append("echo \"回退完成!\"\n");
            sb.append("echo \"========================================\"\n");

            String content = sb.toString();
            if (!content.endsWith("\n")) content += "\n";
            fos.write(content.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException("生成回滚脚本失败: " + e.getMessage(), e);
        }
    }

    /**
     * 追加单条回滚条目：注释行 + rollback_file 调用。
     * <p>
     * 路径中的 {@code $}（内部类名如 {@code Foo$1.class}）必须转义为 {@code \$}，
     * 否则双引号内 {@code $1} 会被 Bash 当作脚本第一个参数展开。
     */
    private void appendRollbackEntry(StringBuilder sb, int index, DFile dFile) {
        String productionPath = dFile.getWebTargetPath();
        String fileName = getFileNameWithExtension(dFile.getCompilePath());
        // 转义 $ 为 \$，避免双引号内 $N 被解释为位置参数
        String escapedPath = productionPath.replace("$", "\\$");
        sb.append("# ===== ").append(index).append(". ").append(fileName).append(" =====\n");
        sb.append("rollback_file \"").append(escapedPath).append("\"\n\n");
    }

    // ===================== Getter（供 Dialog 展示） =====================

    public String getSrcDir() { return srcDir; }
    public String getDeployDir() { return deployDir; }
}
