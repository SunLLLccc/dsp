package com.sunlc.dsp.admin.assistant.retrieval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

/**
 * retrieval-sources.json 反序列化模型。
 * 字段与 ai-assets/retrieval-sources.json 一一对应。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetrievalSourcesConfig {

    private String version;
    private Strategy strategy;
    private Docs docs;
    private SourceCodeFallback sourceCodeFallback;
    private CitationRules citationRules;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Strategy {
        private String primary;
        private String fallback;
        public String getPrimary() { return primary; }
        public void setPrimary(String primary) { this.primary = primary; }
        public String getFallback() { return fallback; }
        public void setFallback(String fallback) { this.fallback = fallback; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Docs {
        private List<Source> sources;
        public List<Source> getSources() { return sources == null ? Collections.emptyList() : sources; }
        public void setSources(List<Source> sources) { this.sources = sources; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        private String path;
        private String type;
        private String title;
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceCodeFallback {
        private String trigger;
        private List<Source> whitelist;
        private Ignore ignore;

        public String getTrigger() { return trigger; }
        public void setTrigger(String trigger) { this.trigger = trigger; }
        public List<Source> getWhitelist() { return whitelist == null ? Collections.emptyList() : whitelist; }
        public void setWhitelist(List<Source> whitelist) { this.whitelist = whitelist; }
        public Ignore getIgnore() { return ignore; }
        public void setIgnore(Ignore ignore) { this.ignore = ignore; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ignore {
        private List<String> paths;
        private List<String> filePatterns;
        private List<String> sensitive;
        private int maxFileSizeKB = 512;

        public List<String> getPaths() { return paths == null ? Collections.emptyList() : paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }
        public List<String> getFilePatterns() { return filePatterns == null ? Collections.emptyList() : filePatterns; }
        public void setFilePatterns(List<String> filePatterns) { this.filePatterns = filePatterns; }
        public List<String> getSensitive() { return sensitive == null ? Collections.emptyList() : sensitive; }
        public void setSensitive(List<String> sensitive) { this.sensitive = sensitive; }
        public int getMaxFileSizeKB() { return maxFileSizeKB; }
        public void setMaxFileSizeKB(int maxFileSizeKB) { this.maxFileSizeKB = maxFileSizeKB; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CitationRules {
        private DocCitation docs;
        private SourceCitation sourceCode;
        public DocCitation getDocs() { return docs; }
        public void setDocs(DocCitation docs) { this.docs = docs; }
        public SourceCitation getSourceCode() { return sourceCode; }
        public void setSourceCode(SourceCitation sourceCode) { this.sourceCode = sourceCode; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocCitation {
        private List<String> show;
        public List<String> getShow() { return show == null ? Collections.emptyList() : show; }
        public void setShow(List<String> show) { this.show = show; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceCitation {
        private List<String> show;
        public List<String> getShow() { return show == null ? Collections.emptyList() : show; }
        public void setShow(List<String> show) { this.show = show; }
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Strategy getStrategy() { return strategy; }
    public void setStrategy(Strategy strategy) { this.strategy = strategy; }
    public Docs getDocs() { return docs; }
    public void setDocs(Docs docs) { this.docs = docs; }
    public SourceCodeFallback getSourceCodeFallback() { return sourceCodeFallback; }
    public void setSourceCodeFallback(SourceCodeFallback sourceCodeFallback) { this.sourceCodeFallback = sourceCodeFallback; }
    public CitationRules getCitationRules() { return citationRules; }
    public void setCitationRules(CitationRules citationRules) { this.citationRules = citationRules; }
}
