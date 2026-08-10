package com.deploy;

import java.util.List;

/**
 * 部署请求参数（替代原控制台交互输入）
 */
public class DeployRequest {

    /** 部署环境："1" 测试环境 / "2" 生产环境 */
    private String environment;

    /** 获取方式："1" 本地修改 / "2" SVN 历史 */
    private String mode;

    /** SVN 版本号列表（mode=2 时使用，如 ["r1562","r1565"]） */
    private List<String> revisions;

    /** 选中的文件索引列表（对应 modifiedFiles 列表下标） */
    private List<Integer> selectedIndices;

    /** 是否全选 */
    private boolean selectAll;

    /** 排除模式（选中索引为排除项） */
    private boolean excludeMode;

    /** 项目根路径（来自当前 IntelliJ Project basePath） */
    private String projectRoot;

    /** 自定义配置文件路径（可选，为空则用内置默认配置） */
    private String configPath;

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public List<String> getRevisions() { return revisions; }
    public void setRevisions(List<String> revisions) { this.revisions = revisions; }

    public List<Integer> getSelectedIndices() { return selectedIndices; }
    public void setSelectedIndices(List<Integer> selectedIndices) { this.selectedIndices = selectedIndices; }

    public boolean isSelectAll() { return selectAll; }
    public void setSelectAll(boolean selectAll) { this.selectAll = selectAll; }

    public boolean isExcludeMode() { return excludeMode; }
    public void setExcludeMode(boolean excludeMode) { this.excludeMode = excludeMode; }

    public String getProjectRoot() { return projectRoot; }
    public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }

    public String getConfigPath() { return configPath; }
    public void setConfigPath(String configPath) { this.configPath = configPath; }
}
