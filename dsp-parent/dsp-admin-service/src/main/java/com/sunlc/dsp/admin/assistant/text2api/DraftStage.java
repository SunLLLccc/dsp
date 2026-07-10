package com.sunlc.dsp.admin.assistant.text2api;

/**
 * Text2API 草稿阶段常量。
 * 1-需求 2-接口定义 3-SQL 4-模板 5-XML 6-已发布。
 * <p>
 * 回退 invalidation：rollbackToStage(N) 设置 invalidated_from_stage = N + 1，
 * 表示 N+1 及之后的产物失效（保留但不允许发布）。
 */
public final class DraftStage {

    public static final int REQUIREMENT = 1;
    public static final int INTERFACE = 2;
    public static final int SQL = 3;
    public static final int TEMPLATE = 4;
    public static final int XML = 5;
    public static final int PUBLISHED = 6;

    /** 最大有效阶段（用于校验）。 */
    public static final int MAX = 6;

    private DraftStage() {
    }

    /** 校验阶段值合法。 */
    public static boolean isValid(int stage) {
        return stage >= REQUIREMENT && stage <= MAX;
    }

    /** 转中文描述。 */
    public static String name(int stage) {
        switch (stage) {
            case REQUIREMENT: return "需求输入";
            case INTERFACE: return "接口定义";
            case SQL: return "Text2SQL";
            case TEMPLATE: return "模板选择";
            case XML: return "XML/JSON生成";
            case PUBLISHED: return "已发布";
            default: return "未知(" + stage + ")";
        }
    }
}
