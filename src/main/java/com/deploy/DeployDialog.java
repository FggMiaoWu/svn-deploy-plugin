package com.deploy;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 部署图形界面（替代原 System.in 控制台交互）。
 * <p>
 * 流程：选环境 → 选获取方式 → 输入版本号 → 勾选文件 → 确认部署。
 * <p>
 * 文件列表用 CheckboxTree 按包/目录路径分组展示，可折叠展开，父子节点联动勾选。
 * <p>
 * 多项目支持：每个项目使用 {项目根}/svn-deploy/deploy.properties 独立配置，
 * 首次使用时自动从内置模板创建，可通过「编辑配置」按钮直接打开修改。
 */
public class DeployDialog extends DialogWrapper {

    private final Project project;
    private final DeployService service;

    // 环境选择
    private JRadioButton rbTest;
    private JRadioButton rbProduction;

    // 获取方式
    private JRadioButton rbLocal;
    private JRadioButton rbSvnHistory;

    // 版本号输入
    private JTextField tfRevisions;
    // 最近提交记录表格（复选框列 + 版本号/作者/提交信息，勾选后自动填入版本号）
    private JTable tableRecentRevisions;
    private DefaultTableModel tableModel;
    // 已加载的最旧版本号（纯数字），用于「加载更多」分页；为 0 表示尚未加载
    private int oldestLoadedRevision = 0;
    // 「加载更多」按钮（无更多记录时禁用）
    private JButton btnLoadMore;

    // 文件勾选树（按包路径分组，可折叠展开）
    private CheckboxTree fileTree;
    private JCheckBox cbSelectAll;

    // 排除模式
    private JCheckBox cbExcludeMode;

    // 获取到的修改文件（保持顺序，用于回查索引）
    private List<DFile> modifiedFiles = new ArrayList<>();

    public DeployDialog(Project project, DeployService service) {
        super(project);
        this.project = project;
        this.service = service;
        setTitle("SVN 部署");
        init();

        // 首次自动创建配置时提示用户编辑
        if (service.isConfigJustCreated()) {
            SwingUtilities.invokeLater(() ->
                    Messages.showInfoMessage(project,
                            "已为当前项目自动创建配置文件：\n"
                                    + service.getConfigPath() + "\n\n"
                                    + "请按当前项目实际情况修改路径（src.dir / build.dir / test.dir 等），\n"
                                    + "可点击对话框下方「编辑配置」按钮直接打开修改。",
                            "首次使用 - 已创建项目级配置"));
        }
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ---- 部署环境 ----
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JBLabel("部署环境:"), gbc);
        gbc.gridx = 1;
        rbTest = new JRadioButton("测试环境", true);
        rbProduction = new JRadioButton("生产环境");
        ButtonGroup envGroup = new ButtonGroup();
        envGroup.add(rbTest);
        envGroup.add(rbProduction);
        JPanel envPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        envPanel.add(rbTest);
        envPanel.add(Box.createHorizontalStrut(16));
        envPanel.add(rbProduction);
        panel.add(envPanel, gbc);

