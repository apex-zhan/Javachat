"""
MallChat AI Fine-Tune Service
基于 FastAPI 的微调服务，支持 LLaMA-Factory 和 Axolotl
"""

import asyncio
import logging
import os
import uuid
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.services.llamafactory_service import LlamaFactoryService
from app.services.axolotl_service import AxolotlService

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# 全局任务存储
tasks = {}


class FineTuneRequest(BaseModel):
    provider: str = "llamafactory"  # llamafactory | axolotl
    base_model: str
    dataset_path: Optional[str] = None
    training_data: Optional[list] = None
    output_dir: Optional[str] = None
    lora_config: Optional[dict] = None
    training_config: Optional[dict] = None
    use_deepspeed: bool = False
    deepspeed_config: Optional[str] = None


class FineTuneResponse(BaseModel):
    task_id: str
    status: str
    base_model: str
    provider: str
    output_path: Optional[str] = None
    created_at: str
    error_message: Optional[str] = None


class FineTuneStatusResponse(BaseModel):
    task_id: str
    status: str
    progress: int
    progress_detail: Optional[str] = None
    created_at: str
    updated_at: str
    latest_log: Optional[str] = None
    error_message: Optional[str] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    logger.info("Starting MallChat AI Fine-Tune Service")
    yield
    logger.info("Shutting down MallChat AI Fine-Tune Service")


app = FastAPI(
    title="MallChat AI Fine-Tune Service",
    description="基于 LLaMA-Factory 和 Axolotl 的模型微调服务",
    version="1.0.0",
    lifespan=lifespan
)


@app.get("/health")
async def health_check():
    """健康检查"""
    return {"status": "healthy", "timestamp": datetime.now().isoformat()}


@app.post("/api/v1/finetune", response_model=FineTuneResponse)
async def submit_finetune(request: FineTuneRequest):
    """提交微调任务"""
    task_id = str(uuid.uuid4())
    logger.info(f"Submitting fine-tune task: {task_id}, provider: {request.provider}, model: {request.base_model}")

    # 初始化任务
    tasks[task_id] = {
        "task_id": task_id,
        "status": "pending",
        "provider": request.provider,
        "base_model": request.base_model,
        "progress": 0,
        "created_at": datetime.now().isoformat(),
        "updated_at": datetime.now().isoformat(),
        "logs": "",
        "error_message": None
    }

    # 选择服务
    if request.provider == "llamafactory":
        service = LlamaFactoryService()
    elif request.provider == "axolotl":
        service = AxolotlService()
    else:
        raise HTTPException(status_code=400, detail=f"Unknown provider: {request.provider}")

    # 异步启动训练
    asyncio.create_task(run_training(task_id, request, service))

    return FineTuneResponse(
        task_id=task_id,
        status="pending",
        base_model=request.base_model,
        provider=request.provider,
        created_at=tasks[task_id]["created_at"]
    )


async def run_training(task_id: str, request: FineTuneRequest, service):
    """运行训练任务"""
    try:
        tasks[task_id]["status"] = "running"
        tasks[task_id]["updated_at"] = datetime.now().isoformat()

        logger.info(f"Starting training for task: {task_id}")

        # 执行训练
        result = await service.train(
            base_model=request.base_model,
            dataset_path=request.dataset_path,
            training_data=request.training_data,
            output_dir=request.output_dir or f"./outputs/{task_id}",
            lora_config=request.lora_config,
            training_config=request.training_config,
            use_deepspeed=request.use_deepspeed,
            deepspeed_config=request.deepspeed_config
        )

        tasks[task_id]["status"] = "completed"
        tasks[task_id]["output_path"] = result.get("output_path")
        tasks[task_id]["progress"] = 100
        tasks[task_id]["updated_at"] = datetime.now().isoformat()

        logger.info(f"Training completed for task: {task_id}")

    except Exception as e:
        logger.error(f"Training failed for task: {task_id}, error: {str(e)}")
        tasks[task_id]["status"] = "failed"
        tasks[task_id]["error_message"] = str(e)
        tasks[task_id]["updated_at"] = datetime.now().isoformat()


@app.get("/api/v1/finetune/{task_id}/status", response_model=FineTuneStatusResponse)
async def get_task_status(task_id: str):
    """查询任务状态"""
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail=f"Task not found: {task_id}")

    task = tasks[task_id]
    return FineTuneStatusResponse(
        task_id=task_id,
        status=task["status"],
        progress=task.get("progress", 0),
        progress_detail=f"{task.get('progress', 0)}%",
        created_at=task["created_at"],
        updated_at=task["updated_at"],
        latest_log=task.get("logs", "")[-500:] if task.get("logs") else None,
        error_message=task.get("error_message")
    )


@app.post("/api/v1/finetune/{task_id}/cancel")
async def cancel_task(task_id: str):
    """取消任务"""
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail=f"Task not found: {task_id}")

    tasks[task_id]["status"] = "cancelled"
    tasks[task_id]["updated_at"] = datetime.now().isoformat()

    logger.info(f"Task cancelled: {task_id}")
    return {"message": f"Task {task_id} cancelled"}


@app.get("/api/v1/finetune/{task_id}/logs")
async def get_task_logs(task_id: str, lines: Optional[int] = 100):
    """获取任务日志"""
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail=f"Task not found: {task_id}")

    logs = tasks[task_id].get("logs", "")
    if lines and logs:
        log_lines = logs.split("\n")
        logs = "\n".join(log_lines[-lines:])

    return logs


@app.get("/api/v1/models")
async def list_models():
    """获取微调后的模型列表"""
    completed_tasks = [
        {
            "task_id": task["task_id"],
            "status": task["status"],
            "base_model": task["base_model"],
            "provider": task["provider"],
            "output_path": task.get("output_path"),
            "created_at": task["created_at"]
        }
        for task in tasks.values()
        if task["status"] == "completed"
    ]
    return completed_tasks


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
