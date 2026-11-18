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
import com.intellij.openapi.wm.WindowManager;
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
    // 当前查询的 limit（首页 5，每次「加载更多」+5）；为 0 表示尚未加载
    private int currentLimit = 0;
    // 「加载更多」按钮（无更多记录时禁用）
    private JButton btnLoadMore;
    // SVN 历史相关 UI 容器（版本号输入 + 获取按钮 + 记录表格），仅在选择 SVN 历史时显示
    private JPanel svnHistoryPanel;

    // 文件勾选树（按包路径分组，可折叠展开）
    private CheckboxTree fileTree;
    private JCheckBox cbSelectAll;

    // 排除模式
    private JCheckBox cbExcludeMode;

    // 上线申请表：主界面仅保留生成开关，勾选后弹窗填写功能名/上线时间/上线内容描述
    private JCheckBox cbGenForm;
    // 弹窗确认后填写的申请表字段（均非必填）
    private String formFunctionName;
    private String formOnlineTime;
    private String formRequirementDesc;
    // 程序化修改勾选框状态时置为 true，避免再次触发弹窗
    private boolean suppressGenFormEvent;

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
        // 切换获取方式时，按需显示/隐藏 SVN 历史 UI
        rbLocal.addActionListener(e -> updateSvnHistoryVisibility());
        rbSvnHistory.addActionListener(e -> updateSvnHistoryVisibility());
        panel.add(modePanel, gbc);

        // ---- SVN 历史区域（版本号输入 + 获取按钮 + 记录表格），按需显示 ----
        // 打包进独立 JPanel，便于整体切换可见性
        svnHistoryPanel = new JPanel(new GridBagLayout());
        GridBagConstraints hgbc = new GridBagConstraints();
        hgbc.insets = new Insets(4, 0, 4, 0);
        hgbc.anchor = GridBagConstraints.WEST;
        hgbc.fill = GridBagConstraints.HORIZONTAL;

        // 版本号输入
        hgbc.gridx = 0; hgbc.gridy = 0;
        svnHistoryPanel.add(new JBLabel("SVN 版本号:"), hgbc);
        hgbc.gridx = 1;
        tfRevisions = new JTextField(30);
        tfRevisions.setToolTipText("多个版本用逗号分隔，如: r1562,r1565。下方表格多选后会自动填入");
        svnHistoryPanel.add(tfRevisions, hgbc);

        // 获取提交记录按钮 + 加载更多按钮
        hgbc.gridx = 0; hgbc.gridy = 1; hgbc.gridwidth = 2;
        JButton btnFetchLogs = new JButton("获取 SVN 提交记录");
        btnFetchLogs.setToolTipText("异步加载最近 5 条提交记录到下方表格，勾选后自动填入版本号");
        btnFetchLogs.addActionListener(e -> {
            // 重新获取：清空已有数据和分页状态
            tableModel.setRowCount(0);
            currentLimit = 0;
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
        svnHistoryPanel.add(logBtnPanel, hgbc);

        // 提交记录表格（复选框 / 版本号 / 作者 / 提交信息）
        hgbc.gridx = 0; hgbc.gridy = 2; hgbc.gridwidth = 2;
        hgbc.weightx = 1.0; hgbc.weighty = 0.3;
        hgbc.fill = GridBagConstraints.BOTH;
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
        // 高度需容纳表头 + 5 行（每行 24px）+ 滚动条余量，约 180
        tableScroll.setPreferredSize(new Dimension(600, 180));
        svnHistoryPanel.add(tableScroll, hgbc);

        // 将 SVN 历史面板加入主面板，占两列
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(svnHistoryPanel, gbc);

        // ---- 获取文件按钮 ----
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JButton btnFetch = new JButton("获取修改文件");
        btnFetch.addActionListener(e -> fetchFiles());
        panel.add(btnFetch, gbc);

        // ---- 文件勾选树（按包路径分组） ----
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
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

        // ---- 上线申请表（可选，勾选后弹窗填写，部署后用于生成上线单） ----
        gbc.gridx = 0; gbc.gridy = 5; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        formPanel.setBorder(BorderFactory.createTitledBorder("上线申请表（可选生成）"));
        cbGenForm = new JCheckBox("生成上线申请表", false);
        cbGenForm.setToolTipText("勾选后弹出窗口填写功能名称、上线时间、上线内容描述；取消勾选则不生成（如发布测试环境）");
        // 勾选时立即弹出申请表信息窗口；取消弹窗则视为跳过申请表，继续部署
        cbGenForm.addActionListener(e -> {
            if (suppressGenFormEvent) return;
            if (cbGenForm.isSelected() && !showUploadFormDialog()) {
                setGenFormSelected(false);
            }
        });
        formPanel.add(cbGenForm);
        panel.add(formPanel, gbc);

        // ---- 全选 / 排除模式 / 编辑配置 ----
        gbc.gridy = 6; gbc.weighty = 0;
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
        gbc.gridy = 7;
        JBLabel configPathLabel = new JBLabel("配置: " + service.getConfigPath());
        configPathLabel.setFont(configPathLabel.getFont().deriveFont(11f));
        configPathLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(configPathLabel, gbc);

        // 初始状态：默认选中「本地修改」，隐藏 SVN 历史 UI
        updateSvnHistoryVisibility();

        return panel;
    }

    /**
     * 根据获取方式单选切换 SVN 历史 UI 的可见性。
     * 选中「SVN 历史」时显示版本号输入框、获取按钮和记录表格；
     * 选中「本地修改」时隐藏并清空已选版本号，避免遗留数据误用。
     * <p>
     * 切换后调用 pack() 重新计算对话框尺寸：显示时撑开避免表格被压缩，
     * 隐藏时收缩避免留白。
     */
    private void updateSvnHistoryVisibility() {
        boolean svnMode = rbSvnHistory.isSelected();
        svnHistoryPanel.setVisible(svnMode);
        if (!svnMode) {
            // 切回本地修改时清空已选版本号和表格，重置分页状态
            tfRevisions.setText("");
            tableModel.setRowCount(0);
            currentLimit = 0;
            btnLoadMore.setEnabled(false);
        }
        // 重新布局并重新 pack 对话框，确保高度自适应
        svnHistoryPanel.revalidate();
        pack();
    }

    /**
     * 加载最近 5 条 SVN 提交记录到表格（首页）。
     * <p>
     * 后台线程执行 svn log -l 5（避免阻塞 EDT），完成后切回 EDT 更新表格。
     * 加载成功后启用「加载更多」按钮；失败时在表格内显示错误信息。
     */
    private void loadRecentRevisions() {
        currentLimit = 5;
        loadRevisionsInternal();
    }

    /**
     * 加载更多：limit 在当前基础上 +5 重新查询，清空表格后重新加载全部记录。
     * <p>
     * 例如首页 svn log -l 5，加载更多执行 svn log -l 10，清空表格后填入 10 条。
     */
    private void loadMoreRevisions() {
        currentLimit += 5;
        loadRevisionsInternal();
    }

    /**
     * 实际执行 svn log -l currentLimit 并用结果重建表格。
     * <p>
     * 后台线程执行（避免阻塞 EDT），完成后切回 EDT 更新表格。
     * 返回条数少于 currentLimit 说明已到最早版本，禁用「加载更多」。
     */
    private void loadRevisionsInternal() {
        final int limit = currentLimit;
        // 临时禁用并显示加载提示
        tableRecentRevisions.setEnabled(false);
        btnLoadMore.setEnabled(false);
        tableModel.setRowCount(0);
        tableModel.addRow(new Object[]{false, "", "", "加载中..."});

        // 后台线程执行 svn log（避免阻塞 EDT）
        new Thread(() -> {
            List<DeployService.SvnLogEntry> logs;
            try {
                logs = service.getRecentSvnLogs(limit);
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
                    for (DeployService.SvnLogEntry entry : finalLogs) {
                        tableModel.addRow(new Object[]{
                                false,
                                entry.getRevision(),
                                entry.getAuthor(),
                                entry.getMessage()
                        });
                    }
                    tableRecentRevisions.setEnabled(true);
                    // 返回条数少于 limit 说明已到最早版本，禁用「加载更多」
                    btnLoadMore.setEnabled(finalLogs.size() >= limit);
                }
            });
        }, "svn-log-loader").start();
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

    /**
     * 弹出上线申请表信息填写窗口。
     * 确认后把功能名称/上线时间/上线内容描述保存到字段，返回 true；
     * 取消则返回 false（调用方取消勾选，跳过申请表但继续部署）。
     */
    private boolean showUploadFormDialog() {
        UploadFormDialog dialog = new UploadFormDialog();
        if (!dialog.showAndGet()) return false;
        formFunctionName = dialog.getFunctionName();
        formOnlineTime = dialog.getOnlineTime();
        formRequirementDesc = dialog.getRequirementDesc();
        return true;
    }

    /** 程序化设置生成勾选框状态（不触发弹窗） */
    private void setGenFormSelected(boolean selected) {
        suppressGenFormEvent = true;
        try {
            cbGenForm.setSelected(selected);
        } finally {
            suppressGenFormEvent = false;
        }
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

        // 注入上线申请表参数：决定是否生成申请表 & 是否做需求目录归档
        // 字段值来自勾选时弹出的填写窗口，未勾选/取消弹窗均为空
        boolean genForm = cbGenForm != null && cbGenForm.isSelected();
        req.setFunctionName(formFunctionName);
        req.setOnlineTime(formOnlineTime);
        req.setGenerateForm(genForm);

        // 执行部署
        try {
            List<DFile> selected = service.selectFiles(modifiedFiles, req);
            DeployService.DeployResult result = service.deploy(selected, req);
            String envName = "1".equals(req.getEnvironment()) ? "测试环境" : "生产环境";

            // 部署后生成上线申请表（可勾选关闭；功能名/描述/上线时间非必填）
            String formPath = null;
            if (genForm && service.isUploadFormEnabled()) {
                try {
                    formPath = service.generateUploadForm(result,
                            formFunctionName,
                            formRequirementDesc,
                            formOnlineTime);
                } catch (Exception fe) {
                    Messages.showWarningDialog(project, "生成上线申请表失败（不影响部署）：\n" + fe.getMessage(), "提示");
                }
            }

            showDeployResult(envName, result, formPath);
        } catch (Exception e) {
            Messages.showErrorDialog(project, "部署失败: " + e.getMessage(), "错误");
        }
        super.doOKAction();
    }

    /**
     * 展示部署结果：成功/失败数、部署目录、源文件清单、结果文件清单及分类统计。
     * 用带滚动条的文本区展示，支持长清单。
     */
    private void showDeployResult(String envName, DeployService.DeployResult result, String formPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("部署完成（").append(envName).append("）\n");
        sb.append("成功: ").append(result.successCount)
          .append("  失败: ").append(result.failCount).append("\n\n");
        sb.append("部署目录: ").append(service.getDeployDir()).append("\n");
        sb.append("生成脚本: deploy.sh / copy.sh / rollback.sh（回滚用法见 rollback.sh 头部）\n\n");

        // 上线申请表生成路径
        if (formPath != null) {
            sb.append("已生成上线申请表: ").append(formPath).append("\n\n");
        }

        // 总统计行（java/class 来自源与结果，jsp/xml 源与结果一致只计源）
        sb.append("统计: ");
        List<String> stats = new ArrayList<>();
        if (result.sourceJavaCount > 0) stats.add(result.sourceJavaCount + "个java文件");
        if (result.resultClassCount > 0) stats.add(result.resultClassCount + "个class文件");
        if (result.sourceJspCount > 0) stats.add(result.sourceJspCount + "个jsp文件");
        if (result.sourceXmlCount > 0) stats.add(result.sourceXmlCount + "个xml文件");
        sb.append(String.join("，", stats)).append("\n\n");

        // 源文件清单
        sb.append("===== 源文件清单 (共 ").append(result.sourceFileNames.size()).append(" 个) =====\n");
        for (String name : result.sourceFileNames) {
            sb.append(name).append("\n");
        }

        // 结果文件清单
        sb.append("\n===== 结果文件清单 (共 ").append(result.resultFileNames.size()).append(" 个) =====\n");
        for (String name : result.resultFileNames) {
            sb.append(name).append("\n");
        }

        // 用带滚动条的文本区展示，避免清单过长超出屏幕
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setFont(textArea.getFont().deriveFont(13f));
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        // 用 IDEA 主窗口作为父组件，确保对话框显示在 IDEA 之上
        java.awt.Component parent = WindowManager.getInstance().getFrame(project);
        JOptionPane.showMessageDialog(parent, scrollPane, "部署结果", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 上线申请表信息填写弹窗。
     * 主界面空间有限，改为独立弹窗提供更大的「上线内容描述」输入区域；
     * 再次打开时回填上次确认的内容。
     */
    private class UploadFormDialog extends DialogWrapper {
        private final JTextField tfFunctionName = new JTextField(24);
        private final JTextField tfOnlineTime = new JTextField(18);
        private final JTextArea taDesc = new JTextArea(16, 56);

        UploadFormDialog() {
            super(DeployDialog.this.project, true);
            setTitle("上线申请表信息");
            init();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 6, 6, 6);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // 功能名称
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
            panel.add(new JBLabel("功能名称:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0;
            tfFunctionName.setText(formFunctionName != null ? formFunctionName : "");
            tfFunctionName.setToolTipText("上线申请表中的功能名称，留空则该格留空");
            panel.add(tfFunctionName, gbc);

            // 上线时间（默认预填当天 23:30）
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
            panel.add(new JBLabel("上线时间:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0;
            if (formOnlineTime != null && !formOnlineTime.isEmpty()) {
                tfOnlineTime.setText(formOnlineTime);
            } else {
                tfOnlineTime.setText(new java.text.SimpleDateFormat("yyyy.MM.dd").format(new Date()) + " 23:30");
            }
            tfOnlineTime.setToolTipText("上线申请表中的上线时间，格式示例 2026.09.11 23:30，留空则不填");
            panel.add(tfOnlineTime, gbc);

            // 上线内容描述（大文本域，带滚动条）
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 0;
            panel.add(new JBLabel("上线内容描述:"), gbc);
            gbc.gridy = 3; gbc.weightx = 1.0; gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            taDesc.setLineWrap(true);
            taDesc.setWrapStyleWord(true);
            taDesc.setText(formRequirementDesc != null ? formRequirementDesc : "");
            taDesc.setToolTipText("上线申请表中的上线内容描述，可多行，留空则留空");
            JBScrollPane descScroll = new JBScrollPane(taDesc);
            descScroll.setPreferredSize(new Dimension(680, 320));
            panel.add(descScroll, gbc);

            return panel;
        }

        String getFunctionName() {
            return tfFunctionName.getText();
        }

        String getOnlineTime() {
            return tfOnlineTime.getText();
        }

        String getRequirementDesc() {
            return taDesc.getText();
        }
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
