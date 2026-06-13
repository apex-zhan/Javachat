# MallChat AI RAG 模块 API 接口文档

## 📋 文档信息

- **版本**：v1.1
- **创建时间**：2026-02-06
- **最后更新**：2026-06-13
- **作者**：Kiro / AI Assistant
- **Base URL**：`http://localhost:8080/api`

---

## 📑 目录

1. [文档管理接口](#1-文档管理接口)
2. [RAG 查询接口](#2-rag-查询接口)
3. [流式输出接口](#3-流式输出接口)
4. [通用说明](#4-通用说明)

---

## 1. 文档管理接口

### 1.1 上传文档

**接口描述**：上传知识库文档，支持 PDF、DOCX、TXT、MD、HTML 等格式

**请求方式**：`POST`

**请求路径**：`/documents/upload`

**Content-Type**：`multipart/form-data`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 文档文件 |
| title | String | 是 | 文档标题 |
| userId | Long | 是 | 用户ID |
| description | String | 否 | 文档描述 |

**请求示例**：

```bash
curl -X POST "http://localhost:8080/api/documents/upload" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/path/to/document.pdf" \
  -F "title=产品使用手册" \
  -F "userId=10001" \
  -F "description=产品使用手册 v1.0"
```

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| documentId | Long | 文档ID |
| title | String | 文档标题 |
| indexStatus | String | 索引状态（PENDING/INDEXING/COMPLETED/FAILED） |
| message | String | 提示信息 |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": 12345,
    "title": "产品使用手册",
    "indexStatus": "PENDING",
    "message": "文档上传成功，正在等待索引处理"
  }
}
```

**错误码**：

| 错误码 | 说明 |
|--------|------|
| 400 | 参数错误（文件为空、格式不支持、大小超限） |
| 429 | 上传过于频繁 |
| 500 | 服务器内部错误 |

---

### 1.2 更新文档

**接口描述**：更新已存在的文档内容

**请求方式**：`PUT`

**请求路径**：`/documents/{documentId}`

**Content-Type**：`multipart/form-data`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| documentId | Long | 是 | 文档ID |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 新文档文件 |
| title | String | 是 | 新文档标题 |
| userId | Long | 是 | 用户ID |
| description | String | 否 | 文档描述 |

**请求示例**：

```bash
curl -X PUT "http://localhost:8080/api/documents/12345" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/path/to/new_document.pdf" \
  -F "title=产品使用手册v2" \
  -F "userId=10001"
```

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| documentId | Long | 文档ID |
| title | String | 文档标题 |
| indexStatus | String | 索引状态 |
| message | String | 提示信息 |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": 12345,
    "title": "产品使用手册v2",
    "indexStatus": "PENDING",
    "message": "文档更新成功，正在等待索引处理"
  }
}
```

**错误码**：

| 错误码 | 说明 |
|--------|------|
| 400 | 参数错误 |
| 404 | 文档不存在 |
| 429 | 上传过于频繁 |
| 500 | 服务器内部错误 |

---

### 1.3 删除文档

**接口描述**：删除指定文档及其所有相关数据

**请求方式**：`DELETE`

**请求路径**：`/documents/{documentId}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| documentId | Long | 是 | 文档ID |

**请求示例**：

```bash
curl -X DELETE "http://localhost:8080/api/documents/12345"
```

**响应示例**：

```json
{
  "code": 200,
  "message": "文档删除成功",
  "data": null
}
```

**错误码**：

| 错误码 | 说明 |
|--------|------|
| 404 | 文档不存在 |
| 500 | 服务器内部错误 |

---

### 1.4 查询索引状态

**接口描述**：查询文档的索引处理状态

**请求方式**：`GET`

**请求路径**：`/documents/{documentId}/status`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| documentId | Long | 是 | 文档ID |

**请求示例**：

```bash
curl -X GET "http://localhost:8080/api/documents/12345/status"
```

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| documentId | Long | 文档ID |
| indexStatus | String | 索引状态 |
| chunkCount | Integer | 分块数量 |
| errorMessage | String | 错误信息（如果失败） |

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": 12345,
    "indexStatus": "COMPLETED",
    "chunkCount": 156,
    "errorMessage": null
  }
}
```

**索引状态说明**：

| 状态 | 说明 |
|------|------|
| PENDING | 等待处理 |
| INDEXING | 索引中 |
| COMPLETED | 索引完成 |
| FAILED | 索引失败 |

---

## 2. RAG 查询接口

### 2.1 简化流式 RAG 查询

**接口描述**：基于知识库的智能问答，直接返回文本流（由 `DocumentController` 提供）

**请求方式**：`POST`

**请求路径**：`/documents/query`

**Content-Type**：`application/json`

**Accept**：`text/event-stream`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| question | String | 是 | 用户问题 |
| userId | Long | 是 | 用户ID |
| documentId | Long | 否 | 指定文档ID（不填则搜索全部） |
| topK | Integer | 否 | 返回相关片段数量（默认5） |

**请求示例**：

```bash
curl -X POST "http://localhost:8080/api/documents/query" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "question": "如何使用产品的高级功能？",
    "userId": 10001,
    "documentId": 12345,
    "topK": 5
  }'
```

**响应格式**：SSE 文本流，每个数据块为一个 token 字符串

**响应示例**：

```
产品
的
高级
功能
包括
...
```

**错误码**：

| 错误码 | 说明 |
|--------|------|
| 400 | 参数错误（问题为空、用户ID为空） |
| 404 | 文档不存在或索引未完成 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |
| 503 | 服务降级（向量库或LLM不可用） |

---

## 3. 流式输出接口

### 3.1 标准 SSE 流式 RAG 查询

**接口描述**：使用 SSE (Server-Sent Events) 流式返回 RAG 查询结果，包含心跳、结束标记和错误事件

**请求方式**：`POST`

**请求路径**：`/stream/rag/query`

**Content-Type**：`application/json`

**Accept**：`text/event-stream`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| question | String | 是 | 用户问题 |
| userId | Long | 是 | 用户ID |
| documentId | Long | 否 | 指定文档ID |
| topK | Integer | 否 | 返回相关片段数量（默认5） |

**请求示例**：

```bash
curl -X POST "http://localhost:8080/api/stream/rag/query" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "question": "如何使用产品的高级功能？",
    "userId": 10001,
    "documentId": 12345,
    "topK": 5
  }'
```

**响应格式**：SSE 事件流

**事件类型**：

| 事件类型 | 说明 |
|---------|------|
| message | 内容数据块 |
| done | 流结束标记 |
| error | 错误信息 |
| heartbeat | 心跳消息（每 30 秒） |
| timeout | 超时通知（300 秒无活动） |

**响应示例**：

```
event: message
data: {"index":0,"content":"产品的高级功能","finished":false,"timestamp":1234567890}

event: message
data: {"index":1,"content":"包括...","finished":false,"timestamp":1234567891}

event: message
data: {"index":2,"content":"详细说明如下...","finished":false,"timestamp":1234567892}

event: done
data: {"index":3,"content":"","finished":true,"timestamp":1234567893}
```

**StreamChunk 数据结构**：

```json
{
  "index": 0,           // 数据块索引
  "content": "文本内容", // 内容
  "finished": false,    // 是否结束
  "error": null,        // 错误信息
  "timestamp": 1234567890 // 时间戳
}
```

**JavaScript 客户端示例**：

```javascript
const eventSource = new EventSource('/api/stream/rag/query', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    question: '如何使用产品的高级功能？',
    userId: 10001,
    documentId: 12345
  })
});

eventSource.addEventListener('message', (event) => {
  const chunk = JSON.parse(event.data);
  console.log('收到内容:', chunk.content);
  // 追加到页面显示
  document.getElementById('answer').innerText += chunk.content;
});

eventSource.addEventListener('done', (event) => {
  console.log('流式响应完成');
  eventSource.close();
});

eventSource.addEventListener('error', (event) => {
  const error = JSON.parse(event.data);
  console.error('发生错误:', error.error);
  eventSource.close();
});

eventSource.addEventListener('heartbeat', (event) => {
  console.log('收到心跳');
});
```

---

### 3.2 简化版流式查询

**接口描述**：直接返回文本流，不使用 ServerSentEvent 封装

**请求方式**：`POST`

**请求路径**：`/stream/rag/query/simple`

**Content-Type**：`application/json`

**Accept**：`text/event-stream`

**请求参数**：同 3.1

**响应格式**：纯文本流

**响应示例**：

```
产品的高级功能包括...详细说明如下...
```

---

### 3.3 SSE 连接测试

**接口描述**：测试 SSE 连接是否正常

**请求方式**：`GET`

**请求路径**：`/stream/test`

**请求示例**：

```bash
curl -X GET "http://localhost:8080/api/stream/test" \
  -H "Accept: text/event-stream"
```

**响应示例**：

```
event: message
data: {"index":0,"content":"测试消息 0","finished":false,"timestamp":1234567890}

event: message
data: {"index":1,"content":"测试消息 1","finished":false,"timestamp":1234567891}

...

event: done
data: {"index":10,"content":"","finished":true,"timestamp":1234567900}
```

---

## 4. 通用说明

### 4.1 认证方式

所有接口都需要在 Header 中携带认证信息：

```
Authorization: Bearer {access_token}
```

### 4.2 通用响应格式

**成功响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    // 业务数据
  }
}
```

**错误响应**：

```json
{
  "code": 400,
  "message": "参数错误：问题不能为空",
  "data": null
}
```

### 4.3 通用错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |
| 503 | 服务不可用（降级） |

### 4.4 限流规则

| 接口类型 | 限流规则 |
|---------|---------|
| 文档上传 | 10次/小时/用户，50次/天/用户 |
| RAG 查询 | 10次/分钟/用户，100次/小时/用户，500次/天/用户 |
| 智能问答 | 20次/分钟/用户，200次/小时/用户 |

### 4.5 文档格式支持

| 格式 | 扩展名 | 最大大小 |
|------|--------|---------|
| PDF | .pdf | 10MB |
| Word | .docx, .doc | 10MB |
| 文本 | .txt | 10MB |
| Markdown | .md | 10MB |
| HTML | .html, .htm | 10MB |

### 4.6 超时时间

| 操作类型 | 超时时间 |
|---------|---------|
| 文档上传 | 60秒 |
| 文档处理 | 300秒 |
| 文档索引 | 600秒 |
| 向量检索 | 10秒 |
| LLM 调用 | 60-120秒 |
| RAG 查询 | 90秒 |
| 流式查询连接 | 300秒 |

### 4.7 分页参数

对于列表查询接口，支持以下分页参数：

| 参数名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| page | Integer | 1 | 页码（从1开始） |
| pageSize | Integer | 20 | 每页数量 |
| sortBy | String | createTime | 排序字段 |
| sortOrder | String | desc | 排序方向（asc/desc） |

### 4.8 Mock 模式说明

当使用 `spring.profiles.active=mock` 启动时：

- LLM 返回固定模拟回复
- Embedding 返回确定性伪随机向量
- 向量存储使用内存实现
- 适合本地开发和接口测试

---

## 5. 使用示例

### 5.1 完整的文档上传和查询流程

```bash
# 1. 上传文档
DOCUMENT_ID=$(curl -X POST "http://localhost:8080/api/documents/upload" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@document.pdf" \
  -F "title=产品手册" \
  -F "userId=10001" \
  | jq -r '.data.documentId')

echo "文档ID: $DOCUMENT_ID"

# 2. 轮询检查索引状态
while true; do
  STATUS=$(curl -X GET "http://localhost:8080/api/documents/$DOCUMENT_ID/status" \
    -H "Authorization: Bearer {token}" \
    | jq -r '.data.indexStatus')
  
  echo "索引状态: $STATUS"
  
  if [ "$STATUS" = "COMPLETED" ]; then
    echo "索引完成"
    break
  elif [ "$STATUS" = "FAILED" ]; then
    echo "索引失败"
    exit 1
  fi
  
  sleep 5
done

# 3. 执行 RAG 查询（简化流式）
curl -X POST "http://localhost:8080/api/documents/query" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d "{
    \"question\": \"如何使用产品？\",
    \"userId\": 10001,
    \"documentId\": $DOCUMENT_ID
  }"

