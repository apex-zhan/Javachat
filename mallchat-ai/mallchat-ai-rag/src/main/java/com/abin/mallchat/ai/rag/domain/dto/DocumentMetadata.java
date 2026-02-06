package com.abin.mallchat.ai.rag.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 文档类型（txt, pdf, md, html, docx等）
     */
    private String documentType;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 内容类型（MIME type）
     */
    private String contentType;
    
    /**
     * 作者
     */
    private String author;
    
    /**
     * 创建时间
     */
    private String creationDate;
    
    /**
     * 修改时间
     */
    private String modificationDate;
    
    /**
     * 语言
     */
    private String language;
    
    /**
     * 字符编码
     */
    private String encoding;
}
