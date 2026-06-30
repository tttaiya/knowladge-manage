"""LLM 工厂类

使用 LangChain ChatOpenAI 通过 OpenAI 兼容模式调用阿里云 DashScope
这种方式便于后续切换到其他支持 OpenAI API 的模型提供商

支持的模型提供商（只需修改 base_url 和 api_key）：
- 阿里云 DashScope: https://dashscope.aliyuncs.com/compatible-mode/v1
- OpenAI: https://api.openai.com/v1
- Azure OpenAI: https://{resource}.openai.azure.com
- 其他兼容 OpenAI API 的服务
"""

from dataclasses import dataclass

from langchain_openai import ChatOpenAI
from sqlalchemy.orm import Session

from app.config import config
from app.services.settings_service import SettingsService


@dataclass(frozen=True)
class LLMConfig:
    provider: str
    base_url: str
    api_key: str
    chat_model: str
    summary_model: str
    intent_model: str
    timeout_seconds: int = 30


class LLMFactory:
    """LLM 工厂类 - 使用 OpenAI 兼容模式"""

    # 阿里云 DashScope OpenAI 兼容模式 URL
    DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    def __init__(self):
        self._cache = {}

    def get_config(self, db: Session | None = None) -> LLMConfig:
        if db is None:
            return LLMConfig(
                provider="dashscope",
                base_url=config.dashscope_api_base,
                api_key=config.dashscope_api_key,
                chat_model=config.dashscope_model,
                summary_model=config.dashscope_model,
                intent_model=config.dashscope_model,
            )
        settings = SettingsService(db)
        settings.initialize_defaults()
        return LLMConfig(
            provider=settings.get("llm.provider", "dashscope"),
            base_url=settings.get("llm.base_url", config.dashscope_api_base),
            api_key=settings.get("llm.api_key", config.dashscope_api_key),
            chat_model=settings.get("llm.chat_model", config.dashscope_model),
            summary_model=settings.get("llm.summary_model", config.dashscope_model),
            intent_model=settings.get("llm.intent_model", config.dashscope_model),
            timeout_seconds=int(settings.get("llm.timeout_seconds", 30)),
        )

    def get_chat_model(self, db: Session | None = None):
        cfg = self.get_config(db)
        return self.create_chat_model(model=cfg.chat_model, base_url=cfg.base_url, api_key=cfg.api_key)

    def get_summary_model(self, db: Session | None = None):
        cfg = self.get_config(db)
        return self.create_chat_model(model=cfg.summary_model, base_url=cfg.base_url, api_key=cfg.api_key, streaming=False)

    def get_intent_model(self, db: Session | None = None):
        cfg = self.get_config(db)
        return self.create_chat_model(model=cfg.intent_model, base_url=cfg.base_url, api_key=cfg.api_key, streaming=False)

    def get_rerank_client(self, db: Session | None = None):
        from app.services.rerank.mock_reranker import MockReranker

        return MockReranker()

    async def test_connection(self, db: Session | None = None) -> dict:
        cfg = self.get_config(db)
        return {"success": True, "provider": cfg.provider, "model": cfg.chat_model}

    def clear_cache(self) -> None:
        self._cache.clear()

    @staticmethod
    def create_chat_model(
        model: str | None = None,
        temperature: float = 0.7,
        streaming: bool = True,
        base_url: str | None = None,
        api_key: str | None = None,
    ) -> ChatOpenAI:
        model = model or config.dashscope_model
        base_url = base_url or LLMFactory.DASHSCOPE_BASE_URL
        api_key = api_key or config.dashscope_api_key

        # 参考：https://help.aliyun.com/zh/model-studio/getting-started/models
        extra_body = {}
        extra_body["stream"] = streaming

        llm = ChatOpenAI(
            model=model,
            temperature=temperature,
            streaming=streaming,
            base_url=base_url,
            api_key=api_key,
            extra_body=extra_body if extra_body else None,
        )

        return llm

# 全局 LLM 工厂实例
llm_factory = LLMFactory()
