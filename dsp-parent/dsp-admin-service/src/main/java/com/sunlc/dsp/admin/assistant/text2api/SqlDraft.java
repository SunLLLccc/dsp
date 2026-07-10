package com.sunlc.dsp.admin.assistant.text2api;

import java.util.List;

/**
 * Text2SQL 结构化产物。
 */
public class SqlDraft {
    private List<SqlItem> sqlItems;
    /** AI 追问的问题（非空时表示信息不足，不落 sql_draft）。 */
    private List<String> questions;

    public List<SqlItem> getSqlItems() { return sqlItems; }
    public void setSqlItems(List<SqlItem> sqlItems) { this.sqlItems = sqlItems; }
    public List<String> getQuestions() { return questions; }
    public void setQuestions(List<String> questions) { this.questions = questions; }
}