# 4. 或执行标准 SSE 查询
curl -X POST "http://localhost:8080/api/stream/rag/query" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d "{
    \"question\": \"如何使用产品？\",
    \"userId\": 10001,
    \"documentId\": $DOCUMENT_ID
  }"
```

### 5.2 Python 客户端示例

```python
import requests
import json

class RAGClient:
    def __init__(self, base_url, token):
        self.base_url = base_url
        self.headers = {
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        }
    
    def upload_document(self, file_path, title, user_id, description=None):
        """上传文档"""
        url = f'{self.base_url}/documents/upload'
        files = {'file': open(file_path, 'rb')}
        data = {'title': title, 'userId': user_id}
        if description:
            data['description'] = description
        
        response = requests.post(url, files=files, data=data, 
                                headers={'Authorization': self.headers['Authorization']})
        return response.json()
    
    def check_status(self, document_id):
        """检查索引状态"""
        url = f'{self.base_url}/documents/{document_id}/status'
        response = requests.get(url, headers=self.headers)
        return response.json()
    
    def query(self, question, user_id, document_id=None, top_k=5):
        """RAG 简化流式查询"""
        url = f'{self.base_url}/documents/query'
        data = {
            'question': question,
            'userId': user_id,
            'documentId': document_id,
            'topK': top_k
        }
        
        response = requests.post(url, json=data, headers=self.headers, stream=True)
        for chunk in response.iter_content(chunk_size=None):
            if chunk:
                yield chunk.decode('utf-8')
    
    def stream_query(self, question, user_id, document_id=None, top_k=5):
        """标准 SSE 流式查询"""
        url = f'{self.base_url}/stream/rag/query'
        data = {
            'question': question,
            'userId': user_id,
            'documentId': document_id,
            'topK': top_k
        }
        
        response = requests.post(url, json=data, headers=self.headers, stream=True)
        
        buffer = ""
        for line in response.iter_lines():
            if line:
                line = line.decode('utf-8')
                if line.startswith('data: '):
                    data = json.loads(line[6:])
                    yield data

