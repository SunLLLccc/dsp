package com.sunlc.dsp.admin.assistant.text2api;

import java.util.List;

/**
 * 接口定义草稿（AI 生成的结构化产物）。
 */
public class InterfaceDraft {
    private String transno;
    private String name;
    private String system;
    private String description;
    private String inputSchema;
    private String outputSchema;
    /** AI 追问的问题（非空时表示信息不足，不落 interface_draft）。 */
    private List<String> questions;

    public String getTransno() { return transno; }
    public void setTransno(String transno) { this.transno = transno; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
    public String getOutputSchema() { return outputSchema; }
    public void setOutputSchema(String outputSchema) { this.outputSchema = outputSchema; }
    public List<String> getQuestions() { return questions; }
    public void setQuestions(List<String> questions) { this.questions = questions; }
}
