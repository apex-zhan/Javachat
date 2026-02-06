package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.rag.config.DocumentConfig;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature: ai-assistant-rag, Property 6: Input Validation Completeness
 * Validates: Requirements 2.1, 4.1
 * 
 * 验证各种非法输入被正确拒绝，验证合法输入通过验证
 * 
 * @author zxw
 */
class InputValidationCompletenessPropertyTest {
    
    private RAGServiceImpl ragService;
    private DocumentConfig documentConfig;
    
    @BeforeEach
    void setUp() {
        ragService = new RAGServiceImpl();
        documentConfig = new DocumentConfig();
        documentConfig.setAllowedFormats(Arrays.asList("txt", "pdf", "md", "html", "docx", "doc"));
        documentConfig.setMaxFileSize(10 * 1024 * 1024L); // 10MB
        
        // 使用反射设置私有字段
        try {
            java.lang.reflect.Field field = RAGServiceImpl.class.getDeclaredField("documentConfig");
            field.setAccessible(true);
            field.set(ragService, documentConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject documentConfig", e);
        }
    }
    
    /**
     * Property 6.1: 空文件应该被拒绝
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 6.1: Empty files should be rejected")
    void emptyFilesShouldBeRejected() {
        // Given: 空文件
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[0]);
        
        // When & Then: 验证应该抛出异常
        assertThatThrownBy(() -> invokeValidateDocument(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档文件不能为空");
    }
    
    /**
     * Property 6.2: 超大文件应该被拒绝
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 6.2: Oversized files should be rejected")
    void oversizedFilesShouldBeRejected(
            @ForAll @IntRange(min = 11, max = 100) int sizeMB) {
        
        // Given: 超过限制的文件
        long fileSize = sizeMB * 1024L * 1024L;
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[(int) Math.min(fileSize, Integer.MAX_VALUE)]);
        
        // When & Then: 验证应该抛出异常
        assertThatThrownBy(() -> invokeValidateDocument(largeFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档大小超过限制");
    }
    
    /**
     * Property 6.3: 不支持的文件格式应该被拒绝
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 6.3: Unsupported file formats should be rejected")
    void unsupportedFormatsShouldBeRejected(
            @ForAll("unsupportedFormats") String extension) {
        
        // Given: 不支持的文件格式
        String filename = "test." + extension;
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "application/octet-stream", "content".getBytes());
        
        // When & Then: 验证应该抛出异常
        assertThatThrownBy(() -> invokeValidateDocument(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文档格式");
    }
    
    /**
     * Property 6.4: 合法文件应该通过验证
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 6.4: Valid files should pass validation")
    void validFilesShouldPassValidation(
            @ForAll("supportedFormats") String extension,
            @ForAll @IntRange(min = 1, max = 10) int sizeMB,
            @ForAll @StringLength(min = 1, max = 100) String content) {
        
        // Given: 合法的文件
        String filename = "test." + extension;
        long fileSize = sizeMB * 1024L * 1024L;
        byte[] fileContent = content.getBytes();
        
        // 确保文件大小在限制内
        if (fileSize > documentConfig.getMaxFileSize()) {
            fileSize = documentConfig.getMaxFileSize() - 1;
        }
        
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", fileContent);
        
        // When & Then: 验证应该成功（不抛出异常）
        try {
            invokeValidateDocument(file);
            // 如果没有抛出异常，说明验证通过
            assertThat(true).isTrue();
        } catch (Exception e) {
            // 如果抛出异常，说明验证失败
            throw new AssertionError("Valid file should pass validation", e);
        }
    }
    
    /**
     * Property 6.5: null文件应该被拒绝
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 6.5: Null files should be rejected")
    void nullFilesShouldBeRejected() {
        // When & Then: 验证应该抛出异常
        assertThatThrownBy(() -> invokeValidateDocument(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档文件不能为空");
    }
    
    /**
     * Property 6.6: 文件名中的扩展名应该被正确识别
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 6.6: File extensions should be correctly identified")
    void fileExtensionsShouldBeCorrectlyIdentified(
            @ForAll("supportedFormats") String extension,
            @ForAll @StringLength(min = 1, max = 50) String basename) {
        
        // Given: 带有扩展名的文件
        String filename = basename + "." + extension;
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", "content".getBytes());
        
        // When & Then: 验证应该成功
        try {
            invokeValidateDocument(file);
            assertThat(true).isTrue();
        } catch (Exception e) {
            throw new AssertionError("File with valid extension should pass validation", e);
        }
    }
    
    /**
     * Property 6.7: 大小写不敏感的扩展名应该被接受
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 6.7: Case-insensitive extensions should be accepted")
    void caseInsensitiveExtensionsShouldBeAccepted(
            @ForAll("supportedFormats") String extension,
            @ForAll boolean upperCase) {
        
        // Given: 大小写变化的扩展名
        String actualExtension = upperCase ? extension.toUpperCase() : extension;
        String filename = "test." + actualExtension;
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", "content".getBytes());
        
        // When & Then: 验证应该成功
        try {
            invokeValidateDocument(file);
            assertThat(true).isTrue();
        } catch (Exception e) {
            throw new AssertionError("File with case-insensitive extension should pass validation", e);
        }
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<String> supportedFormats() {
        return Arbitraries.of("txt", "pdf", "md", "html", "docx", "doc");
    }
    
    @Provide
    Arbitrary<String> unsupportedFormats() {
        return Arbitraries.of("exe", "zip", "rar", "jpg", "png", "mp4", "avi", "mp3");
    }
    
    // ========== Helper Methods ==========
    
    /**
     * 使用反射调用私有的validateDocument方法
     */
    private void invokeValidateDocument(MultipartFile file) throws Exception {
        try {
            java.lang.reflect.Method method = RAGServiceImpl.class.getDeclaredMethod(
                    "validateDocument", MultipartFile.class);
            method.setAccessible(true);
            method.invoke(ragService, file);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 如果是业务异常，重新抛出
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        }
    }
}
