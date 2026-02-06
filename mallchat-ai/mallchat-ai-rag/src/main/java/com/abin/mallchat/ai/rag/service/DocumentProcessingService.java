package com.abin.mallchat.ai.rag.service;

import com.abin.mallchat.ai.rag.aspect.ChunkStrategy;
import com.abin.mallchat.ai.rag.domain.dto.ChunkMetadata;
import com.abin.mallchat.ai.rag.domain.dto.DocumentMetadata;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * 文档处理服务接口
 * 负责文档解析、分块和元数据提取
 */
public interface DocumentProcessingService {
    
    /**
     * 解析文档内容
     * 
     * @param file 文档文件
     * @return 解析后的文本内容
     */
    String parseDocument(MultipartFile file);
    
    /**
     * 解析文档内容（从File）
     * 
     * @param file 文档文件
     * @return 解析后的文本内容
     */
    String parseDocument(File file);
    
    /**
     * 解析文档内容（从输入流）
     * 
     * @param inputStream 输入流
     * @param filename 文件名（用于推断类型）
     * @return 解析后的文本内容
     */
    String parseDocument(InputStream inputStream, String filename);
    
    /**
     * 文档分块
     * 
     * @param content 文档内容
     * @param documentType 文档类型
     * @return 分块列表
     */
    List<ChunkMetadata> chunkDocument(String content, String documentType);
    
    /**
     * 文档分块（指定策略）
     * 
     * @param content 文档内容
     * @param strategy 分块策略
     * @return 分块列表
     */
    List<ChunkMetadata> chunkDocument(String content, ChunkStrategy strategy);
    
    /**
     * 为分块设置文档级别的元数据
     * 
     * @param chunks 分块列表
     * @param documentId 文档ID
     * @param documentTitle 文档标题
     * @param documentType 文档类型
     */
    void setDocumentMetadata(List<ChunkMetadata> chunks, Long documentId, 
                            String documentTitle, String documentType);
    
    /**
     * 提取文档元数据
     * 
     * @param file 文档文件
     * @return 文档元数据
     */
    DocumentMetadata extractMetadata(MultipartFile file);
    
    /**
     * 提取文档元数据（从输入流）
     * 
     * @param inputStream 输入流
     * @param filename 文件名
     * @return 文档元数据
     */
    DocumentMetadata extractMetadata(InputStream inputStream, String filename);
}
