package com.sunlc.dsp.admin.assistant.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

/**
 * template-index.json 反序列化模型。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateIndexConfig {

    private String version;
    private List<TemplateEntry> templates;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TemplateEntry {
        private String id;
        private String file;
        private String scenario;
        private List<String> appliesTo;
        private List<String> selectionSignals;
        private List<String> notFor;
        private List<String> queryTypes;
        private List<String> requiresUserConfirmation;
        private String source;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public String getScenario() { return scenario; }
        public void setScenario(String scenario) { this.scenario = scenario; }
        public List<String> getAppliesTo() { return appliesTo == null ? Collections.emptyList() : appliesTo; }
        public void setAppliesTo(List<String> appliesTo) { this.appliesTo = appliesTo; }
        public List<String> getSelectionSignals() { return selectionSignals == null ? Collections.emptyList() : selectionSignals; }
        public void setSelectionSignals(List<String> selectionSignals) { this.selectionSignals = selectionSignals; }
        public List<String> getNotFor() { return notFor == null ? Collections.emptyList() : notFor; }
        public void setNotFor(List<String> notFor) { this.notFor = notFor; }
        public List<String> getQueryTypes() { return queryTypes == null ? Collections.emptyList() : queryTypes; }
        public void setQueryTypes(List<String> queryTypes) { this.queryTypes = queryTypes; }
        public List<String> getRequiresUserConfirmation() { return requiresUserConfirmation == null ? Collections.emptyList() : requiresUserConfirmation; }
        public void setRequiresUserConfirmation(List<String> requiresUserConfirmation) { this.requiresUserConfirmation = requiresUserConfirmation; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public List<TemplateEntry> getTemplates() { return templates == null ? Collections.emptyList() : templates; }
    public void setTemplates(List<TemplateEntry> templates) { this.templates = templates; }
}
