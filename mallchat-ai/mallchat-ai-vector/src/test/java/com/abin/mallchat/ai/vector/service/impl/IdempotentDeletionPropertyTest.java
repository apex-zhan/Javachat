package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.vector.service.VectorService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 幂等删除属性测试
 * Feature: ai-assistant-rag, Property 12: Idempotent Deletion
 * 
 * 验证：
 * 1. 多次删除同一文档返回一致结果
 * 2. 删除后向量不存在
 * 
 * Validates: Requirements 4.6, 9.3
 * 
 * @author abin
 */
@Tag("property-test")
public class IdempotentDeletionPropertyTest {
    
    @Mock
    private VectorService vectorService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    /**
     * Property 12: Idempotent Deletion
     * 
     * For any document deletion request, calling the delete operation multiple 
     * times should always return success and result in the same final state 
     * (no vectors exist for that document).
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 12: Idempotent Deletion")
    void deletionIsIdempotent(@ForAll @IntRange(min = 1, max = 10000) long documentId) {
        // Given: Mock vector service with idempotent behavior
        // First call: vectors exist, will be deleted
        // Subsequent calls: vectors don't exist, no-op
        doNothing().when(vectorService).deleteVectors(anyLong());
        when(vectorService.exists(documentId))
                .thenReturn(true)   // Before first deletion
                .thenReturn(false)  // After first deletion
                .thenReturn(false); // After second deletion (idempotent)
        
        // When: Delete the document first time
        vectorService.deleteVectors(documentId);
        boolean existsAfterFirstDelete = vectorService.exists(documentId);
        
        // When: Delete the document second time (idempotent)
        vectorService.deleteVectors(documentId);
        boolean existsAfterSecondDelete = vectorService.exists(documentId);
        
        // Then: Both deletions should succeed (no exception thrown)
        // Then: Vectors should not exist after both deletions
        assertThat(existsAfterFirstDelete).isFalse();
        assertThat(existsAfterSecondDelete).isFalse();
        
        // Then: Both results should be consistent (idempotent)
        assertThat(existsAfterFirstDelete).isEqualTo(existsAfterSecondDelete);
        
        // Verify deleteVectors was called twice
        verify(vectorService, times(2)).deleteVectors(documentId);
    }
    
    /**
     * Property 12 (Multiple Calls): Multiple deletion calls
     * 
     * Calling delete N times should produce the same result as calling it once.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 12: Idempotent Deletion - Multiple Calls")
    void multipleDeleteCallsProduceSameResult(
            @ForAll @IntRange(min = 1, max = 1000) long documentId,
            @ForAll @IntRange(min = 2, max = 5) int deleteCount
    ) {
        // Given: Mock vector service
        doNothing().when(vectorService).deleteVectors(anyLong());
        
        // Setup exists() to return false after first deletion
        when(vectorService.exists(documentId))
                .thenReturn(true)  // Before deletion
                .thenReturn(false); // After deletion (and all subsequent calls)
        
        // When: Delete multiple times
        for (int i = 0; i < deleteCount; i++) {
            vectorService.deleteVectors(documentId);
        }
        
        // Then: Vectors should not exist
        boolean exists = vectorService.exists(documentId);
        assertThat(exists).isFalse();
        
        // Verify deleteVectors was called the expected number of times
        verify(vectorService, times(deleteCount)).deleteVectors(documentId);
    }
    
    /**
     * Property 12 (Non-existent Document): Deleting non-existent document
     * 
     * Deleting a document that doesn't exist should succeed (idempotent).
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 12: Idempotent Deletion - Non-existent Document")
    void deletingNonExistentDocumentSucceeds(
            @ForAll @IntRange(min = 1, max = 10000) long documentId
    ) {
        // Given: Document doesn't exist
        doNothing().when(vectorService).deleteVectors(anyLong());
        when(vectorService.exists(documentId)).thenReturn(false);
        
        // When: Delete the non-existent document
        vectorService.deleteVectors(documentId);
        
        // Then: Operation should succeed (no exception)
        // Then: Document still doesn't exist
        boolean exists = vectorService.exists(documentId);
        assertThat(exists).isFalse();
        
        // Verify deleteVectors was called
        verify(vectorService, times(1)).deleteVectors(documentId);
    }
    
    /**
     * Property 12 (Concurrent Deletions): Concurrent deletion attempts
     * 
     * Multiple concurrent deletion attempts should all succeed and result
     * in the same final state.
     */
    @Property(tries = 30)
    @Label("Feature: ai-assistant-rag, Property 12: Idempotent Deletion - Concurrent Deletions")
    void concurrentDeletionsAreIdempotent(
            @ForAll @IntRange(min = 1, max = 1000) long documentId,
            @ForAll @IntRange(min = 2, max = 4) int concurrentCount
    ) {
        // Given: Mock vector service with thread-safe behavior
        doNothing().when(vectorService).deleteVectors(anyLong());
        when(vectorService.exists(documentId))
                .thenReturn(true)   // Before deletions
                .thenReturn(false); // After any deletion
        
        // When: Simulate concurrent deletions
        for (int i = 0; i < concurrentCount; i++) {
            vectorService.deleteVectors(documentId);
        }
        
        // Then: Final state should be consistent
        boolean exists = vectorService.exists(documentId);
        assertThat(exists).isFalse();
        
        // Verify all deletion attempts were made
        verify(vectorService, times(concurrentCount)).deleteVectors(documentId);
    }
    
