"""Runtime settings service."""

import json
from dataclasses import dataclass
from typing import Any

from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.config import config
from app.models.orm.settings import SystemSetting
from app.repositories.settings_repository import SettingsRepository


DEFAULT_SETTINGS = [
    {"key": "rag.retrieval_mode", "value": "vector", "default_value": "vector", "value_type": "string", "scope": "rag", "description": "检索模式"},
    {"key": "rag.vector_top_k", "value": str(config.rag_top_k), "default_value": str(config.rag_top_k), "value_type": "int", "scope": "rag", "description": "向量召回条数"},
    {"key": "rag.vector_score_threshold", "value": "0.0", "default_value": "0.0", "value_type": "float", "scope": "rag", "description": "向量分数阈值"},
    {"key": "rag.rerank_top_n", "value": "3", "default_value": "3", "value_type": "int", "scope": "rag", "description": "重排序保留条数"},
    {"key": "rag.rerank_score_threshold", "value": "0.0", "default_value": "0.0", "value_type": "float", "scope": "rag", "description": "重排序阈值"},
    {"key": "rag.require_keyword_overlap", "value": "false", "default_value": "false", "value_type": "bool", "scope": "rag", "description": "是否强制关键词重叠"},
    {"key": "rag.bound_knowledge_base_ids", "value": "[]", "default_value": "[]", "value_type": "json", "scope": "rag", "description": "默认绑定知识库"},
    {"key": "rag.allow_fallback_answer", "value": "false", "default_value": "false", "value_type": "bool", "scope": "rag", "description": "允许无资料兜底回答"},
    {"key": "intent.knowledge_terms", "value": "[]", "default_value": "[]", "value_type": "json", "scope": "system", "description": "知识库术语库"},
    {"key": "intent.confidence_threshold", "value": "0.5", "default_value": "0.5", "value_type": "float", "scope": "system", "description": "意图置信度阈值"},
    {"key": "context.max_recent_rounds", "value": "3", "default_value": "3", "value_type": "int", "scope": "system", "description": "最近轮次"},
    {"key": "context.summary_trigger_message_count", "value": "12", "default_value": "12", "value_type": "int", "scope": "system", "description": "摘要触发消息数"},
    {"key": "context.max_context_tokens", "value": "6000", "default_value": "6000", "value_type": "int", "scope": "system", "description": "上下文窗口"},
    {"key": "context.reserved_output_tokens", "value": "1000", "default_value": "1000", "value_type": "int", "scope": "system", "description": "输出预留"},
    {"key": "context.summary_model", "value": config.dashscope_model, "default_value": config.dashscope_model, "value_type": "string", "scope": "llm", "description": "摘要模型"},
    {"key": "llm.provider", "value": "dashscope", "default_value": "dashscope", "value_type": "string", "scope": "llm", "description": "模型供应商"},
    {"key": "llm.base_url", "value": config.dashscope_api_base, "default_value": config.dashscope_api_base, "value_type": "string", "scope": "llm", "description": "接口地址"},
    {"key": "llm.api_key", "value": config.dashscope_api_key, "default_value": "", "value_type": "secret", "scope": "llm", "description": "API Key", "is_secret": True},
    {"key": "llm.chat_model", "value": config.dashscope_model, "default_value": config.dashscope_model, "value_type": "string", "scope": "llm", "description": "对话模型"},
    {"key": "llm.summary_model", "value": config.dashscope_model, "default_value": config.dashscope_model, "value_type": "string", "scope": "llm", "description": "摘要模型"},
    {"key": "llm.intent_model", "value": config.dashscope_model, "default_value": config.dashscope_model, "value_type": "string", "scope": "llm", "description": "意图模型"},
    {"key": "llm.timeout_seconds", "value": "30", "default_value": "30", "value_type": "int", "scope": "llm", "description": "超时时间"},
    {"key": "security.access_token_expire_minutes", "value": str(config.access_token_expire_minutes), "default_value": str(config.access_token_expire_minutes), "value_type": "int", "scope": "security", "description": "Access Token 过期分钟"},
]


def _parse(value: str, value_type: str) -> Any:
    if value_type == "int":
        return int(value)
    if value_type == "float":
        return float(value)
    if value_type == "bool":
        return value.lower() in {"1", "true", "yes", "on"}
    if value_type == "json":
        return json.loads(value)
    return value


def _stringify(value: Any, value_type: str) -> str:
    if value_type == "json":
        return json.dumps(value, ensure_ascii=False)
    if value_type == "bool":
        return "true" if bool(value) else "false"
    return str(value)


@dataclass
class SettingView:
    key: str
    value: Any
    value_type: str
    default_value: Any
    description: str
    scope: str
    is_secret: bool


class SettingsService:
    def __init__(self, db: Session):
        self.db = db
        self.repo = SettingsRepository(db)

    def initialize_defaults(self) -> None:
        for item in DEFAULT_SETTINGS:
            full = {"is_secret": False, **item}
            self.repo.upsert_default(full)
        self.db.commit()

    def get(self, key: str, default: Any = None) -> Any:
        row = self.repo.get(key)
        if row is None:
            return default
        return _parse(row.value, row.value_type)

    def list_settings(self) -> list[SettingView]:
        self.initialize_defaults()
        return [self._view(row) for row in self.repo.list()]

    def update(self, key: str, value: Any, user_id: str | None = None) -> SettingView:
        self.initialize_defaults()
        row = self.repo.get(key)
        if row is None:
            raise HTTPException(status_code=404, detail="配置不存在")
        self._validate(row, value)
        row.value = _stringify(value, row.value_type)
        row.updated_by = user_id
        self.db.commit()
        self.db.refresh(row)
        return self._view(row)

    def reset(self) -> list[SettingView]:
        self.initialize_defaults()
        for row in self.repo.list():
            row.value = row.default_value
        self.db.commit()
        return [self._view(row) for row in self.repo.list()]

    def _view(self, row: SystemSetting) -> SettingView:
        value = "******" if row.is_secret and row.value else _parse(row.value, row.value_type)
        return SettingView(
            key=row.key,
            value=value,
            value_type=row.value_type,
            default_value="******" if row.is_secret and row.default_value else _parse(row.default_value, row.value_type),
            description=row.description,
            scope=row.scope,
            is_secret=row.is_secret,
        )

    def _validate(self, row: SystemSetting, value: Any) -> None:
        try:
            parsed = _parse(_stringify(value, row.value_type), row.value_type)
        except Exception as exc:
            raise HTTPException(status_code=400, detail="配置值类型不合法") from exc
        if row.key.endswith("top_k") or row.key.endswith("top_n"):
            if int(parsed) < 1 or int(parsed) > 50:
                raise HTTPException(status_code=400, detail="配置值超出范围")
        if "threshold" in row.key:
            if float(parsed) < 0 or float(parsed) > 1:
                raise HTTPException(status_code=400, detail="配置值超出范围")
