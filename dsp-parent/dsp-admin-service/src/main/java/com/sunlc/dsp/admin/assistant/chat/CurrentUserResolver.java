package com.sunlc.dsp.admin.assistant.chat;

import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从 request attribute 稳健提取当前登录用户信息。
 * <p>
 * adminUserId 来自 JWT claims，类型可能是 Long/Integer/String/null，
 * 这里统一 toString + Long.valueOf 并 try-catch，转换失败按鉴权异常处理，
 * 不让 ClassCastException / NumberFormatException 泄露到前端。
 */
public final class CurrentUserResolver {

    public static final String ATTR_ADMIN_USER = "adminUser";
    public static final String ATTR_ADMIN_USER_ID = "adminUserId";

    private CurrentUserResolver() {
    }

    /** 取当前用户名（adminUser），未登录返回 null。 */
    public static String resolveUserName(HttpServletRequest request) {
        Object user = request.getAttribute(ATTR_ADMIN_USER);
        return user != null ? user.toString() : null;
    }

    /**
     * 取当前用户 ID 并稳健转换为 Long。
     * 空值或格式错误抛 {@link BusinessException}（TOKEN_MISSING/ACCESS_DENIED），不返回 null、不抛运行时异常。
     */
    public static Long requireUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(ATTR_ADMIN_USER_ID);
        if (raw == null) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING, "当前用户未登录（adminUserId 缺失）");
        }
        try {
            return Long.valueOf(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前用户ID格式非法，无法识别身份");
        }
    }

    /** 将任意 attribute 值转换为 Long，失败返回 null（用于可选字段如 deptId）。 */
    public static Long toLongOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
