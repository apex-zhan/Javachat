package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.rag.domain.dto.ChunkMetadata;
import com.abin.mallchat.ai.rag.aspect.ChunkStrategy;
import com.abin.mallchat.ai.rag.service.DocumentProcessingService;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分块元数据完整性属性测试
 * Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness
 * 
 * 验证：
 * 1. 每个分块包含必要元数据（document_id, chunk_index, source_location, create_timestamp）
 * 2. 元数据格式正确（可以正确序列化和反序列化）
 * 
 * Validates: Requirements 5.5
 * 
 * @author abin
 */
@Tag("property-test")
public class ChunkMetadataCompletenessPropertyTest {
    
    private DocumentProcessingService documentProcessingService;
    
    @BeforeEach
    void setUp() {
        documentProcessingService = new TikaDocumentProcessingService();
    }
    
    /**
     * Property 17: Chunk Metadata Completeness
     * 
     * For any document chunk, the metadata should include at minimum: 
     * document_id, chunk_index, source_location, and creation_timestamp.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness")
    void everyChunkShouldHaveCompleteMetadata(
            @ForAll("validDocumentContent") String content,
            @ForAll ChunkStrategy strategy
    ) {
        // When: Chunk document with any strategy
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(content, strategy);
        
        // Then: Every chunk should have complete metadata
        for (DocumentChunk chunk : chunks) {
            // 1. Verify metadata field exists
            assertThat(chunk.getMetadata())
                    .as("Chunk %d should have metadata", chunk.getChunkIndex())
                    .isNotNull()
                    .isNotEmpty();
            
            // 2. Verify metadata can be deserialized
            ChunkMetadata metadata = ChunkMetadata.fromJson(chunk.getMetadata());
            assertThat(metadata)
                    .as("Chunk %d metadata should be deserializable", chunk.getChunkIndex())
                    .isNotNull();
            
            // 3. Verify required fields are present
            assertThat(metadata.getChunkIndex())
                    .as("Chunk %d metadata should have chunk_index", chunk.getChunkIndex())
                    .isNotNull()
                    .isEqualTo(chunk.getChunkIndex());
            
            assertThat(metadata.getSourceLocation())
                    .as("Chunk %d metadata should have source_location", chunk.getChunkIndex())
                    .isNotNull()
                    .isGreaterThanOrEqualTo(0);
            
            assertThat(metadata.getSourceLocationEnd())
                    .as("Chunk %d metadata should have source_location_end", chunk.getChunkIndex())
                    .isNotNull()
                    .isGreaterThan(metadata.getSourceLocation());
            
            assertThat(metadata.getCreateTimestamp())
                    .as("Chunk %d metadata should have create_timestamp", chunk.getChunkIndex())
                    .isNotNull()
                    .isGreaterThan(0L);
            
            assertThat(metadata.getChunkStrategy())
                    .as("Chunk %d metadata should have chunk_strategy", chunk.getChunkIndex())
                    .isNotNull()
                    .isNotEmpty();
        }
    }
    
    /**
     * Property 17 (Format Correctness): Metadata format should be valid JSON
     * 
     * For any chunk metadata, it should be serializable to JSON and deserializable back
     * without data loss.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness - Format Correctness")
    void metadataShouldBeValidJson(
            @ForAll("validDocumentContent") String content
    ) {
        // When: Chunk document
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(
                content, 
                ChunkStrategy.FIXED_SIZE
        );
        
        // Then: Every chunk's metadata should be valid JSON
        for (DocumentChunk chunk : chunks) {
            String metadataJson = chunk.getMetadata();
            
            // Should be able to deserialize
            ChunkMetadata metadata = ChunkMetadata.fromJson(metadataJson);
            
            // Should be able to serialize back
            String reserializedJson = metadata.toJson();
            
            // Should be able to deserialize again
            ChunkMetadata metadata2 = ChunkMetadata.fromJson(reserializedJson);
            
            // All fields should match
            assertThat(metadata2.getChunkIndex()).isEqualTo(metadata.getChunkIndex());
            assertThat(metadata2.getSourceLocation()).isEqualTo(metadata.getSourceLocation());
            assertThat(metadata2.getSourceLocationEnd()).isEqualTo(metadata.getSourceLocationEnd());
            assertThat(metadata2.getChunkStrategy()).isEqualTo(metadata.getChunkStrategy());
            assertThat(metadata2.getCreateTimestamp()).isEqualTo(metadata.getCreateTimestamp());
        }
    }
    
    /**
     * Property 17 (Document Metadata): Document-level metadata should be preserved
     * 
     * When document metadata is set, all chunks should contain the document information.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness - Document Metadata")
    void documentMetadataShouldBePreservedInChunks(
            @ForAll("validDocumentContent") String content,
            @ForAll("validDocumentId") Long documentId,
            @ForAll("validDocumentTitle") String documentTitle,
            @ForAll("validDocumentType") String documentType
    ) {
        // Given: Chunks with document metadata
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(
                content, 
                ChunkStrategy.FIXED_SIZE
        );
        
        // When: Set document metadata
        if (documentProcessingService instanceof TikaDocumentProcessingService) {
            TikaDocumentProcessingService service = (TikaDocumentProcessingService) documentProcessingService;
            service.setDocumentMetadata(chunks, documentId, documentTitle, documentType);
        }
        
        // Then: All chunks should have document metadata
        for (DocumentChunk chunk : chunks) {
            assertThat(chunk.getDocumentId()).isEqualTo(documentId);
            
            ChunkMetadata metadata = ChunkMetadata.fromJson(chunk.getMetadata());
            assertThat(metadata.getDocumentId()).isEqualTo(documentId);
            assertThat(metadata.getDocumentTitle()).isEqualTo(documentTitle);
            assertThat(metadata.getDocumentType()).isEqualTo(documentType);
        }
    }
    
    /**
     * Property 17 (Source Location Validity): Source locations should be valid
     * 
     * For any chunk, the source location should be within the original content bounds.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness - Source Location Validity")
    void sourceLocationsShouldBeValid(
            @ForAll("validDocumentContent") String content
    ) {
        // When: Chunk document
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(
                content, 
                ChunkStrategy.FIXED_SIZE
        );
        
        int contentLength = content.length();
        
        // Then: All source locations should be valid
        for (DocumentChunk chunk : chunks) {
            ChunkMetadata metadata = ChunkMetadata.fromJson(chunk.getMetadata());
            
            // Source location should be within content bounds
            assertThat(metadata.getSourceLocation())
                    .as("Chunk %d source_location should be within content", chunk.getChunkIndex())
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(contentLength);
            
            assertThat(metadata.getSourceLocationEnd())
                    .as("Chunk %d source_location_end should be within content", chunk.getChunkIndex())
                    .isGreaterThan(metadata.getSourceLocation())
                    .isLessThanOrEqualTo(contentLength);
        }
    }
    
    /**
     * Property 17 (Sequential Locations): Source locations should be sequential
     * 
     * For consecutive chunks, source locations should progress forward.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness - Sequential Locations")
    void sourceLocationsShouldBeSequential(
            @ForAll("longDocumentContent") String content
    ) {
        // When: Chunk document
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(
                content, 
                ChunkStrategy.FIXED_SIZE
        );
        
        // Skip if only one chunk
        if (chunks.size() <= 1) {
            return;
        }
        
        // Then: Source locations should progress forward
        for (int i = 0; i < chunks.size() - 1; i++) {
            ChunkMetadata currentMetadata = ChunkMetadata.fromJson(chunks.get(i).getMetadata());
            ChunkMetadata nextMetadata = ChunkMetadata.fromJson(chunks.get(i + 1).getMetadata());
            
            // Next chunk should start at or after current chunk starts
            assertThat(nextMetadata.getSourceLocation())
                    .as("Chunk %d source_location should be >= chunk %d source_location", 
                            i + 1, i)
                    .isGreaterThanOrEqualTo(currentMetadata.getSourceLocation());
        }
    }
    
    /**
     * Property 17 (Timestamp Validity): Create timestamps should be reasonable
     * 
     * For any chunk, the create timestamp should be a valid recent timestamp.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness - Timestamp Validity")
    void createTimestampsShouldBeValid(
            @ForAll("validDocumentContent") String content
    ) {
        // Given: Current time
        long beforeChunking = System.currentTimeMillis();
        
        // When: Chunk document
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(
                content, 
                ChunkStrategy.FIXED_SIZE
        );
        
        long afterChunking = System.currentTimeMillis();
        
        // Then: All timestamps should be within reasonable range
        for (DocumentChunk chunk : chunks) {
            ChunkMetadata metadata = ChunkMetadata.fromJson(chunk.getMetadata());
            
            assertThat(metadata.getCreateTimestamp())
                    .as("Chunk %d create_timestamp should be recent", chunk.getChunkIndex())
                    .isGreaterThanOrEqualTo(beforeChunking - 1000) // Allow 1 second before
                    .isLessThanOrEqualTo(afterChunking + 1000);    // Allow 1 second after
        }
    }
    
    /**
     * Property 17 (Strategy Consistency): Chunk strategy should match the requested strategy
     * 
     * For any chunking operation, the metadata should reflect the actual strategy used.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness - Strategy Consistency")
    void chunkStrategyShouldMatchRequest(
            @ForAll("validDocumentContent") String content,
            @ForAll ChunkStrategy strategy
    ) {
        // When: Chunk document with specific strategy
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(content, strategy);
        
        // Then: All chunks should have consistent strategy in metadata
        String expectedStrategy = strategy == ChunkStrategy.AUTO ? "FIXED_SIZE" : strategy.name();
        
        for (DocumentChunk chunk : chunks) {
            ChunkMetadata metadata = ChunkMetadata.fromJson(chunk.getMetadata());
            
            assertThat(metadata.getChunkStrategy())
                    .as("Chunk %d strategy should match requested strategy", chunk.getChunkIndex())
                    .isEqualTo(expectedStrategy);
        }
    }
    
    /**
     * Property 17 (Empty Content): Empty content should produce no chunks with metadata
     * 
     * When content is empty, no chunks should be created.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 17: Chunk Metadata Completeness - Empty Content")
    void emptyContentShouldProduceNoChunks() {
        // When: Chunk empty content
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(
                "", 
                ChunkStrategy.FIXED_SIZE
        );
        
        // Then: No chunks should be created
        assertThat(chunks).isEmpty();
    }
    
    // ==================== Arbitraries ====================
    
    /**
     * Generate valid document content (500-5000 characters)
     */
    @Provide
    Arbitrary<String> validDocumentContent() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '\n', '.', ',')
                .ofMinLength(500)
                .ofMaxLength(5000);
    }
    
    /**
     * Generate long document content (2000-10000 characters)
     */
    @Provide
    Arbitrary<String> longDocumentContent() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '\n', '.', ',')
                .ofMinLength(2000)
                .ofMaxLength(10000);
    }
    
    /**
     * Generate valid document IDs
     */
    @Provide
    Arbitrary<Long> validDocumentId() {
        return Arbitraries.longs().between(1L, 999999L);
    }
    
    /**
     * Generate valid document titles
     */
    @Provide
    Arbitrary<String> validDocumentTitle() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '-', '_')
                .ofMinLength(5)
                .ofMaxLength(100);
    }
    
    /**
     * Generate valid document types
     */
    @Provide
    Arbitrary<String> validDocumentType() {
        return Arbitraries.of("txt", "md", "html", "pdf", "doc", "docx");
    }
}
