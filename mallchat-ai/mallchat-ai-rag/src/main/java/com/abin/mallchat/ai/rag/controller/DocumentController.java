package com.abin.mallchat.ai.rag.controller;

import com.abin.mallchat.ai.rag.domain.dto.DocumentUpdateRequest;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadRequest;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadResponse;
import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import com.abin.mallchat.ai.rag.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import javax.validation.Valid;

/**
 * 文档管理控制器
 * 
 * @author zxw
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentController {
    
    @Autowired
    private RAGService ragService;
    
    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "description", required = false) String description) {
        
        DocumentUploadRequest request = new DocumentUploadRequest();
        request.setTitle(title);
        request.setFile(file);
        request.setUserId(userId);
        request.setDescription(description);
        
        DocumentUploadResponse response = ragService.uploadDocument(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 更新文档
     */
    @PutMapping("/{documentId}")
    public ResponseEntity<DocumentUploadResponse> updateDocument(
            @PathVariable Long documentId,
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "description", required = false) String description) {
        
        DocumentUpdateRequest request = new DocumentUpdateRequest();
        request.setTitle(title);
        request.setFile(file);
        request.setUserId(userId);
        request.setDescription(description);
        
        DocumentUploadResponse response = ragService.updateDocument(documentId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除文档
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId) {
        ragService.deleteDocument(documentId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 检查索引状态
     */
    @GetMapping("/{documentId}/status")
    public ResponseEntity<String> checkIndexStatus(@PathVariable Long documentId) {
        String status = ragService.checkIndexStatus(documentId);
        return ResponseEntity.ok(status);
    }
    
    /**
     * RAG查询接口（流式响应）
     */
    @PostMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ragQuery(@Valid @RequestBody RAGQueryRequest request) {
        log.info("收到RAG查询请求，问题：{}, 文档ID：{}", request.getQuestion(), request.getDocumentId());
        return ragService.ragQuery(request);
    }
}
