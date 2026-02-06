package com.abin.mallchat.ai.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI模块错误码枚举
 * 
 * @author zxw
 */
@AllArgsConstructor
@Getter
public enum AIErrorEnum implements AIException.ErrorEnum {

    // LLM相关错误 (1000-1099)
    LLM_API_ERROR(1000, "AI服务暂时不可用，请稍后再试"),
    LLM_TIMEOUT(1001, "AI响应超时，请稍后再试"),
    LLM_RATE_LIMIT(1002, "AI服务请求过于频繁，请稍后再试"),
    LLM_INVALID_RESPONSE(1003, "AI服务返回异常，请稍后再试"),
    TOKEN_LIMIT_EXCEEDED(1004, "内容过长，请缩短后重试"),
    
    // 向量存储相关错误 (1100-1199)
    VECTOR_STORE_ERROR(1100, "知识库服务暂时不可用，请稍后再试"),
    VECTOR_STORE_TIMEOUT(1101, "知识库查询超时，请稍后再试"),
    VECTOR_SEARCH_ERROR(1102, "知识检索失败，请稍后再试"),
    EMBEDDING_GENERATION_ERROR(1103, "向量生成失败，请稍后再试"),
    
    // 文档处理相关错误 (1200-1299)
    DOCUMENT_PARSE_ERROR(1200, "文档解析失败，请检查文档格式"),
    DOCUMENT_TOO_LARGE(1201, "文档过大，请上传小于10MB的文档"),
    DOCUMENT_FORMAT_UNSUPPORTED(1202, "不支持的文档格式"),
    DOCUMENT_NOT_FOUND(1203, "文档不存在"),
    DOCUMENT_INDEXING_ERROR(1204, "文档索引失败，请稍后再试"),
    
    // RAG相关错误 (1300-1399)
    INDEX_NOT_READY(1300, "知识库索引未就绪，请稍后再试"),
    NO_RELEVANT_CONTEXT(1301, "未找到相关知识，请尝试其他问题"),
    PROMPT_CONSTRUCTION_ERROR(1302, "问题处理失败，请稍后再试"),
    
    // 输入验证错误 (1400-1499)
    INVALID_INPUT(1400, "输入内容不合法"),
    EMPTY_INPUT(1401, "输入内容不能为空"),
    INPUT_TOO_LONG(1402, "输入内容过长"),
    INVALID_DOCUMENT_TYPE(1403, "文档类型不合法"),
    
    // 系统错误 (1500-1599)
    SYSTEM_ERROR(1500, "系统繁忙，请稍后再试"),
    SERVICE_DEGRADED(1501, "服务降级中，功能受限"),
    RESOURCE_EXHAUSTED(1502, "系统资源不足，请稍后再试"),
    TIMEOUT_ERROR(1503, "操作超时，请稍后再试"),
    ;

    private final Integer code;
    private final String msg;

    @Override
    public Integer getErrorCode() {
        return this.code;
    }

    @Override
    public String getErrorMsg() {
        return this.msg;
    }
}
