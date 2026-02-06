package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.common.dao.AIConversationDao;
import com.abin.mallchat.ai.common.dao.DocumentChunkDao;
import com.abin.mallchat.ai.common.dao.KnowledgeDocumentDao;
import com.abin.mallchat.ai.common.domain.entity.AIConversation;
import com.abin.mallchat.ai.common.domain.entity.KnowledgeDocument;
import com.abin.mallchat.ai.common.domain.enums.ConversationType;
import com.abin.mallchat.ai.common.domain.enums.IndexStatus;
import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.service.LLMService;
import com.abin.mallchat.ai.rag.config.DocumentConfig;
import com.abin.mallchat.ai.rag.domain.dto.DocumentIndexingMessage;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUpdateRequest;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadRequest;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadResponse;
import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import com.abin.mallchat.ai.rag.cache.DocumentMetadataCache;
import com.abin.mallchat.ai.rag.cache.IndexStatusCache;
import com.abin.mallchat.ai.rag.cache.QueryResultCache;
import com.abin.mallchat.ai.rag.service.DegradationService;
import com.abin.mallchat.ai.rag.service.DocumentIndexingProducer;
import com.abin.mallchat.ai.rag.service.RAGService;
import com.abin.mallchat.ai.vector.domain.SearchResult;
import com.abin.mallchat.ai.vector.service.EmbeddingService;
import com.abin.mallchat.ai.vector.service.VectorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG服务实现
 * 
 * @author zxw
 */
@Slf4j
@Service
public class RAGServiceImpl implements RAGService {
    
    @Autowired
    private KnowledgeDocumentDao knowledgeDocumentDao;
    
    @Autowired
    private DocumentChunkDao documentChunkDao;
    
    @Autowired
    private AIConversationDao aiConversationDao;
    
    @Autowired
    private VectorService vectorService;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private LLMService llmService;
    
    @Autowired
    private DocumentConfig documentConfig;
    
    @Autowired
    private DocumentIndexingProducer documentIndexingProducer;
    
    @Autowired
    private DegradationService degradationService;
    
    @Autowired
    private DocumentMetadataCache documentMetadataCache;
    
    @Autowired
    private IndexStatusCache indexStatusCache;
    
