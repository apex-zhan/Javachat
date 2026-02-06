package com.abin.mallchat.ai.rag.domain.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 文档上传请求DTO
 * 
 * @author zxw
 */
@Data
public class DocumentUploadRequest {
    
    /**
     * 文档标题
     */
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 255, message = "文档标题长度不能超过255个字符")
    private String title;
    
    /**
     * 文档文件
     */
    @NotNull(message = "文档文件不能为空")
    private MultipartFile file;
    
    /**
     * 上传用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
    /**
     * 文档描述（可选）
     */
    @Size(max = 1000, message = "文档描述长度不能超过1000个字符")
    private String description;
}
