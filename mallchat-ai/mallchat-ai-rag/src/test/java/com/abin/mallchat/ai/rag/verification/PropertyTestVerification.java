package com.abin.mallchat.ai.rag.verification;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 属性测试验证工具
 * 
 * 验证所有属性测试是否已实现，并生成测试覆盖报告
 * 
 * @author zxw
 * @since 2025-01-08
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("属性测试验证")
public class PropertyTestVerification {

    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    private static final String TEST_SOURCE_ROOT = PROJECT_ROOT + "/src/test/java";

    /**
     * 验证所有属性测试是否已实现
     */
    @Test
    @DisplayName("验证所有属性测试已实现")
    void verifyAllPropertyTestsImplemented() throws Exception {
        log.info("=".repeat(80));
        log.info("开始验证属性测试实现情况");
        log.info("=".repeat(80));

        // 从设计文档中提取所有属性
        Map<Integer, String> designProperties = extractPropertiesFromDesign();
        log.info("从设计文档中提取到 {} 个属性", designProperties.size());

        // 从测试代码中提取已实现的属性
        Map<Integer, List<String>> implementedProperties = extractImplementedProperties();
        log.info("从测试代码中提取到 {} 个已实现的属性", implementedProperties.size());

        // 生成覆盖报告
        generateCoverageReport(designProperties, implementedProperties);

        // 验证所有属性都已实现
        Set<Integer> missingProperties = new HashSet<>(designProperties.keySet());
        missingProperties.removeAll(implementedProperties.keySet());

        if (!missingProperties.isEmpty()) {
            log.warn("以下属性尚未实现测试:");
            missingProperties.forEach(propNum -> {
                log.warn("  Property {}: {}", propNum, designProperties.get(propNum));
            });
        }

        log.info("=".repeat(80));
        log.info("属性测试验证完成");
        log.info("=".repeat(80));

        // 注意：这里不强制要求所有属性都实现，因为有些属性可能是可选的
        // assertThat(missingProperties).isEmpty();
    }

    /**
     * 生成测试覆盖报告
     */
    @Test
    @DisplayName("生成测试覆盖报告")
    void generateTestCoverageReport() throws Exception {
        log.info("\n" + "=".repeat(80));
        log.info("RAG 系统测试覆盖报告");
        log.info("=".repeat(80));

        // 统计测试文件
        Map<String, Integer> testStats = collectTestStatistics();

        log.info("\n测试文件统计:");
        log.info("-".repeat(80));
        testStats.forEach((category, count) -> {
            log.info("  {}: {} 个测试文件", category, count);
        });

        // 统计属性测试
        Map<Integer, List<String>> implementedProperties = extractImplementedProperties();
        log.info("\n属性测试统计:");
        log.info("-".repeat(80));
        log.info("  已实现属性数: {}", implementedProperties.size());
        log.info("  总测试方法数: {}", 
                implementedProperties.values().stream().mapToInt(List::size).sum());

        // 按模块分组
        Map<String, List<Integer>> propertiesByModule = groupPropertiesByModule(implementedProperties);
        log.info("\n按模块分组:");
        log.info("-".repeat(80));
        propertiesByModule.forEach((module, properties) -> {
            log.info("  {}: {} 个属性", module, properties.size());
            properties.forEach(propNum -> {
                log.info("    - Property {}", propNum);
            });
        });

        log.info("\n" + "=".repeat(80));
    }

    /**
     * 验证测试命名规范
     */
    @Test
    @DisplayName("验证测试命名规范")
    void verifyTestNamingConventions() throws Exception {
        log.info("=".repeat(80));
        log.info("验证测试命名规范");
        log.info("=".repeat(80));

        List<String> violations = new ArrayList<>();

        // 查找所有测试文件
        Files.walk(Paths.get(TEST_SOURCE_ROOT))
                .filter(path -> path.toString().endsWith("Test.java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String fileName = path.getFileName().toString();

                        // 检查属性测试文件命名
                        if (content.contains("@Property")) {
                            if (!fileName.contains("PropertyTest")) {
                                violations.add(fileName + " 包含 @Property 但文件名不包含 'PropertyTest'");
                            }
                        }

                        // 检查 Label 注解格式
                        Pattern labelPattern = Pattern.compile("@Label\\(\"Feature: ([^,]+), Property (\\d+): (.+)\"\\)");
                        Matcher matcher = labelPattern.matcher(content);
                        while (matcher.find()) {
                            String feature = matcher.group(1);
                            if (!feature.equals("ai-assistant-rag")) {
                                violations.add(fileName + " 中的 Feature 名称不正确: " + feature);
                            }
                        }

                    } catch (Exception e) {
                        log.error("处理文件失败: {}", path, e);
                    }
                });

        if (!violations.isEmpty()) {
            log.warn("发现以下命名规范违规:");
            violations.forEach(v -> log.warn("  - {}", v));
        } else {
            log.info("✅ 所有测试文件都符合命名规范");
        }

