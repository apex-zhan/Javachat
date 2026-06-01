"""
Axolotl 服务实现（备选）

负责调用 Axolotl 进行模型微调
"""

import asyncio
import logging
import os
import subprocess
from typing import Optional

logger = logging.getLogger(__name__)


class AxolotlService:
    """Axolotl 微调服务"""

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
        执行 Axolotl 微调训练

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
        logger.info(f"Starting Axolotl training for model: {base_model}")

        # 生成配置文件
        config_path = await self._generate_config(
            base_model=base_model,
            dataset_path=dataset_path,
            output_dir=output_dir,
            lora_config=lora_config,
            training_config=training_config,
            use_deepspeed=use_deepspeed,
            deepspeed_config=deepspeed_config
        )

        # 构建训练命令
        cmd = [
            "axolotl", "train", config_path,
            "--deepspeed" if use_deepspeed else ""
        ]
        cmd = [c for c in cmd if c]  # 移除空字符串

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

    async def _generate_config(
        self,
        base_model: str,
        dataset_path: Optional[str],
        output_dir: Optional[str],
        lora_config: Optional[dict],
        training_config: Optional[dict],
        use_deepspeed: bool,
        deepspeed_config: Optional[str]
    ) -> str:
        """生成 Axolotl YAML 配置文件"""

        config = {
            "base_model": base_model,
            "model_type": "AutoModelForCausalLM",
            "tokenizer_type": "AutoTokenizer",

            # 数据集配置
            "datasets": [
                {
                    "path": dataset_path or "./data/alpaca.jsonl",
                    "type": "alpaca"
                }
            ],

            # 输出配置
            "output_dir": output_dir or "./outputs",

            # 序列长度
            "sequence_len": training_config.get("max_seq_length", 2048) if training_config else 2048,

            # 样本打包
            "sample_packing": True,
            "pad_to_sequence_len": True,

            # 适配器配置
            "adapter": "lora",
            "lora_r": lora_config.get("r", 32) if lora_config else 32,
            "lora_alpha": lora_config.get("lora_alpha", 64) if lora_config else 64,
            "lora_dropout": lora_config.get("lora_dropout", 0.05) if lora_config else 0.05,
            "lora_target_linear": True,
            "lora_fan_in_fan_out": False,

            # 训练配置
            "num_epochs": training_config.get("num_train_epochs", 3) if training_config else 3,
            "micro_batch_size": training_config.get("per_device_train_batch_size", 2) if training_config else 2,
            "gradient_accumulation_steps": training_config.get("gradient_accumulation_steps", 4) if training_config else 4,
            "learning_rate": training_config.get("learning_rate", 2e-4) if training_config else 2e-4,
            "lr_scheduler": training_config.get("lr_scheduler_type", "cosine") if training_config else "cosine",
            "warmup_ratio": training_config.get("warmup_ratio", 0.03) if training_config else 0.03,

            # 优化器
            "optimizer": "adamw_torch",

            # 日志
            "logging_steps": training_config.get("logging_steps", 10) if training_config else 10,
            "save_steps": training_config.get("save_steps", 100) if training_config else 100,

            # 精度
            "bf16": training_config.get("bf16", False) if training_config else False,
            "fp16": training_config.get("fp16", True) if training_config else True,

            # 其他
            "gradient_checkpointing": True,
            "flash_attention": True,
            "resume_from_checkpoint": False,
        }

        # DeepSpeed 配置
        if use_deepspeed and deepspeed_config:
            config["deepspeed"] = deepspeed_config

        # 保存配置文件
        import yaml
        config_path = os.path.join(output_dir or "./outputs", "axolotl_config.yaml")
        os.makedirs(os.path.dirname(config_path), exist_ok=True)

        with open(config_path, "w") as f:
            yaml.dump(config, f, default_flow_style=False)

        logger.info(f"Axolotl config generated: {config_path}")
        return config_path

    async def merge_lora(
        self,
        base_model: str,
        lora_model: str,
        output_path: str
    ) -> dict:
        """
        合并 LoRA 权重到基础模型

        Args:
            base_model: 基础模型路径
            lora_model: LoRA 模型路径
            output_path: 输出路径

        Returns:
            合并结果
        """
        logger.info(f"Merging LoRA from {lora_model} into {base_model}")

        cmd = [
            "axolotl", "merge-lora",
            "--base_model", base_model,
            "--lora_model", lora_model,
            "--output", output_path
        ]

        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )

        stdout, stderr = await process.communicate()

        if process.returncode != 0:
            error_msg = stderr.decode() if stderr else "Unknown error"
            raise RuntimeError(f"Merge failed: {error_msg}")

        logger.info(f"LoRA merged successfully to {output_path}")

        return {
            "output_path": output_path,
            "status": "completed"
        }
