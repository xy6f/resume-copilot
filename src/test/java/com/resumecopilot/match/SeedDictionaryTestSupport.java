package com.resumecopilot.match;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 共享测试支撑：从 schema.sql 解析 t_skill_alias 种子建字典。
 * SkillDictionaryTest 与 EvalRunnerTest 用同一入口——"生产字典"与"测试字典"
 * 同源（见 schema.sql 注释的承诺），本类是该承诺的实现处。
 */
public final class SeedDictionaryTestSupport {

    private static final Pattern INSERT_BLOCK =
            Pattern.compile("INSERT IGNORE INTO t_skill_alias[\\s\\S]*?VALUES([\\s\\S]*?);");
    private static final Pattern ROW = Pattern.compile("\\('([^']+)',\\s*'([^']+)'\\)");

    private SeedDictionaryTestSupport() {
    }

    public static SkillDictionary fromSchemaSql() throws IOException {
        return new SkillDictionary(rowsFromSchemaSql());
    }

    public static List<String[]> rowsFromSchemaSql() throws IOException {
        String sql;
        try (InputStream in = SeedDictionaryTestSupport.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("classpath 根找不到 schema.sql");
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher block = INSERT_BLOCK.matcher(sql);
        if (!block.find()) {
            throw new IllegalStateException("schema.sql 里找不到 t_skill_alias 的种子 INSERT 语句");
        }
        List<String[]> rows = new ArrayList<>();
        Matcher row = ROW.matcher(block.group(1));
        while (row.find()) {
            rows.add(new String[]{row.group(1), row.group(2)});
        }
        return rows;
    }
}
