package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.rag.domain.dto.ChunkMetadata;
import com.abin.mallchat.ai.rag.domain.dto.DocumentMetadata;
import com.abin.mallchat.ai.rag.aspect.ChunkStrategy;
import com.abin.mallchat.ai.rag.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Apache Tika 的文档处理服务实现
 */
@Slf4j
@Service
public class TikaDocumentProcessingService implements DocumentProcessingService {
    
    private final Tika tika = new Tika();
    private final Parser parser = new AutoDetectParser();
    
    // 默认分块配置
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;
    
    @Override
    public String parseDocument(MultipartFile file) {
        try {
            return parseDocument(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to parse document: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String parseDocument(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return parseDocument(fis, file.getName());
        } catch (IOException e) {
            log.error("Failed to parse document: {}", file.getName(), e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String parseDocument(InputStream inputStream, String filename) {
        try {
            // 使用 Tika 解析文档
            String content = tika.parseToString(inputStream);
            log.info("Successfully parsed document: {}, content length: {}", filename, content.length());
            return content;
        } catch (IOException | TikaException e) {
            log.error("Failed to parse document: {}", filename, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<ChunkMetadata> chunkDocument(String content, String documentType) {
        // 根据文档类型自动选择策略
        ChunkStrategy strategy = selectStrategy(documentType);
        return chunkDocument(content, strategy);
    }
    
    @Override
    public List<ChunkMetadata> chunkDocument(String content, ChunkStrategy strategy) {
        if (strategy == ChunkStrategy.AUTO) {
            // 默认使用固定长度分块
            strategy = ChunkStrategy.FIXED_SIZE;
        }
        
        switch (strategy) {
            case SEMANTIC:
                return chunkBySemantic(content);
            case RECURSIVE:
                return chunkByRecursive(content);
            case FIXED_SIZE:
            default:
                return chunkByFixedSize(content);
        }
    }
    
    /**
     * 为分块设置文档级别的元数据
     * 
     * @param chunks 分块列表
     * @param documentId 文档ID
     * @param documentTitle 文档标题
     * @param documentType 文档类型
     */
    @Override
    public void setDocumentMetadata(List<ChunkMetadata> chunks, Long documentId, 
                                    String documentTitle, String documentType) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        
        for (ChunkMetadata chunk : chunks) {
            chunk.setDocumentId(documentId);
            chunk.setDocumentTitle(documentTitle);
            chunk.setDocumentType(documentType);
        }
        
        log.info("Set document metadata for {} chunks: documentId={}, title={}, type={}", 
                chunks.size(), documentId, documentTitle, documentType);
    }
    
    @Override
    public DocumentMetadata extractMetadata(MultipartFile file) {
        try {
            return extractMetadata(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to extract metadata: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("元数据提取失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DocumentMetadata extractMetadata(InputStream inputStream, String filename) {
        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            
            // 使用 Tika Parser 提取元数据
            BodyContentHandler handler = new BodyContentHandler(-1); // 不限制内容长度
            ParseContext context = new ParseContext();
            parser.parse(inputStream, handler, metadata, context);
            
            // 构建元数据对象
            return DocumentMetadata.builder()
                    .title(metadata.get(TikaCoreProperties.TITLE) != null ? 
                           metadata.get(TikaCoreProperties.TITLE) : filename)
                    .documentType(extractFileExtension(filename))
                    .contentType(metadata.get(Metadata.CONTENT_TYPE))
                    .author(metadata.get(TikaCoreProperties.CREATOR))
                    .creationDate(metadata.get(TikaCoreProperties.CREATED))
                    .modificationDate(metadata.get(TikaCoreProperties.MODIFIED))
                    .language(metadata.get(Metadata.CONTENT_LANGUAGE))
                    .encoding(metadata.get(Metadata.CONTENT_ENCODING))
                    .build();
                    
        } catch (IOException | SAXException | TikaException e) {
            log.error("Failed to extract metadata: {}", filename, e);
            throw new RuntimeException("元数据提取失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据文档类型选择分块策略
     */
    private ChunkStrategy selectStrategy(String documentType) {
        if (documentType == null) {
            return ChunkStrategy.FIXED_SIZE;
        }
        
        String type = documentType.toLowerCase();
        if (type.equals("md") || type.equals("markdown") || type.equals("html")) {
            return ChunkStrategy.SEMANTIC;
        } else if (type.equals("java") || type.equals("py") || type.equals("js")) {
            return ChunkStrategy.RECURSIVE;
        } else {
            return ChunkStrategy.FIXED_SIZE;
        }
    }
    
    /**
     * 固定长度分块
     */
    private List<ChunkMetadata> chunkByFixedSize(String content) {
        List<ChunkMetadata> chunks = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return chunks;
        }
        
        int contentLength = content.length();
        int chunkIndex = 0;
        
        for (int start = 0; start < contentLength; start += (DEFAULT_CHUNK_SIZE - DEFAULT_CHUNK_OVERLAP)) {
            int end = Math.min(start + DEFAULT_CHUNK_SIZE, contentLength);
            String chunkContent = content.substring(start, end);
            
            // 构建分块元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceLocation", start);
            metadata.put("sourceLocationEnd", end);
            metadata.put("chunkStrategy", "FIXED_SIZE");
            
            ChunkMetadata chunk = ChunkMetadata.builder()
                    .content(chunkContent)
                    .tokenCount(estimateTokenCount(chunkContent))
                    .chunkIndex(chunkIndex++)
                    .sourceLocation(start)
                    .sourceLocationEnd(end)
                    .chunkStrategy("FIXED_SIZE")
                    .createTimestamp(System.currentTimeMillis())
                    .metadata(metadata)
                    .build();
            
            chunks.add(chunk);
            
            // 如果已经到达末尾，退出循环
            if (end >= contentLength) {
                break;
            }
        }
        
        log.info("Fixed-size chunking completed: {} chunks created", chunks.size());
        return chunks;
    }
    
    /**
     * 语义分块（按段落）
     */
    private List<ChunkMetadata> chunkBySemantic(String content) {
        List<ChunkMetadata> chunks = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return chunks;
        }
        
        // 按段落分割（双换行符）
        String[] paragraphs = content.split("\\n\\s*\\n");
        int chunkIndex = 0;
        StringBuilder currentChunk = new StringBuilder();
        int currentStart = 0;
        int currentPosition = 0;
        
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                // 跳过空段落，但更新位置
                currentPosition += paragraph.length() + 2; // +2 for \n\n
                continue;
            }
            
            // 如果当前块加上新段落超过大小限制，保存当前块
            if (currentChunk.length() > 0 && 
                currentChunk.length() + trimmed.length() > DEFAULT_CHUNK_SIZE) {
                
                // 构建分块元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("sourceLocation", currentStart);
                metadata.put("sourceLocationEnd", currentPosition);
                metadata.put("chunkStrategy", "SEMANTIC");
                
                ChunkMetadata chunk = ChunkMetadata.builder()
                        .content(currentChunk.toString())
                        .tokenCount(estimateTokenCount(currentChunk.toString()))
                        .chunkIndex(chunkIndex++)
                        .sourceLocation(currentStart)
                        .sourceLocationEnd(currentPosition)
                        .chunkStrategy("SEMANTIC")
                        .createTimestamp(System.currentTimeMillis())
                        .metadata(metadata)
                        .build();
                chunks.add(chunk);
                
                currentChunk = new StringBuilder();
                currentStart = currentPosition;
            }
            
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(trimmed);
            currentPosition += paragraph.length() + 2; // +2 for \n\n
        }
        
        // 保存最后一个块
        if (currentChunk.length() > 0) {
            // 构建分块元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceLocation", currentStart);
            metadata.put("sourceLocationEnd", currentPosition);
            metadata.put("chunkStrategy", "SEMANTIC");
            
            ChunkMetadata chunk = ChunkMetadata.builder()
                    .content(currentChunk.toString())
                    .tokenCount(estimateTokenCount(currentChunk.toString()))
                    .chunkIndex(chunkIndex)
                    .sourceLocation(currentStart)
                    .sourceLocationEnd(currentPosition)
                    .chunkStrategy("SEMANTIC")
                    .createTimestamp(System.currentTimeMillis())
                    .metadata(metadata)
                    .build();
            chunks.add(chunk);
        }
        
        log.info("Semantic chunking completed: {} chunks created", chunks.size());
        return chunks;
    }
    
    /**
     * 递归分块（简化实现，按代码块）
     */
    private List<ChunkMetadata> chunkByRecursive(String content) {
        // 简化实现：先按固定长度分块
        // 实际生产环境可以使用更复杂的AST解析
        return chunkByFixedSize(content);
    }
    
    /**
     * 估算 token 数量
     * 简化实现：英文按空格分词，中文按字符数/1.5
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 统计中文字符数
        int chineseChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseChars++;
            }
        }
        
        // 统计英文单词数
        String[] words = text.split("\\s+");
        int englishWords = words.length;
        
        // 估算：中文字符/1.5 + 英文单词数
        return (int) (chineseChars / 1.5) + englishWords;
    }
    
    /**
     * 提取文件扩展名
     */
    private String extractFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