        // ---- 获取方式 ----
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JBLabel("获取方式:"), gbc);
        gbc.gridx = 1;
        rbLocal = new JRadioButton("本地修改", true);
        rbSvnHistory = new JRadioButton("SVN 历史");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(rbLocal);
        modeGroup.add(rbSvnHistory);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modePanel.add(rbLocal);
        modePanel.add(Box.createHorizontalStrut(16));
        modePanel.add(rbSvnHistory);
        panel.add(modePanel, gbc);

        // ---- 版本号输入 ----
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JBLabel("SVN 版本号:"), gbc);
        gbc.gridx = 1;
        tfRevisions = new JTextField(30);
        tfRevisions.setToolTipText("多个版本用逗号分隔，如: r1562,r1565。下方表格多选后会自动填入");
        panel.add(tfRevisions, gbc);

        // ---- 获取提交记录按钮 + 加载更多按钮 ----
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JButton btnFetchLogs = new JButton("获取 SVN 提交记录");
        btnFetchLogs.setToolTipText("异步加载最近 5 条提交记录到下方表格，勾选后自动填入版本号");
        btnFetchLogs.addActionListener(e -> {
            // 重新获取：清空已有数据和分页状态
            tableModel.setRowCount(0);
            oldestLoadedRevision = 0;
            btnLoadMore.setEnabled(false);
            loadRecentRevisions();
        });
        btnLoadMore = new JButton("加载更多");
        btnLoadMore.setToolTipText("往前翻页加载 5 条更早的提交记录");
        btnLoadMore.setEnabled(false);
        btnLoadMore.addActionListener(e -> loadMoreRevisions());
        JPanel logBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logBtnPanel.add(btnFetchLogs);
        logBtnPanel.add(Box.createHorizontalStrut(8));
        logBtnPanel.add(btnLoadMore);
        panel.add(logBtnPanel, gbc);

        // ---- 提交记录表格（复选框 / 版本号 / 作者 / 提交信息） ----
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        // 第一列 Boolean 类型，JTable 默认用 JCheckBox 渲染和编辑；其余列不可编辑
        tableModel = new DefaultTableModel(new Object[]{"选", "版本号", "作者", "提交信息"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        tableRecentRevisions = new JTable(tableModel);
        tableRecentRevisions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableRecentRevisions.setRowHeight(24);
        tableRecentRevisions.getColumnModel().getColumn(0).setPreferredWidth(40);
        tableRecentRevisions.getColumnModel().getColumn(1).setPreferredWidth(70);
        tableRecentRevisions.getColumnModel().getColumn(2).setPreferredWidth(100);
        tableRecentRevisions.getColumnModel().getColumn(3).setPreferredWidth(390);
        // 复选框勾选变化时，把所有勾选行的版本号同步到版本号输入框（逗号分隔）
        tableModel.addTableModelListener(e -> {
            if (e.getColumn() != 0) return;
            syncSelectedRevisionsToTextField();
        });
        JBScrollPane tableScroll = new JBScrollPane(tableRecentRevisions);
        tableScroll.setPreferredSize(new Dimension(600, 120));
        panel.add(tableScroll, gbc);

        // ---- 获取文件按钮 ----
        gbc.gridx = 1; gbc.gridy = 5; gbc.gridwidth = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JButton btnFetch = new JButton("获取修改文件");
        btnFetch.addActionListener(e -> fetchFiles());
        panel.add(btnFetch, gbc);

        // ---- 文件勾选树（按包路径分组） ----
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        // 空树根节点，后续 fetchFiles 时填充
        CheckedTreeNode treeRoot = new CheckedTreeNode("文件列表");
        // 必须传入具体渲染器，传 null 会导致复选框不显示
        fileTree = new CheckboxTree(new FileCheckboxRenderer(), treeRoot);
        fileTree.setRootVisible(false);
        fileTree.setShowsRootHandles(true);
        // 初始空提示：设置一个占位节点
        JBScrollPane scrollPane = new JBScrollPane(fileTree);
        scrollPane.setPreferredSize(new Dimension(600, 280));
        panel.add(scrollPane, gbc);

        // ---- 全选 / 排除模式 / 编辑配置 ----
        gbc.gridy = 7; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel optPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cbSelectAll = new JCheckBox("全选");
        cbSelectAll.addActionListener(e -> toggleSelectAll(cbSelectAll.isSelected()));
        cbExcludeMode = new JCheckBox("排除模式（勾选的文件将被排除）");
        optPanel.add(cbSelectAll);
        optPanel.add(Box.createHorizontalStrut(16));
        optPanel.add(cbExcludeMode);
        // 编辑配置按钮：打开当前项目的 svn-deploy/deploy.properties
        JButton btnEditConfig = new JButton("编辑配置");
        btnEditConfig.setToolTipText("在 IDEA 编辑器中打开当前项目配置文件，修改后保存即生效");
        btnEditConfig.addActionListener(e -> openConfigFile());
        optPanel.add(Box.createHorizontalStrut(16));
        optPanel.add(btnEditConfig);
        panel.add(optPanel, gbc);

        // ---- 配置文件路径提示 ----
        gbc.gridy = 8;
        JBLabel configPathLabel = new JBLabel("配置: " + service.getConfigPath());
        configPathLabel.setFont(configPathLabel.getFont().deriveFont(11f));
        configPathLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(configPathLabel, gbc);

        return panel;
    }

    /**
     * 加载最近 5 条 SVN 提交记录到表格（首页）。
     * <p>
     * 后台线程执行 svn log（避免阻塞 EDT），完成后用 SwingUtilities.invokeLater 切回 EDT 更新表格。
     * 加载成功后启用「加载更多」按钮；失败时在表格内显示错误信息。
     */
    private void loadRecentRevisions() {
        // 临时禁用并显示加载提示
        tableRecentRevisions.setEnabled(false);
        btnLoadMore.setEnabled(false);
        tableModel.setRowCount(0);
        tableModel.addRow(new Object[]{false, "", "", "加载中..."});

        // 后台线程执行 svn log（避免阻塞 EDT）
        new Thread(() -> {
            List<DeployService.SvnLogEntry> logs;
            try {
                logs = service.getRecentSvnLogs(5);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    tableModel.addRow(new Object[]{false, "", "", "获取失败: " + ex.getMessage()});
                    tableRecentRevisions.setEnabled(false);
                    btnLoadMore.setEnabled(false);
                });
                return;
            }
            final List<DeployService.SvnLogEntry> finalLogs = logs;
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                if (finalLogs.isEmpty()) {
                    tableModel.addRow(new Object[]{false, "", "", "无法获取提交记录（非 SVN 项目或网络问题）"});
                    tableRecentRevisions.setEnabled(false);
                    btnLoadMore.setEnabled(false);
                } else {
                    appendLogRows(finalLogs);
                    tableRecentRevisions.setEnabled(true);
                    // 不足 5 条说明已到最早版本，禁用「加载更多」
                    btnLoadMore.setEnabled(finalLogs.size() >= 5);
                }
            });
        }, "svn-log-loader").start();
    }

    /**
     * 加载更多：以当前已加载的最旧版本号往前翻页 5 条。
     * <p>
     * 用 {@link DeployService#getRecentSvnLogs(int, String)} 的分页重载，
     * 传入 oldestLoadedRevision - 1 作为起始版本号，查 startRev:1 范围的前 5 条。
     */
    private void loadMoreRevisions() {
        if (oldestLoadedRevision <= 1) {
            btnLoadMore.setEnabled(false);
            return;
        }
        String startRev = String.valueOf(oldestLoadedRevision - 1);
        btnLoadMore.setEnabled(false);
        btnLoadMore.setText("加载中...");

        new Thread(() -> {
            List<DeployService.SvnLogEntry> logs;
            try {
                logs = service.getRecentSvnLogs(5, startRev);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnLoadMore.setText("加载更多");
                    btnLoadMore.setEnabled(true);
                    Messages.showWarningDialog(project, "加载更多失败: " + ex.getMessage(), "提示");
                });
                return;
            }
            final List<DeployService.SvnLogEntry> finalLogs = logs;
            SwingUtilities.invokeLater(() -> {
                btnLoadMore.setText("加载更多");
                if (finalLogs.isEmpty()) {
                    // 已到最早版本，无更多记录
                    btnLoadMore.setEnabled(false);
                } else {
                    appendLogRows(finalLogs);
                    // 不足 5 条说明已到最早版本
                    btnLoadMore.setEnabled(finalLogs.size() >= 5);
                }
            });
        }, "svn-log-more-loader").start();
    }

    /**
     * 把新加载的提交记录追加到表格，并更新 oldestLoadedRevision。
     * 注意：追加时暂停 TableModelListener，避免每行触发版本号同步。
     */
    private void appendLogRows(List<DeployService.SvnLogEntry> logs) {
        for (DeployService.SvnLogEntry entry : logs) {
            tableModel.addRow(new Object[]{
                    false,
                    entry.getRevision(),
                    entry.getAuthor(),
                    entry.getMessage()
            });
            // 更新最旧版本号
            int revNum = parseRevisionNumber(entry.getRevision());
            if (revNum > 0 && (oldestLoadedRevision == 0 || revNum < oldestLoadedRevision)) {
                oldestLoadedRevision = revNum;
            }
        }
    }

    /** 从 "r1854" 格式解析出版本号数字 1854；解析失败返回 0 */
    private static int parseRevisionNumber(String revision) {
        if (revision == null) return 0;
        String num = revision.startsWith("r") ? revision.substring(1) : revision;
        try {
            return Integer.parseInt(num.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 把表格中所有勾选行的版本号用逗号拼接，同步到版本号输入框 */
    private void syncSelectedRevisionsToTextField() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean checked = (Boolean) tableModel.getValueAt(i, 0);
            if (checked != null && checked) {
                String rev = (String) tableModel.getValueAt(i, 1);
                if (sb.length() > 0) sb.append(",");
                sb.append(rev);
            }
        }
        tfRevisions.setText(sb.toString());
    }

    /** 在 IDEA 编辑器中打开当前项目配置文件 */
    private void openConfigFile() {
        String path = service.getConfigPath();
        VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
        if (vf == null) {
            Messages.showWarningDialog(project, "配置文件不存在: " + path, "提示");
            return;
        }
        // 刷新文件系统，确保读取最新内容
        vf.refresh(false, false);
        new OpenFileDescriptor(project, vf).navigate(true);
    }

    /** 获取修改文件并按包路径分组填充勾选树 */
    private void fetchFiles() {
        DeployRequest req = buildRequestForFetch();
        if (req == null) return;

        try {
            modifiedFiles = service.getModifiedFiles(req);
            if (modifiedFiles.isEmpty()) {
                Messages.showInfoMessage(project, "未找到修改的文件", "提示");
                rebuildTree(new LinkedHashMap<>());
                return;
            }

            // 按包/目录路径分组，保持插入顺序（用 LinkedHashMap）
            Map<String, List<DFile>> grouped = new LinkedHashMap<>();
            String srcDir = service.getSrcDir();
            for (DFile dFile : modifiedFiles) {
                String pkg = extractPackagePath(dFile.getSourcePath(), srcDir);
                grouped.computeIfAbsent(pkg, k -> new ArrayList<>()).add(dFile);
            }
            rebuildTree(grouped);
        } catch (Exception e) {
            Messages.showErrorDialog(project, "获取文件失败: " + e.getMessage(), "错误");
        }
    }

    /**
     * 从源码路径提取包/目录路径用于分组展示。
     * 例如 srcDir=src/main, sourcePath=src/main/java/com/ideal/epi/entity/CallInfo.java
     * → 返回 java.com.ideal.epi.entity
     * 文件直接在 srcDir 下时返回 "(根目录)"。
     */
    private String extractPackagePath(String sourcePath, String srcDir) {
        String relative = sourcePath;
        // 去掉 srcDir 前缀
        if (srcDir != null && !srcDir.isEmpty() && sourcePath.startsWith(srcDir + "/")) {
            relative = sourcePath.substring(srcDir.length() + 1);
        }
        // 去掉文件名，保留目录部分
        int lastSlash = relative.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "(根目录)";
        }
        relative = relative.substring(0, lastSlash);
        // / 换成 . 便于阅读（形如 java.com.ideal.epi.entity）
        return relative.replace('/', '.');
    }

    /** 根据分组数据重建勾选树 */
    private void rebuildTree(Map<String, List<DFile>> grouped) {
        CheckedTreeNode root = new CheckedTreeNode("文件列表");
        for (Map.Entry<String, List<DFile>> entry : grouped.entrySet()) {
            // 目录节点：显示 "包路径 (文件数)"
            String dirLabel = entry.getKey() + "  (" + entry.getValue().size() + ")";
            CheckedTreeNode dirNode = new CheckedTreeNode(dirLabel);
            for (DFile dFile : entry.getValue()) {
                // 文件节点：userObject 为 DFile，toString() 返回 "文件名.扩展名"
                CheckedTreeNode fileNode = new CheckedTreeNode(dFile);
                dirNode.add(fileNode);
            }
            root.add(dirNode);
        }
        fileTree.setModel(new DefaultTreeModel(root));
        // 默认展开所有目录节点
        expandAllDirectoryNodes(root);
    }

    /** 展开所有目录节点（第一层子节点） */
    private void expandAllDirectoryNodes(TreeNode root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            TreeNode child = root.getChildAt(i);
            if (child.getChildCount() > 0) {
                fileTree.expandPath(new TreePath(new Object[]{root, child}));
            }
        }
    }

    /** 全选 / 取消全选（通过根节点联动所有子节点） */
    private void toggleSelectAll(boolean selected) {
        TreeNode root = (TreeNode) fileTree.getModel().getRoot();
        if (root instanceof CheckedTreeNode) {
            fileTree.setNodeState((CheckedTreeNode) root, selected);
        }
    }

    /** 构建用于「获取文件」阶段的请求 */
    private DeployRequest buildRequestForFetch() {
        DeployRequest req = new DeployRequest();
        req.setProjectRoot(project.getBasePath());
        req.setEnvironment(rbTest.isSelected() ? "1" : "2");
        req.setMode(rbLocal.isSelected() ? "1" : "2");

        if (rbSvnHistory.isSelected()) {
            String revText = tfRevisions.getText().trim();
            if (revText.isEmpty()) {
                Messages.showWarningDialog(project, "请输入 SVN 版本号", "提示");
                return null;
            }
            List<String> revisions = new ArrayList<>();
            for (String rev : revText.split(",")) {
                rev = rev.trim();
                if (!rev.isEmpty()) revisions.add(rev);
            }
            req.setRevisions(revisions);
        }
        return req;
    }

    /** 确认时构建完整部署请求 */
    @Override
    protected void doOKAction() {
        if (modifiedFiles.isEmpty()) {
            Messages.showWarningDialog(project, "请先获取修改文件", "提示");
            return;
        }

        DeployRequest req = buildRequestForFetch();
        if (req == null) return;

        // 收集勾选的文件（getCheckedNodes 返回所有 userObject 为 DFile 类型且被勾选的节点）
        DFile[] checked = fileTree.getCheckedNodes(DFile.class, null);
        Set<DFile> checkedSet = new HashSet<>(Arrays.asList(checked));

        // 回查在 modifiedFiles 中的索引（保持原有 selectFiles 逻辑兼容）
        List<Integer> selectedIndices = new ArrayList<>();
        for (int i = 0; i < modifiedFiles.size(); i++) {
            if (checkedSet.contains(modifiedFiles.get(i))) {
                selectedIndices.add(i);
            }
        }

        if (cbSelectAll.isSelected() || selectedIndices.size() == modifiedFiles.size()) {
            req.setSelectAll(true);
        } else {
            if (selectedIndices.isEmpty()) {
                Messages.showWarningDialog(project, "请至少选择一个文件", "提示");
                return;
            }
            req.setExcludeMode(cbExcludeMode.isSelected());
            req.setSelectedIndices(selectedIndices);
        }

        // 执行部署
        try {
            List<DFile> selected = service.selectFiles(modifiedFiles, req);
            DeployService.DeployResult result = service.deploy(selected, req);
            String envName = "1".equals(req.getEnvironment()) ? "测试环境" : "生产环境";
            Messages.showInfoMessage(project,
                    String.format("部署完成（%s）\n成功: %d\n失败: %d\n\n部署目录: %s",
                            envName, result.successCount, result.failCount, service.getDeployDir()),
                    "部署结果");
        } catch (Exception e) {
            Messages.showErrorDialog(project, "部署失败: " + e.getMessage(), "错误");
        }
        super.doOKAction();
    }

    /**
     * 勾选树渲染器。
     * 必须实现 customizeRenderer，否则传 null 给 CheckboxTree 构造函数会导致复选框不显示。
     * - 目录节点（String）：用灰色普通样式显示 "包路径 (文件数)"
     * - 文件节点（DFile）：用默认样式显示 "文件名.扩展名"
     */
    private static class FileCheckboxRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
        @Override
        public void customizeRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
            Object userObject = ((CheckedTreeNode) value).getUserObject();
            if (userObject instanceof DFile) {
                // 文件节点：显示文件名.扩展名（DFile.toString() 返回该格式）
                getTextRenderer().append(userObject.toString());
            } else if (userObject != null) {
                // 目录节点：包路径 (文件数)，用稍灰的样式区分
                getTextRenderer().append(userObject.toString(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }
}
