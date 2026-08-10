package com.deploy;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 部署入口 Action：从项目右键菜单触发，弹部署对话框。
 */
public class DeployAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || project.getBasePath() == null) {
            return;
        }

        // 用当前项目根路径初始化服务（自动查找/创建项目级配置 svn-deploy/deploy.properties）
        DeployService service = new DeployService(project.getBasePath());
        DeployDialog dialog = new DeployDialog(project, service);
        dialog.show();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 仅在有打开的项目时可用
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null && project.getBasePath() != null);
    }
}
