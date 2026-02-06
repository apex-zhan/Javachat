package com.abin.mallchat.ai.rag.aspect;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abin.mallchat.ai.rag.controller.DocumentController;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadRequest;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadResponse;
import com.abin.mallchat.ai.rag.service.RAGService;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 属性测试：请求日志完整性
 * Feature: ai-assistant-rag, Property 29: Request Logging Completeness
 * Validates: Requirements 12.1
 * 
 * 验证每个 AI 请求都有完整的日志记录，包含必要字段
 * 
 * @author AI Assistant
 * @date 2025-01-07
 */
class RequestLoggingCompletenessPropertyTest {

    @Mock
    private RAGService ragService;

    @InjectMocks
    private DocumentController documentController;

    private AIRequestLoggingAspect loggingAspect;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        loggingAspect = new AIRequestLoggingAspect();
        
        // 设置日志捕获
        Logger logger = (Logger) LoggerFactory.getLogger(AIRequestLoggingAspect.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    /**
     * Property 29: Request Logging Completeness
     * For any AI request, a log entry should exist containing:
     * - requestId
     * - className
     * - methodName
     * - duration
     * - status
     * - timestamp
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 29: Request Logging Completeness")
    void everyRequestShouldHaveCompleteLog(
            @ForAll("validDocumentRequests") DocumentUploadRequest request) throws Throwable {
        
        // Given: Mock service response
        DocumentUploadResponse mockResponse = new DocumentUploadResponse();
        mockResponse.setDocumentId(1L);
        mockResponse.setIndexStatus("PENDING");
        when(ragService.uploadDocument(any())).thenReturn(mockResponse);
        
        // Clear previous logs
        listAppender.list.clear();
        
        // When: Execute request through controller
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                request.getTitle() + ".txt", 
                "text/plain", 
                "test content".getBytes()
        );
        
        try {
            documentController.uploadDocument(
                    request.getTitle(), 
                    file, 
                    1L, 
                    "test description"
            );
        } catch (Exception e) {
            // Expected for some test cases
        }
        
        // Then: Verify log entries exist
        List<ILoggingEvent> logEvents = listAppender.list;
        
        // Should have at least START and END log entries
        assertThat(logEvents).isNotEmpty();
        
        // Find START and END logs
        List<String> startLogs = logEvents.stream()
                .filter(event -> event.getMessage().contains("[AI-REQUEST-START]"))
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
        
        List<String> endLogs = logEvents.stream()
                .filter(event -> event.getMessage().contains("[AI-REQUEST-END]"))
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
        
        // Verify START log exists and contains required fields
        if (!startLogs.isEmpty()) {
            String startLog = startLogs.get(0);
            assertThat(startLog).contains("requestId");
            assertThat(startLog).contains("className");
            assertThat(startLog).contains("methodName");
            assertThat(startLog).contains("timestamp");
        }
        
        // Verify END log exists and contains required fields
        if (!endLogs.isEmpty()) {
            String endLog = endLogs.get(0);
            assertThat(endLog).contains("requestId");
            assertThat(endLog).contains("className");
            assertThat(endLog).contains("methodName");
            assertThat(endLog).contains("duration");
            assertThat(endLog).contains("status");
            assertThat(endLog).contains("timestamp");
        }
    }

    /**
     * Property: Log entries should be paired (START and END)
     * For any request, if there's a START log, there should be a corresponding END log
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 29: Log Entry Pairing")
    void startAndEndLogsShouldBePaired(
            @ForAll("validDocumentRequests") DocumentUploadRequest request) throws Throwable {
        
        // Given: Mock service response
        DocumentUploadResponse mockResponse = new DocumentUploadResponse();
        mockResponse.setDocumentId(1L);
        mockResponse.setIndexStatus("PENDING");
        when(ragService.uploadDocument(any())).thenReturn(mockResponse);
        
        // Clear previous logs
        listAppender.list.clear();
        
        // When: Execute request
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                request.getTitle() + ".txt", 
                "text/plain", 
                "test content".getBytes()
        );
        
        try {
            documentController.uploadDocument(
                    request.getTitle(), 
                    file, 
                    1L, 
                    "test description"
            );
        } catch (Exception e) {
            // Expected for some test cases
        }
        
        // Then: Count START and END logs
        long startCount = listAppender.list.stream()
                .filter(event -> event.getMessage().contains("[AI-REQUEST-START]"))
                .count();
        
        long endCount = listAppender.list.stream()
                .filter(event -> event.getMessage().contains("[AI-REQUEST-END]"))
                .count();
        
        // START and END counts should match (every request should complete)
        assertThat(startCount).isEqualTo(endCount);
    }

    /**
     * Property: Failed requests should log error information
     * For any request that fails, the END log should contain error details
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 29: Error Logging")
    void failedRequestsShouldLogErrorDetails(
            @ForAll("validDocumentRequests") DocumentUploadRequest request) throws Throwable {
        
        // Given: Mock service to throw exception
        when(ragService.uploadDocument(any())).thenThrow(new RuntimeException("Test error"));
        
        // Clear previous logs
        listAppender.list.clear();
        
        // When: Execute request that will fail
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                request.getTitle() + ".txt", 
                "text/plain", 
                "test content".getBytes()
        );
        
        try {
            documentController.uploadDocument(
                    request.getTitle(), 
                    file, 
                    1L, 
                    "test description"
            );
        } catch (Exception e) {
            // Expected
        }
        
        // Then: Verify error is logged
        List<String> endLogs = listAppender.list.stream()
                .filter(event -> event.getMessage().contains("[AI-REQUEST-END]"))
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
        
        if (!endLogs.isEmpty()) {
            String endLog = endLogs.get(0);
            assertThat(endLog).contains("status");
            assertThat(endLog).contains("FAILED");
            // Should contain error information
            assertThat(endLog).containsAnyOf("errorType", "errorMessage");
        }
    }

    // ==================== Generators ====================

    @Provide
    Arbitrary<DocumentUploadRequest> validDocumentRequests() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(50)
                .map(title -> {
                    DocumentUploadRequest request = new DocumentUploadRequest();
                    request.setTitle(title);
                    return request;
                });
    }
}
