package com.sunlc.dsp.admin.assistant.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 检索编排服务：问题分类 → 文档优先 → 源码兜底 → 输出 {@link RetrievalResult}。
 * <p>
 * 一期不接向量库，不写 RAG，不访问网络。问题分类用关键词规则。
 */
@Slf4j
@Service
public class RetrievalService {

    /** 文档 topK。 */
    private static final int DOC_TOP_K = 5;
    /** 源码 topK。 */
    private static final int SOURCE_TOP_K = 3;
    /** retrievalContext 最大字符数（避免 prompt 过长）。 */
    private static final int MAX_CONTEXT_CHARS = 6000;
    /** 触发源码兜底的文档结果数阈值（文档命中数 ≤ 该值时触发兜底）。 */
    private static final int SOURCE_FALLBACK_DOC_THRESHOLD = 1;

    private final DocRetriever docRetriever;
    private final SourceCodeRetriever sourceCodeRetriever;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 项目相关关键词（命中任一即认为项目相关）。 */
    private static final Set<String> PROJECT_KEYWORDS = new HashSet<>(Arrays.asList(
            "dsp", "xml", "dsl", "sql", "接口", "模板", "引擎", "数据源", "datasource",
            "分页", "动态", "编排", "resultmap", "mybatis", "spring", "controller", "service",
            "mapper", "entity", "config", "模板", "template", "admin", "offline", "transno",
            "requestdata", "response", "jwt", "dubbo", "mongo", "http", "cursor", "pagination",
            "class", "method", "函数", "类", "方法", "配置", "数据库", "审批", "授权", "导出",
            "model", "agent", "agentscope", "ai", "retrieval", "检索", "知识库"));

    public RetrievalService(DocRetriever docRetriever, SourceCodeRetriever sourceCodeRetriever) {
        this.docRetriever = docRetriever;
        this.sourceCodeRetriever = sourceCodeRetriever;
    }

    /**
     * 统一检索入口。
     *
     * @param question 用户原始问题
     * @return 检索结果（非项目相关返回空 context/citations）
     */
    public RetrievalResult retrieve(String question) {
        if (question == null || question.isBlank()) {
            return RetrievalResult.notProjectRelated();
        }
        boolean projectRelated = isProjectRelated(question);
        if (!projectRelated) {
            log.debug("非项目相关问题，跳过本地检索：{}", truncate(question, 40));
            return RetrievalResult.notProjectRelated();
        }

        List<String> tokens = KeywordSearchUtils.tokenize(question);
        if (tokens.isEmpty()) {
            return RetrievalResult.notProjectRelated();
        }

        // 文档优先
        List<RetrievalCitation> docHits = docRetriever.retrieve(tokens, DOC_TOP_K);

        // 源码兜底：文档命中不足时触发
        List<RetrievalCitation> sourceHits = new ArrayList<>();
        if (docHits.size() <= SOURCE_FALLBACK_DOC_THRESHOLD) {
            log.debug("文档命中不足（{}），触发源码兜底", docHits.size());
            sourceHits = sourceCodeRetriever.retrieve(tokens, SOURCE_TOP_K);
        }

        List<RetrievalCitation> all = new ArrayList<>(docHits.size() + sourceHits.size());
        all.addAll(docHits);
        all.addAll(sourceHits);

        String citationsJson = toCitationsJson(all);
        String context = buildContext(all, docHits.isEmpty() && sourceHits.isEmpty());
        return new RetrievalResult(true, context, citationsJson, all);
    }

    /** 问题分类：命中项目关键词即项目相关。 */
    boolean isProjectRelated(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        for (String kw : PROJECT_KEYWORDS) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String toCitationsJson(List<RetrievalCitation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            log.warn("citations JSON 序列化失败，返回空数组", e);
            return "[]";
        }
    }

    /** 构造给 prompt 的检索上下文。 */
    private String buildContext(List<RetrievalCitation> citations, boolean noneHit) {
        if (noneHit) {
            return "本地文档/源码未检索到相关依据，请谨慎回答或向用户追问。";
        }
        StringBuilder sb = new StringBuilder();
        List<RetrievalCitation> docs = filterByType(citations, SourceType.DOC.getCode());
        List<RetrievalCitation> sources = filterByType(citations, SourceType.SOURCE.getCode());
        if (!docs.isEmpty()) {
            sb.append("【文档依据】\n");
            for (RetrievalCitation c : docs) {
                appendEntry(sb, c);
                if (sb.length() > MAX_CONTEXT_CHARS) {
                    break;
                }
            }
        }
        if (!sources.isEmpty()) {
            sb.append("【源码依据】\n");
            for (RetrievalCitation c : sources) {
                appendEntry(sb, c);
                if (sb.length() > MAX_CONTEXT_CHARS) {
                    break;
                }
            }
        }
        String result = sb.toString();
        if (result.length() > MAX_CONTEXT_CHARS) {
            result = result.substring(0, MAX_CONTEXT_CHARS) + "...(已截断)";
        }
        return result.stripTrailing();
    }

    private void appendEntry(StringBuilder sb, RetrievalCitation c) {
        sb.append("- ").append(c.getPath());
        if (c.getLineStart() != null && c.getLineEnd() != null) {
            sb.append(":").append(c.getLineStart()).append("-").append(c.getLineEnd());
        }
        if (c.getTitle() != null && !c.getTitle().isEmpty()) {
            sb.append("（").append(c.getTitle()).append("）");
        }
        sb.append("\n").append(c.getSnippet()).append("\n\n");
    }

    private List<RetrievalCitation> filterByType(List<RetrievalCitation> citations, String type) {
        List<RetrievalCitation> result = new ArrayList<>();
        for (RetrievalCitation c : citations) {
            if (type.equals(c.getType())) {
                result.add(c);
            }
        }
        return result;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
