package com.deploy;

import java.util.ArrayList;
import java.util.List;

/**
 * 部署文件数据类（从原 SvnDeployTool.DFile 复制）
 */
public class DFile {
    // 文件名（不含扩展名）
    private String fileName;
    // 源码文件类型
    private String sourceFileType;
    // 编译文件类型
    private String compileFileType;
    // 源码文件路径
    private String sourcePath;
    // 编译文件路径
    private String compilePath;
    // 需要部署到服务器的路径
    private String webTargetPath;
    // 编译内部类
    private List<DFile> innerClassFiles = new ArrayList<>();

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getSourceFileType() { return sourceFileType; }
    public void setSourceFileType(String sourceFileType) { this.sourceFileType = sourceFileType; }

    public String getCompileFileType() { return compileFileType; }
    public void setCompileFileType(String compileFileType) { this.compileFileType = compileFileType; }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    public String getCompilePath() { return compilePath; }
    public void setCompilePath(String compilePath) { this.compilePath = compilePath; }

    public String getWebTargetPath() { return webTargetPath; }
    public void setWebTargetPath(String webTargetPath) { this.webTargetPath = webTargetPath; }

    public List<DFile> getInnerClassFiles() { return innerClassFiles; }
    public void addInnerClassFile(DFile file) { innerClassFiles.add(file); }

    @Override
    public String toString() {
        return this.fileName + "." + this.sourceFileType;
    }
}