    @Autowired
    private QueryResultCache queryResultCache;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public Flux<String> ragQuery(RAGQueryRequest request) {
        log.info("开始RAG查询，问题：{}, 文档ID：{}, 用户ID：{}", 
                request.getQuestion(), request.getDocumentId(), request.getUserId());
        
        long startTime = System.currentTimeMillis();
        
        // 1. 检查索引状态（使用缓存）
        if (request.getDocumentId() != null) {
            String indexStatus = indexStatusCache.getIndexStatus(request.getDocumentId());
            if (!IndexStatus.COMPLETED.name().equals(indexStatus)) {
                String message = getIndexStatusMessage(indexStatus);
                log.warn("索引未就绪，文档ID：{}, 状态：{}", request.getDocumentId(), indexStatus);
                return Flux.just(message);
            }
        }
        
        try {
            // 2. 尝试从缓存获取查询结果
            final List<SearchResult> cachedSearchResults = queryResultCache.getQueryResult(
                    request.getQuestion(), 
                    request.getDocumentId(), 
                    request.getTopK()
            );
            
            final List<SearchResult> searchResults;
            if (cachedSearchResults == null) {
                // 缓存未命中，执行向量检索
                // 2.1 生成问题向量
                float[] queryVector = embeddingService.generateEmbedding(request.getQuestion());
                log.debug("问题向量生成成功，维度：{}", queryVector.length);
                
                // 2.2 执行向量检索
                searchResults = vectorService.search(
                        queryVector, 
                        request.getTopK(), 
                        request.getDocumentId()
                );
                
                log.info("向量检索完成，找到 {} 个相关片段", searchResults.size());
                
                // 2.3 缓存查询结果
                if (!searchResults.isEmpty()) {
                    queryResultCache.cacheQueryResult(
                            request.getQuestion(), 
                            request.getDocumentId(), 
                            request.getTopK(), 
                            searchResults
                    );
                }
            } else {
                searchResults = cachedSearchResults;
                log.info("从缓存获取查询结果，找到 {} 个相关片段", searchResults.size());
            }
            
            // 3. 检查检索结果
            if (searchResults.isEmpty()) {
                log.warn("未找到相关知识片段，降级到普通问答");
                return fallbackToNormalQA(request, startTime);
            }
            
            // 5. 构造RAG Prompt
            String ragPrompt = buildRAGPrompt(request.getQuestion(), searchResults);
            log.debug("RAG Prompt构造完成，长度：{}", ragPrompt.length());
            
            // 6. 调用LLM流式生成回答
            LLMOptions options = LLMOptions.builder()
                    .temperature(0.7)
                    .maxTokens(2000)
                    .build();
            
            Flux<String> responseFlux = llmService.streamChat(ragPrompt, options);
            
            // 7. 收集完整响应并保存对话历史
            StringBuilder fullResponse = new StringBuilder();
            return responseFlux
                    .doOnNext(chunk -> fullResponse.append(chunk))
                    .doOnComplete(() -> {
                        long responseTime = System.currentTimeMillis() - startTime;
                        saveConversation(request, fullResponse.toString(), searchResults, responseTime);
                        log.info("RAG查询完成，耗时：{}ms", responseTime);
                    })
                    .doOnError(error -> {
                        log.error("RAG查询失败", error);
                    });
            
        } catch (Exception e) {
            log.error("RAG查询异常，尝试降级处理", e);
            // 向量库不可用时，降级到普通问答
            if (degradationService.shouldDegrade()) {
                log.warn("检测到服务降级条件，使用降级策略");
                return degradationService.degradedRAGQuery(request.getQuestion());
            }
            return Flux.just("抱歉，处理您的问题时发生错误，请稍后重试。");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse uploadDocument(DocumentUploadRequest request) {
        log.info("开始上传文档，标题：{}, 用户ID：{}", request.getTitle(), request.getUserId());
        
        // 1. 验证文档
        validateDocument(request.getFile());
        
        // 2. 保存文档到本地存储或OSS
        String filePath = saveDocument(request.getFile());
        
        // 3. 创建文档记录
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(request.getTitle());
        document.setDocumentType(getFileExtension(request.getFile().getOriginalFilename()));
        document.setFileSize(request.getFile().getSize());
        document.setFilePath(filePath);
        document.setIndexStatus(IndexStatus.PENDING.name());
        document.setUploadUserId(request.getUserId());
        document.setChunkCount(0);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        
        // 4. 保存到数据库
        knowledgeDocumentDao.save(document);
        
        log.info("文档上传成功，文档ID：{}, 文件路径：{}", document.getId(), filePath);
        
        // 5. 触发异步索引任务
        DocumentIndexingMessage indexingMessage = DocumentIndexingMessage.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .filePath(filePath)
                .documentType(document.getDocumentType())
                .retryCount(0)
                .build();
        documentIndexingProducer.sendIndexingTask(indexingMessage);
        
        // 6. 缓存文档元数据
        documentMetadataCache.invalidateDocumentMetadata(document.getId());
        
        // 7. 返回响应
        return DocumentUploadResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .indexStatus(document.getIndexStatus())
                .message("文档上传成功，正在等待索引处理")
                .build();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse updateDocument(Long documentId, DocumentUpdateRequest request) {
        log.info("开始更新文档，文档ID：{}", documentId);
        
        // 1. 查询旧文档
        KnowledgeDocument oldDocument = knowledgeDocumentDao.getById(documentId);
        if (oldDocument == null) {
            throw new IllegalArgumentException("文档不存在，ID：" + documentId);
        }
        
        // 2. 幂等删除旧版本向量
        log.info("幂等删除旧版本向量，文档ID：{}", documentId);
        vectorService.deleteVectors(documentId);
        
        // 3. 验证新文档
        validateDocument(request.getFile());
        
        // 4. 保存新文档
        String filePath = saveDocument(request.getFile());
        
        // 5. 更新文档记录
        oldDocument.setTitle(request.getTitle());
        oldDocument.setDocumentType(getFileExtension(request.getFile().getOriginalFilename()));
        oldDocument.setFileSize(request.getFile().getSize());
        oldDocument.setFilePath(filePath);
        oldDocument.setIndexStatus(IndexStatus.PENDING.name());
        oldDocument.setChunkCount(0);
        oldDocument.setErrorMessage(null);
        oldDocument.setUpdateTime(LocalDateTime.now());
        
        knowledgeDocumentDao.updateById(oldDocument);
        
        log.info("文档更新成功，文档ID：{}", documentId);
        
        // 6. 触发异步索引任务
        DocumentIndexingMessage indexingMessage = DocumentIndexingMessage.builder()
                .documentId(oldDocument.getId())
                .title(oldDocument.getTitle())
                .filePath(filePath)
                .documentType(oldDocument.getDocumentType())
                .retryCount(0)
                .build();
        documentIndexingProducer.sendIndexingTask(indexingMessage);
        
        // 7. 清除相关缓存
        documentMetadataCache.invalidateDocumentMetadata(documentId);
        indexStatusCache.invalidateIndexStatus(documentId);
        queryResultCache.invalidateByDocumentId(documentId);
        
        return DocumentUploadResponse.builder()
                .documentId(oldDocument.getId())
                .title(oldDocument.getTitle())
                .indexStatus(oldDocument.getIndexStatus())
                .message("文档更新成功，正在等待索引处理")
                .build();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long documentId) {
        log.info("开始删除文档，文档ID：{}", documentId);
        
        // 1. 幂等删除向量
        log.info("删除文档向量，文档ID：{}", documentId);
        vectorService.deleteVectors(documentId);
        
        // 2. 删除文档分块记录
        log.info("删除文档分块记录，文档ID：{}", documentId);
        documentChunkDao.deleteByDocumentId(documentId);
        
        // 3. 删除文档记录（硬删除）
        log.info("删除文档记录，文档ID：{}", documentId);
        knowledgeDocumentDao.removeById(documentId);
        
        // 4. 清除相关缓存
        documentMetadataCache.invalidateDocumentMetadata(documentId);
        indexStatusCache.invalidateIndexStatus(documentId);
        queryResultCache.invalidateByDocumentId(documentId);
        
        log.info("文档删除成功，文档ID：{}", documentId);
    }
    
    @Override
    public String checkIndexStatus(Long documentId) {
        // 使用缓存查询索引状态
        return indexStatusCache.getIndexStatus(documentId);
    }
    
    /**
     * 验证文档格式和大小
     */
    private void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文档文件不能为空");
        }
        
        // 验证文件大小
        if (file.getSize() > documentConfig.getMaxFileSize()) {
            throw new IllegalArgumentException(
                    String.format("文档大小超过限制，最大允许：%d MB", 
                            documentConfig.getMaxFileSize() / 1024 / 1024));
        }
        
        // 验证文件格式
        String extension = getFileExtension(file.getOriginalFilename());
        if (!documentConfig.getAllowedFormats().contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(
                    String.format("不支持的文档格式：%s，支持的格式：%s", 
                            extension, documentConfig.getAllowedFormats()));
        }
    }
    
    /**
     * 保存文档到本地存储或OSS
     */
    private String saveDocument(MultipartFile file) {
        try {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String filename = UUID.randomUUID().toString() + "." + extension;
            
            if (documentConfig.getUseOss()) {
                // TODO: 实现OSS上传逻辑
                throw new UnsupportedOperationException("OSS存储功能尚未实现");
            } else {
                // 本地存储
                Path storagePath = Paths.get(documentConfig.getStoragePath());
                if (!Files.exists(storagePath)) {
                    Files.createDirectories(storagePath);
                }
                
                Path filePath = storagePath.resolve(filename);
                file.transferTo(filePath.toFile());
                
                return filePath.toString();
            }
        } catch (IOException e) {
            log.error("保存文档失败", e);
            throw new RuntimeException("保存文档失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
    
    /**
     * 根据索引状态返回友好提示信息
     */
    private String getIndexStatusMessage(String status) {
        if (IndexStatus.PENDING.name().equals(status)) {
            return "文档正在等待索引处理，请稍后再试。";
        } else if (IndexStatus.INDEXING.name().equals(status)) {
            return "文档正在索引中，请稍后再试。";
        } else if (IndexStatus.FAILED.name().equals(status)) {
            return "文档索引失败，请联系管理员或重新上传文档。";
        }
        return "文档索引状态异常，请联系管理员。";
    }
    
    /**
     * 降级到普通问答模式
     */
    private Flux<String> fallbackToNormalQA(RAGQueryRequest request, long startTime) {
        String prompt = buildNormalQAPrompt(request.getQuestion());
        
        LLMOptions options = LLMOptions.builder()
                .temperature(0.7)
                .maxTokens(2000)
                .build();
        
        Flux<String> responseFlux = llmService.streamChat(prompt, options);
        
        StringBuilder fullResponse = new StringBuilder();
        return responseFlux
                .doOnNext(chunk -> fullResponse.append(chunk))
                .doOnComplete(() -> {
                    long responseTime = System.currentTimeMillis() - startTime;
                    saveConversation(request, fullResponse.toString(), null, responseTime);
                    log.info("普通问答完成，耗时：{}ms", responseTime);
                });
    }
    
    /**
     * 构造RAG Prompt（系统指令 + 上下文 + 问题）
     */
    private String buildRAGPrompt(String question, List<SearchResult> searchResults) {
        StringBuilder prompt = new StringBuilder();
        
        // 系统指令
        prompt.append("你是一个专业的知识问答助手。请根据以下提供的知识库内容回答用户的问题。\n\n");
        prompt.append("回答要求：\n");
        prompt.append("1. 仅基于提供的知识库内容回答，不要编造信息\n");
        prompt.append("2. 如果知识库中没有相关信息，请明确告知用户\n");
        prompt.append("3. 回答要准确、简洁、易懂\n");
        prompt.append("4. 可以适当引用知识库中的原文\n\n");
        
        // 检索上下文（去重）
        prompt.append("知识库内容：\n");
        prompt.append("---\n");
        
        // 去重并按相似度排序
        List<SearchResult> uniqueResults = deduplicateSearchResults(searchResults);
        for (int i = 0; i < uniqueResults.size(); i++) {
            SearchResult result = uniqueResults.get(i);
            prompt.append(String.format("[片段 %d] (相似度: %.2f)\n", i + 1, result.getScore()));
            prompt.append(result.getContent());
            prompt.append("\n\n");
        }
        prompt.append("---\n\n");
        
        // 用户问题
        prompt.append("用户问题：\n");
        prompt.append(question);
        prompt.append("\n\n请回答：");
        
        return prompt.toString();
    }
    
    /**
     * 构造普通问答Prompt
     */
    private String buildNormalQAPrompt(String question) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个友好的AI助手。请回答用户的问题。\n\n");
        prompt.append("用户问题：\n");
        prompt.append(question);
        prompt.append("\n\n请回答：");
        
        return prompt.toString();
    }
    
    /**
     * 去重检索结果（基于内容相似度）
     */
    private List<SearchResult> deduplicateSearchResults(List<SearchResult> results) {
        // 简单去重：如果内容完全相同，只保留一个
        return results.stream()
                .collect(Collectors.toMap(
                        SearchResult::getContent,
                        result -> result,
                        (existing, replacement) -> existing.getScore() > replacement.getScore() ? existing : replacement
                ))
                .values()
                .stream()
                .sorted((r1, r2) -> Float.compare(r2.getScore(), r1.getScore()))
                .collect(Collectors.toList());
    }
    
    /**
     * 保存对话历史
     */
    private void saveConversation(RAGQueryRequest request, String aiResponse, 
                                   List<SearchResult> searchResults, long responseTime) {
        try {
            AIConversation conversation = new AIConversation();
            conversation.setUserId(request.getUserId());
            conversation.setConversationType(ConversationType.RAG.name());
            conversation.setUserInput(request.getQuestion());
            conversation.setAiResponse(aiResponse);
            conversation.setDocumentId(request.getDocumentId());
            conversation.setResponseTime(responseTime);
            conversation.setCreateTime(LocalDateTime.now());
            
            // 保存检索到的分块ID列表
            if (searchResults != null && !searchResults.isEmpty()) {
                List<Long> chunkIds = searchResults.stream()
                        .map(SearchResult::getChunkId)
                        .collect(Collectors.toList());
                conversation.setRetrievedChunkIds(objectMapper.writeValueAsString(chunkIds));
            }
            
            aiConversationDao.save(conversation);
            log.debug("对话历史保存成功，对话ID：{}", conversation.getId());
            
        } catch (JsonProcessingException e) {
            log.error("保存对话历史失败", e);
        }
    }
}