# 使用示例
client = RAGClient('http://localhost:8080/api', 'your_token')

# 上传文档
result = client.upload_document('document.pdf', '产品手册', 10001)
document_id = result['data']['documentId']

# 简化流式查询
for chunk in client.query('如何使用产品？', 10001, document_id):
    print(chunk, end='', flush=True)

# 标准 SSE 查询
for chunk in client.stream_query('如何使用产品？', 10001, document_id):
    if chunk.get('error'):
        print(f"错误: {chunk['error']}")
    elif not chunk.get('finished'):
        print(chunk.get('content', ''), end='', flush=True)
```

---

## 6. 常见问题

### Q1: 文档上传后多久可以查询？

A: 文档上传后会异步进行索引处理，处理时间取决于文档大小和内容复杂度。通常：
- 小文档（<1MB）：10-30秒
- 中等文档（1-10MB）：30-120秒
- 大文档（10MB+）：2-5分钟

可以通过 `/documents/{documentId}/status` 接口查询索引状态。

### Q2: 如何提高查询准确度？

A: 可以通过以下方式提高准确度：
1. 增加 `topK` 参数值，检索更多相关片段
2. 优化文档内容，确保信息完整准确
3. 使用更具体的问题描述
4. 指定 `documentId` 限定搜索范围

### Q3: 流式查询如何处理超时？

A: 流式查询有以下超时机制：
- 连接超时：300秒
- 心跳间隔：30秒
- 超时后会收到 `timeout` 事件，客户端应关闭连接

### Q4: 如何处理并发查询？

A: 系统支持并发查询，但有限流保护：
- 单用户：10次/分钟（RAG）
- 建议使用连接池复用连接
- 对于高并发场景，建议使用缓存

### Q5: 文档删除后是否可以恢复？

A: 文档删除是硬删除，无法恢复。删除操作会：
1. 删除向量数据
2. 删除分块记录
3. 删除文档记录
4. 清除相关缓存

建议在删除前做好备份。

### Q6: `/documents/query` 和 `/stream/rag/query` 有什么区别？

A: 
- `/documents/query`：简化流式接口，直接返回文本流，无 SSE 事件封装。
- `/stream/rag/query`：标准 SSE 接口，包含 `message`/`done`/`error`/`heartbeat`/`timeout` 事件，适合生产环境前端对接。

---

## 📝 更新日志

| 日期 | 版本 | 更新内容 | 更新人 |
|------|------|---------|--------|
| 2026-06-13 | v1.1 | 接口路径统一为 `/api/documents/*`；增加标准 SSE 接口说明；更新限流、超时、文档格式、Mock 模式说明 | AI Assistant |
| 2026-02-06 | v1.0 | 创建 API 文档 | Kiro |

---

*本文档由 AI Assistant 维护，如有问题请及时反馈。*