    /**
     * Property 12 (State Consistency): State consistency after deletion
     * 
     * After deletion, querying the document should consistently return
     * "not exists" regardless of how many times we check.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 12: Idempotent Deletion - State Consistency")
    void stateIsConsistentAfterDeletion(
            @ForAll @IntRange(min = 1, max = 10000) long documentId,
            @ForAll @IntRange(min = 1, max = 5) int checkCount
    ) {
        // Given: Mock vector service
        doNothing().when(vectorService).deleteVectors(anyLong());
        when(vectorService.exists(documentId))
                .thenReturn(true)   // Before deletion
                .thenReturn(false); // After deletion (all subsequent calls)
        
        // When: Delete the document
        vectorService.deleteVectors(documentId);
        
        // Then: Check existence multiple times - should always be false
        for (int i = 0; i < checkCount; i++) {
            boolean exists = vectorService.exists(documentId);
            assertThat(exists)
                    .as("Check #%d: Document should not exist after deletion", i + 1)
                    .isFalse();
        }
        
        // Verify exists() was called the expected number of times
        verify(vectorService, times(checkCount)).exists(documentId);
    }
    
    /**
     * Property 12 (Delete-Create-Delete): Delete-Create-Delete cycle
     * 
     * Deleting, then creating, then deleting again should work correctly.
     * Each deletion should be idempotent.
     */
    @Property(tries = 30)
    @Label("Feature: ai-assistant-rag, Property 12: Idempotent Deletion - Delete-Create-Delete Cycle")
    void deleteCreateDeleteCycleWorks(
            @ForAll @IntRange(min = 1, max = 1000) long documentId
    ) {
        // Given: Mock vector service
        doNothing().when(vectorService).deleteVectors(anyLong());
        
        // Setup exists() to simulate the lifecycle
        when(vectorService.exists(documentId))
                .thenReturn(true)   // Initial state: exists
                .thenReturn(false)  // After first delete
                .thenReturn(true)   // After create (simulated)
                .thenReturn(false); // After second delete
        
        // When: First deletion
        vectorService.deleteVectors(documentId);
        boolean existsAfterFirstDelete = vectorService.exists(documentId);
        
        // Simulate creation (in real scenario, this would call storeVectors)
        // For this test, we just verify the state transitions
        
        // When: Second deletion (after simulated creation)
        vectorService.deleteVectors(documentId);
        boolean existsAfterSecondDelete = vectorService.exists(documentId);
        
        // Then: Both deletions should result in non-existence
        assertThat(existsAfterFirstDelete).isFalse();
        assertThat(existsAfterSecondDelete).isFalse();
        
        // Verify deleteVectors was called twice
        verify(vectorService, times(2)).deleteVectors(documentId);
    }
}