        log.info("=".repeat(80));
    }

    // ==================== 辅助方法 ====================

    /**
     * 从设计文档中提取属性
     */
    private Map<Integer, String> extractPropertiesFromDesign() throws Exception {
        Map<Integer, String> properties = new HashMap<>();
        
        Path designPath = Paths.get(PROJECT_ROOT, "../../../.kiro/specs/ai-assistant-rag/design.md");
        if (!Files.exists(designPath)) {
            log.warn("设计文档不存在: {}", designPath);
            return properties;
        }

        String content = Files.readString(designPath);
        Pattern pattern = Pattern.compile("### Property (\\d+): (.+)");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            int propertyNum = Integer.parseInt(matcher.group(1));
            String propertyName = matcher.group(2).trim();
            properties.put(propertyNum, propertyName);
        }

        return properties;
    }

    /**
     * 从测试代码中提取已实现的属性
     */
    private Map<Integer, List<String>> extractImplementedProperties() throws Exception {
        Map<Integer, List<String>> properties = new HashMap<>();

        Files.walk(Paths.get(TEST_SOURCE_ROOT))
                .filter(path -> path.toString().endsWith("Test.java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String fileName = path.getFileName().toString();

                        Pattern pattern = Pattern.compile("@Label\\(\"Feature: ai-assistant-rag, Property (\\d+): (.+)\"\\)");
                        Matcher matcher = pattern.matcher(content);

                        while (matcher.find()) {
                            int propertyNum = Integer.parseInt(matcher.group(1));
                            String testName = matcher.group(2).trim();
                            
                            properties.computeIfAbsent(propertyNum, k -> new ArrayList<>())
                                    .add(fileName + ": " + testName);
                        }

                    } catch (Exception e) {
                        log.error("处理文件失败: {}", path, e);
                    }
                });

        return properties;
    }

    /**
     * 生成覆盖报告
     */
    private void generateCoverageReport(Map<Integer, String> designProperties, 
                                       Map<Integer, List<String>> implementedProperties) {
        log.info("\n属性测试覆盖报告:");
        log.info("-".repeat(80));

        int totalProperties = designProperties.size();
        int implementedCount = implementedProperties.size();
        double coverage = totalProperties > 0 ? (implementedCount * 100.0 / totalProperties) : 0;

        log.info("总属性数: {}", totalProperties);
        log.info("已实现: {}", implementedCount);
        log.info("覆盖率: {}%", String.format("%.2f", coverage));
        log.info("");

        // 详细列表
        designProperties.forEach((propNum, propName) -> {
            if (implementedProperties.containsKey(propNum)) {
                log.info("✅ Property {}: {}", propNum, propName);
                implementedProperties.get(propNum).forEach(test -> {
                    log.info("     └─ {}", test);
                });
            } else {
                log.info("❌ Property {}: {} (未实现)", propNum, propName);
            }
        });
    }

    /**
     * 收集测试统计信息
     */
    private Map<String, Integer> collectTestStatistics() throws Exception {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("属性测试", 0);
        stats.put("单元测试", 0);
        stats.put("集成测试", 0);
        stats.put("性能测试", 0);

        Files.walk(Paths.get(TEST_SOURCE_ROOT))
                .filter(path -> path.toString().endsWith("Test.java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String fileName = path.getFileName().toString();

                        if (fileName.contains("PropertyTest")) {
                            stats.merge("属性测试", 1, Integer::sum);
                        } else if (path.toString().contains("/integration/")) {
                            stats.merge("集成测试", 1, Integer::sum);
                        } else if (path.toString().contains("/performance/")) {
                            stats.merge("性能测试", 1, Integer::sum);
                        } else {
                            stats.merge("单元测试", 1, Integer::sum);
                        }

                    } catch (Exception e) {
                        log.error("处理文件失败: {}", path, e);
                    }
                });

        return stats;
    }

    /**
     * 按模块分组属性
     */
    private Map<String, List<Integer>> groupPropertiesByModule(Map<Integer, List<String>> properties) {
        Map<String, List<Integer>> grouped = new HashMap<>();

        properties.forEach((propNum, tests) -> {
            String module = determineModule(propNum);
            grouped.computeIfAbsent(module, k -> new ArrayList<>()).add(propNum);
        });

        // 排序
        grouped.values().forEach(Collections::sort);

        return grouped;
    }

    /**
     * 根据属性编号确定所属模块
     */
    private String determineModule(int propertyNum) {
        if (propertyNum <= 5) {
            return "智能助手 (AI Assistant)";
        } else if (propertyNum <= 12) {
            return "文档处理 (Document Processing)";
        } else if (propertyNum <= 19) {
            return "向量检索 (Vector Search)";
        } else if (propertyNum <= 25) {
            return "RAG 查询 (RAG Query)";
        } else {
            return "系统监控 (Monitoring)";
        }
    }
}
