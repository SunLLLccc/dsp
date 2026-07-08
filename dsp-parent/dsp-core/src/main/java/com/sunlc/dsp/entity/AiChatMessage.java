package com.sunlc.dsp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 智能助手消息实体。
 * 软删语义：业务流程不主动删除 ai_chat_message，消息保留用于审计；
 * deleted 字段预留以与项目其它表结构一致，默认 0，业务层不主动置 1。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务会话 ID（关联 ai_chat_session.session_id） */
    private String sessionId;

    /** 角色：user / assistant */
    private String role;

    /** 消息正文 */
    private String content;

    /** 引用来源 JSON（docs + sources），可空 */
    private String citations;

    /** 状态：0-生成中 1-完成 2-失败 3-取消 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 逻辑删除：0-正常 1-已删除（预留，消息保留用于审计，业务层不主动删除） */
    @TableLogic
    private Integer deleted;
}
