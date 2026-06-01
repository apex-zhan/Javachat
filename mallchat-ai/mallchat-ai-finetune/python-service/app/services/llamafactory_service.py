"""
LLaMA-Factory 服务实现（推荐）

负责调用 LLaMA-Factory 进行模型微调
"""

import asyncio
import json
import logging
import os
import subprocess
from typing import Optional

logger = logging.getLogger(__name__)


class LlamaFactoryService:
    """LLaMA-Factory 微调服务"""

    async def train(
        self,
        base_model: str,
        dataset_path: Optional[str] = None,
        training_data: Optional[list] = None,
        output_dir: Optional[str] = None,
        lora_config: Optional[dict] = None,
        training_config: Optional[dict] = None,
        use_deepspeed: bool = False,
        deepspeed_config: Optional[str] = None
    ) -> dict:
        """
        执行 LLaMA-Factory 微调训练

        Args:
            base_model: 基础模型名称
            dataset_path: 数据集路径
            training_data: 训练数据（内联）
            output_dir: 输出目录
            lora_config: LoRA 配置
            training_config: 训练配置
            use_deepspeed: 是否使用 DeepSpeed
            deepspeed_config: DeepSpeed 配置文件

        Returns:
            训练结果
        """
        logger.info(f"Starting LLaMA-Factory training for model: {base_model}")

        # 构建训练命令
        cmd = self._build_train_command(
            base_model=base_model,
            dataset_path=dataset_path,
            output_dir=output_dir,
            lora_config=lora_config,
            training_config=training_config,
            use_deepspeed=use_deepspeed,
            deepspeed_config=deepspeed_config
        )

        logger.info(f"Training command: {' '.join(cmd)}")

        # 执行训练
        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )

        stdout, stderr = await process.communicate()

        if process.returncode != 0:
            error_msg = stderr.decode() if stderr else "Unknown error"
            logger.error(f"Training failed: {error_msg}")
            raise RuntimeError(f"Training failed: {error_msg}")

        logger.info(f"Training completed successfully")

        return {
            "output_path": output_dir,
            "status": "completed"
        }

    def _build_train_command(
        self,
        base_model: str,
        dataset_path: Optional[str],
        output_dir: Optional[str],
        lora_config: Optional[dict],
        training_config: Optional[dict],
        use_deepspeed: bool,
        deepspeed_config: Optional[str]
    ) -> list:
        """构建训练命令"""

        # 基础命令
        cmd = [
            "llamafactory-cli", "train",
            "--model_name_or_path", base_model,
            "--stage", "sft",
            "--do_train", "True",
            "--finetuning_type", "lora",
            "--template", "default",
            "--dataset_dir", os.path.dirname(dataset_path) if dataset_path else "./data",
            "--dataset", os.path.splitext(os.path.basename(dataset_path))[0] if dataset_path else "alpaca",
            "--output_dir", output_dir or "./outputs",
            "--overwrite_output_dir", "True"
        ]

        # LoRA 配置
        if lora_config:
            cmd.extend([
                "--lora_rank", str(lora_config.get("r", 64)),
                "--lora_alpha", str(lora_config.get("lora_alpha", 128)),
                "--lora_dropout", str(lora_config.get("lora_dropout", 0.05))
            ])
            target_modules = lora_config.get("target_modules")
            if target_modules:
                cmd.extend(["--lora_target", ",".join(target_modules)])

        # 训练配置
        if training_config:
            cmd.extend([
                "--num_train_epochs", str(training_config.get("num_train_epochs", 3)),
                "--per_device_train_batch_size", str(training_config.get("per_device_train_batch_size", 1)),
                "--gradient_accumulation_steps", str(training_config.get("gradient_accumulation_steps", 8)),
                "--learning_rate", str(training_config.get("learning_rate", 5e-5)),
                "--max_seq_length", str(training_config.get("max_seq_length", 2048)),
                "--warmup_ratio", str(training_config.get("warmup_ratio", 0.03)),
                "--lr_scheduler_type", str(training_config.get("lr_scheduler_type", "cosine")),
                "--logging_steps", str(training_config.get("logging_steps", 10)),
                "--save_steps", str(training_config.get("save_steps", 100))
            ])

            # 精度设置
            if training_config.get("bf16", False):
                cmd.append("--bf16")
            elif training_config.get("fp16", True):
                cmd.append("--fp16")

        # DeepSpeed 配置
        if use_deepspeed and deepspeed_config:
            cmd.extend(["--deepspeed", deepspeed_config])

        return cmd

    async def export_model(
        self,
        adapter_path: str,
        output_path: str,
        template: str = "default"
    ) -> dict:
        """
        导出微调后的模型（合并 LoRA）

        Args:
            adapter_path: Adapter 路径
            output_path: 输出路径
            template: 对话模板

        Returns:
            导出结果
        """
        logger.info(f"Exporting model from {adapter_path} to {output_path}")

        cmd = [
            "llamafactory-cli", "export",
            "--adapter_name_or_path", adapter_path,
            "--template", template,
            "--finetuning_type", "lora",
            "--export_dir", output_path,
            "--export_size", "2",
            "--export_device", "cpu",
            "--export_legacy_format", "False"
        ]

        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )

        stdout, stderr = await process.communicate()

        if process.returncode != 0:
            error_msg = stderr.decode() if stderr else "Unknown error"
            raise RuntimeError(f"Export failed: {error_msg}")

        logger.info(f"Model exported successfully to {output_path}")

        return {
            "output_path": output_path,
            "status": "completed"
        }
