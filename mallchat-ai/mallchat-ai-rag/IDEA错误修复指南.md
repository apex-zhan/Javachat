# IDEA 错误修复指南

## 问题总结

根据您提供的截图，RAG 模块中存在以下错误：

### 1. JetCacheUtils.create() 参数顺序错误 ✅ 已修复

**问题**：
```java
// 错误的参数顺序
Cache<Long, KnowledgeDocument> cache = JetCacheUtils.create(
    CacheType.BOTH,                    // ❌ 第一个参数应该是 name
    "ai:document:metadata:",           // ❌ 第二个参数应该是 cacheType
    Duration.ofMinutes(30),
    true
);
```

**修复**：
```java
// 正确的参数顺序
Cache<Long, KnowledgeDocument> cache = JetCacheUtils.create(
    "ai:document:metadata:",           // ✅ name
    CacheType.BOTH,                    // ✅ cacheType
    Duration.ofMinutes(30),            // ✅ expire
    true                               // ✅ cacheNullValue
);
```

**修复位置**：
- `DocumentMetadataCache.java` 中的 3 处调用已全部修复

---

### 2. 缺少 mallchat-cache-starter 依赖 ✅ 已修复

**问题**：
- `pom.xml` 中缺少 `mallchat-cache-starter` 依赖
- 导致 `JetCacheUtils` 类无法找到

**修复**：
已在 `mallchat-ai/mallchat-ai-rag/pom.xml` 中添加：
```xml
<!-- MallChat Cache Starter - for JetCache utilities -->
<dependency>
    <groupId>com.abin.mallchat.cache</groupId>
    <artifactId>mallchat-cache-starter</artifactId>
</dependency>
```

---

## 修复步骤

### 步骤 1: 刷新 Maven 项目

在 IDEA 中执行以下操作：

1. **打开 Maven 工具窗口**
   - 点击右侧的 "Maven" 标签
   - 或使用快捷键：`Ctrl + Shift + O` (Windows) / `Cmd + Shift + O` (Mac)

2. **重新加载所有 Maven 项目**
   - 点击 Maven 工具窗口左上角的 "刷新" 图标（圆形箭头）
   - 或右键点击项目 → "Maven" → "Reload Project"

3. **等待依赖下载完成**
   - 查看 IDEA 底部的进度条
   - 确保所有依赖都下载成功

### 步骤 2: 清理并重新编译

```bash
# 在项目根目录执行
mvn clean install -DskipTests
```

或在 IDEA 中：
1. 打开 Maven 工具窗口
2. 展开 "Lifecycle"
3. 双击 "clean"
4. 双击 "install"

### 步骤 3: 使缓存失效并重启

1. **使缓存失效**
   - 菜单：`File` → `Invalidate Caches...`
   - 勾选 "Invalidate and Restart"
   - 点击 "Invalidate and Restart"

2. **等待 IDEA 重启并重新索引**

### 步骤 4: 验证修复

1. **检查代码是否还有红色波浪线**
   - 打开 `DocumentMetadataCache.java`
   - 检查 `JetCacheUtils.create()` 调用是否还有错误

2. **检查依赖是否正确加载**
   - 打开 `External Libraries`
   - 查找 `mallchat-cache-starter`
   - 确认 `JetCacheUtils` 类可以找到

3. **运行编译测试**
   ```bash
   mvn compile -pl mallchat-ai/mallchat-ai-rag
   ```

---

## 常见问题排查

### 问题 1: 依赖下载失败

**症状**：
- Maven 刷新后仍然找不到 `JetCacheUtils`
- 控制台显示依赖下载错误

**解决方案**：
1. 检查网络连接
2. 检查 Maven 配置文件 `settings.xml`
3. 尝试使用国内镜像：
   ```xml
   <mirror>
       <id>aliyun</id>
       <mirrorOf>central</mirrorOf>
       <name>Aliyun Maven</name>
       <url>https://maven.aliyun.com/repository/public</url>
   </mirror>
   ```

### 问题 2: IDEA 索引未更新

**症状**：
- 代码修复后仍然显示红色波浪线
- 但 Maven 编译成功

**解决方案**：
1. 使缓存失效并重启（见步骤 3）
2. 或手动触发重新索引：
   - 菜单：`File` → `Invalidate Caches...`
   - 勾选 "Clear file system cache and Local History"
   - 点击 "Invalidate and Restart"

### 问题 3: Java 版本不匹配

**症状**：
- 编译时报错：`无效的目标发行版: 17`
- 或显示：`java: 不支持发行版本 17`

**解决方案**：
1. **检查 IDEA 项目 SDK**
   - `File` → `Project Structure` → `Project`
   - 确保 "SDK" 选择的是 Java 17

2. **检查 Maven 编译器配置**
   - `File` → `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven` → `Runner`
   - 确保 "JRE" 选择的是 Java 17

3. **检查系统 JAVA_HOME**
   ```bash
   # Windows
   echo %JAVA_HOME%
   
   # Linux/Mac
   echo $JAVA_HOME
   ```
   确保指向 Java 17 安装目录

### 问题 4: 循环依赖

**症状**：
- 编译时报错：`Circular dependency detected`
- 或 IDEA 显示循环依赖警告

**解决方案**：
检查 `pom.xml` 中是否有不必要的依赖，特别是：
```xml
<!-- 这个依赖可能导致循环依赖，如果不需要应该删除 -->
<dependency>
    <groupId>com.abin.mallchat</groupId>
    <artifactId>mallchat-chat-server</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```

---

## 验证清单

完成修复后，请检查以下项目：

- [ ] Maven 项目刷新成功
- [ ] 所有依赖下载完成
- [ ] `JetCacheUtils` 类可以找到
- [ ] `DocumentMetadataCache.java` 中没有红色波浪线
- [ ] `IndexStatusCache.java` 中没有红色波浪线
- [ ] `QueryResultCache.java` 中没有红色波浪线
- [ ] `StreamController.java` 中没有红色波浪线
- [ ] `RAGServiceImpl.java` 中没有红色波浪线
- [ ] Maven 编译成功：`mvn compile -pl mallchat-ai/mallchat-ai-rag`
- [ ] 单元测试可以运行（如果有）

---

## 如果问题仍然存在

如果按照以上步骤操作后问题仍然存在，请提供以下信息：

1. **IDEA 版本**
   - `Help` → `About`

2. **Java 版本**
   ```bash
   java -version
   ```

3. **Maven 版本**
   ```bash
   mvn -version
   ```

4. **具体错误信息**
   - 截图或复制完整的错误堆栈

5. **Maven 编译输出**
   ```bash
   mvn compile -pl mallchat-ai/mallchat-ai-rag -X > compile.log 2>&1
   ```
   提供 `compile.log` 文件内容

---

## 更新日志

| 日期 | 修复内容 | 状态 |
|------|---------|------|
| 2026-02-06 | 修复 JetCacheUtils.create() 参数顺序 | ✅ 完成 |
| 2026-02-06 | 添加 mallchat-cache-starter 依赖 | ✅ 完成 |

