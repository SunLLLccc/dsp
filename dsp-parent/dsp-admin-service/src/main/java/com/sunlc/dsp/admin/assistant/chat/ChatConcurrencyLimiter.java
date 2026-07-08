package com.sunlc.dsp.admin.assistant.chat;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单用户 AI 会话并发限制。
 * <p>
 * 一期基于内存计数：同一 userId 同时进行的 ask 数量不超过 {@code maxConcurrentPerUser}（默认 1）。
 * 完成或失败时必须 {@link #release} 以释放配额。
 * <p>
 * 注意：内存计数不跨实例；多实例部署需替换为分布式计数（如 Redis）。一期单实例足够。
 */
@Component
public class ChatConcurrencyLimiter {

    private final ConcurrentHashMap<Long, AtomicInteger> active = new ConcurrentHashMap<>();
    private final int maxPerUser;

    public ChatConcurrencyLimiter(AssistantProperties properties) {
        this.maxPerUser = Math.max(1, properties.getMaxConcurrentPerUser());
    }

    /**
     * 尝试获取配额。
     *
     * @return true 获取成功；false 已达上限
     */
    public boolean tryAcquire(Long userId) {
        AtomicInteger count = active.computeIfAbsent(userId, k -> new AtomicInteger(0));
        while (true) {
            int current = count.get();
            if (current >= maxPerUser) {
                return false;
            }
            if (count.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    /** 释放配额。幂等。 */
    public void release(Long userId) {
        AtomicInteger count = active.get(userId);
        if (count == null) {
            return;
        }
        while (true) {
            int current = count.get();
            if (current <= 0) {
                return;
            }
            if (count.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }
}
