package com.fintechervision.dsp.engine.executor;

import com.fintechervision.dsp.engine.model.QueryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
@Component
public class QueryOrchestrator {

    private final ExecutorService executor;

    public QueryOrchestrator() {
        this.executor = new ThreadPoolExecutor(
                4, 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public Map<String, List<Map<String, Object>>> orchestrate(
            List<QueryConfig> queries,
            Function<QueryConfig, List<Map<String, Object>>> executeFunc) {

        if (queries == null || queries.isEmpty()) {
            return Collections.emptyMap();
        }

        if (queries.size() == 1) {
            QueryConfig query = queries.get(0);
            List<Map<String, Object>> result = executeFunc.apply(query);
            Map<String, List<Map<String, Object>>> results = new LinkedHashMap<>();
            results.put(query.getId(), result);
            return results;
        }

        Map<String, QueryConfig> queryMap = new LinkedHashMap<>();
        for (QueryConfig q : queries) {
            queryMap.put(q.getId(), q);
        }

        validateDependencies(queries, queryMap);
        detectCycle(queries, queryMap);

        Map<String, CompletableFuture<List<Map<String, Object>>>> futures = new ConcurrentHashMap<>();

        for (QueryConfig query : queries) {
            CompletableFuture<List<Map<String, Object>>> future = buildFuture(query, futures, executeFunc);
            futures.put(query.getId(), future);
        }

        Map<String, List<Map<String, Object>>> results = new LinkedHashMap<>();
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).get();
            for (QueryConfig query : queries) {
                results.put(query.getId(), futures.get(query.getId()).get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("查询编排被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("查询编排执行失败: " + cause.getMessage(), cause);
        }

        return results;
    }

    private CompletableFuture<List<Map<String, Object>>> buildFuture(
            QueryConfig query,
            Map<String, CompletableFuture<List<Map<String, Object>>>> existingFutures,
            Function<QueryConfig, List<Map<String, Object>>> executeFunc) {

        List<String> depends = query.getDepends();

        if (depends == null || depends.isEmpty()) {
            log.debug("查询[{}]无依赖，立即并行执行", query.getId());
            return CompletableFuture.supplyAsync(() -> {
                log.info("开始执行查询: id={}, type={}, datasource={}", query.getId(), query.getType(), query.getDatasource());
                try {
                    List<Map<String, Object>> result = executeFunc.apply(query);
                    log.info("查询完成: id={}, 结果行数={}", query.getId(), result.size());
                    return result;
                } catch (Exception e) {
                    log.error("查询执行失败: id={}", query.getId(), e);
                    throw e;
                }
            }, executor);
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<List<Map<String, Object>>>[] dependFutures = depends.stream()
                .map(depId -> {
                    CompletableFuture<List<Map<String, Object>>> depFuture = existingFutures.get(depId);
                    if (depFuture == null) {
                        throw new RuntimeException("查询[" + query.getId() + "]依赖[" + depId + "]不存在");
                    }
                    return depFuture;
                })
                .toArray(CompletableFuture[]::new);

        log.debug("查询[{}]依赖[{}]，等待依赖完成后执行", query.getId(), depends);

        return CompletableFuture.allOf(dependFutures)
                .thenApplyAsync(v -> {
                    log.info("依赖已完成，开始执行查询: id={}, type={}", query.getId(), query.getType());
                    try {
                        List<Map<String, Object>> result = executeFunc.apply(query);
                        log.info("查询完成: id={}, 结果行数={}", query.getId(), result.size());
                        return result;
                    } catch (Exception e) {
                        log.error("查询执行失败: id={}", query.getId(), e);
                        throw e;
                    }
                }, executor);
    }

    private void validateDependencies(List<QueryConfig> queries, Map<String, QueryConfig> queryMap) {
        for (QueryConfig query : queries) {
            if (query.getDepends() != null) {
                for (String depId : query.getDepends()) {
                    if (!queryMap.containsKey(depId)) {
                        throw new RuntimeException("查询[" + query.getId() + "]依赖[" + depId + "]不存在");
                    }
                }
            }
        }
    }

    private void detectCycle(List<QueryConfig> queries, Map<String, QueryConfig> queryMap) {
        Map<String, Integer> visited = new HashMap<>();
        for (QueryConfig q : queries) {
            visited.put(q.getId(), 0);
        }

        for (QueryConfig q : queries) {
            if (visited.get(q.getId()) == 0) {
                if (hasCycle(q.getId(), queryMap, visited)) {
                    throw new RuntimeException("检测到循环依赖，请检查query的depends配置");
                }
            }
        }
    }

    private boolean hasCycle(String queryId, Map<String, QueryConfig> queryMap, Map<String, Integer> visited) {
        visited.put(queryId, 1);

        QueryConfig query = queryMap.get(queryId);
        if (query.getDepends() != null) {
            for (String depId : query.getDepends()) {
                int state = visited.get(depId);
                if (state == 1) {
                    return true;
                }
                if (state == 0 && hasCycle(depId, queryMap, visited)) {
                    return true;
                }
            }
        }

        visited.put(queryId, 2);
        return false;
    }
}
