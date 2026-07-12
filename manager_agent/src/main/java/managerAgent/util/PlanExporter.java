package managerAgent.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将旅行计划 Markdown 文本导出为 CSV 文件。
 */
public class PlanExporter {

    private static final Pattern DAY_HEADER = Pattern.compile(
            "^#{1,3}\\s*(?:第\\s*(\\d+)\\s*天|Day\\s*(\\d+)).*$",
            Pattern.MULTILINE);

    private static final Pattern BULLET = Pattern.compile(
            "^[-*•]\\s+(.+)$", Pattern.MULTILINE);

    private static final Pattern NUMBERED = Pattern.compile(
            "^\\d+[.、]\\s*(.+)$", Pattern.MULTILINE);

    /**
     * 将 Markdown 计划文本导出为 CSV 文件，返回文件路径。
     */
    public static Path exportToCsv(String planText) throws IOException {
        Path tmpFile = Files.createTempFile("trip_plan_", ".csv");
        List<String[]> rows = parsePlan(planText);

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(tmpFile.toFile()), StandardCharsets.UTF_8))) {
            // BOM for Excel UTF-8 compatibility
            w.print('﻿');
            // Header
            w.println("天数,时间/类型,内容,备注");
            for (String[] row : rows) {
                w.println(String.join(",", escapeCsv(row[0]), escapeCsv(row[1]),
                        escapeCsv(row[2]), escapeCsv(row[3])));
            }
        }
        return tmpFile;
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static List<String[]> parsePlan(String text) {
        List<String[]> rows = new ArrayList<>();
        String currentDay = "";

        String[] lines = text.split("\n");
        for (String line : lines) {
            Matcher dm = DAY_HEADER.matcher(line);
            if (dm.find()) {
                currentDay = "第" + (dm.group(1) != null ? dm.group(1) : dm.group(2)) + "天";
                continue;
            }
            Matcher bm = BULLET.matcher(line);
            if (bm.find()) {
                String content = bm.group(1).trim();
                String type = guessType(content);
                rows.add(new String[]{currentDay, type, content, ""});
                continue;
            }
            Matcher nm = NUMBERED.matcher(line);
            if (nm.find()) {
                String content = nm.group(1).trim();
                String type = guessType(content);
                rows.add(new String[]{currentDay, type, content, ""});
            }
        }

        // If no structured data found, dump all text
        if (rows.isEmpty()) {
            rows.add(new String[]{"", "完整计划", text.replace("\n", " ").replace("\"", "'"), ""});
        }
        return rows;
    }

    private static String guessType(String item) {
        String lower = item.toLowerCase();
        if (lower.contains("酒店") || lower.contains("住宿") || lower.contains("入住") || lower.contains("民宿")) return "住宿";
        if (lower.contains("早餐") || lower.contains("午餐") || lower.contains("晚餐") || lower.contains("美食") || lower.contains("餐厅") || lower.contains("小吃") || lower.contains("吃")) return "餐饮";
        if (lower.contains("火车") || lower.contains("高铁") || lower.contains("飞机") || lower.contains("航班") || lower.contains("车次") || lower.contains("出发") || lower.contains("到达")) return "交通";
        if (lower.contains("门票") || lower.contains("¥") || lower.contains("元") || lower.contains("费用") || lower.contains("花费")) return "费用";
        if (lower.contains("景点") || lower.contains("公园") || lower.contains("山") || lower.contains("寺") || lower.contains("博物馆") || lower.contains("广场") || lower.contains("古城")) return "景点";
        return "活动";
    }
}
