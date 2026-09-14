package com.deploy;

import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.util.*;

/**
 * 上线申请表自动生成器。
 * <p>
 * 以插件内置的空白申请表 (.docx) 为基础模板，模板中预设了占位符：
 * <pre>
 *   {gnmc}  功能名称
 *   {nrms}  上线内容描述（多行）
 *   {sxsj}  上线时间
 *   {yjwz}  移交位置（SVN 路径末段，即需求目录名）
 *   {wjsl}  文件数量
 *   {ywj}   源文件清单（多行）
 *   {jwj}   结果文件清单（多行）
 * </pre>
 * 生成时遍历文档所有段落（含表格、嵌套表），将占位符替换为实际值即可，
 * 模板原有格式（光盘/SVN/FTP 勾选、标题行等）完全保留不动。
 */
public class UploadFormGenerator {

    /** 内置模板资源路径 */
    private static final String TEMPLATE_RESOURCE = "/upload/上报申请模板.docx";

    public UploadFormGenerator() { }

    /**
     * 生成申请表。
     *
     * @param functionName  功能名称（替换 {gnmc}）
     * @param descText      上线内容描述（替换 {nrms}，支持多行）
     * @param onlineTime    上线时间（替换 {sxsj}）
     * @param fileStats     文件数量描述（替换 {wjsl}）
     * @param sourceFiles   源文件清单（替换 {ywj}，每项一行）
     * @param resultFiles   结果文件清单（替换 {jwj}，每项一行）
     * @param needDirName   需求目录名（替换 {yjwz}，即 SVN 路径末段）
     * @param outputPath    输出 .docx 绝对路径
     */
    public void generate(String functionName, String descText, String onlineTime,
                         String fileStats, List<String> sourceFiles, List<String> resultFiles,
                         String needDirName, String outputPath) throws IOException {
        byte[] templateBytes;
        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (is == null) {
                throw new FileNotFoundException("内置申请表模板不存在: " + TEMPLATE_RESOURCE);
            }
            templateBytes = is.readAllBytes();
        }

        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("{gnmc}", nvl(functionName));
        replacements.put("{nrms}", nvl(descText));
        replacements.put("{sxsj}", nvl(onlineTime));
        replacements.put("{yjwz}", nvl(needDirName));
        replacements.put("{wjsl}", nvl(fileStats));
        replacements.put("{ywj}", joinLines(sourceFiles));
        replacements.put("{jwj}", joinLines(resultFiles));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(templateBytes))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                replaceInParagraph(p, replacements);
            }
            for (XWPFTable table : doc.getTables()) {
                replaceInTable(table, replacements);
            }

            File out = new File(outputPath);
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(out)) {
                doc.write(fos);
            }
        }
    }

    /** 递归遍历表格（含嵌套表）所有单元格段落，执行占位符替换 */
    private void replaceInTable(XWPFTable table, Map<String, String> replacements) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, replacements);
                }
                for (XWPFTable nested : cell.getTables()) {
                    replaceInTable(nested, replacements);
                }
            }
        }
    }

    /**
     * 在单个段落中做占位符替换：取段落全文，替换后写回。
     * 多行值（含 \n）用 addBreak() 换行；字体沿用段落首个有文本 run 的格式，兜底宋体 10pt。
     */
    private void replaceInParagraph(XWPFParagraph para, Map<String, String> replacements) {
        String text = para.getText();
        if (text == null || text.isEmpty()) return;

        boolean changed = false;
        for (Map.Entry<String, String> e : replacements.entrySet()) {
            if (text.contains(e.getKey())) {
                text = text.replace(e.getKey(), e.getValue());
                changed = true;
            }
        }
        if (!changed) return;

        List<XWPFRun> runs = para.getRuns();
        String fontFamily = "宋体";
        double fontSize = 10;
        for (XWPFRun r : runs) {
            String rt = r.getText(0);
            if (rt != null && !rt.isEmpty()) {
                String ff = r.getFontFamily();
                if (ff != null) fontFamily = ff;
                double fs = r.getFontSize();
                if (fs > 0) fontSize = fs;
                break;
            }
        }

        for (int i = runs.size() - 1; i >= 0; i--) {
            para.removeRun(i);
        }

        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = para.createRun();
            run.setText(lines[i]);
            run.setFontFamily(fontFamily);
            if (fontSize > 0) run.setFontSize(fontSize);
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String joinLines(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join("\n", list);
    }
}
