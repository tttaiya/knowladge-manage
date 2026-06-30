"""General LLM chat service."""

from langchain_core.messages import HumanMessage, SystemMessage
from sqlalchemy.orm import Session

from app.core.llm_factory import llm_factory


class GeneralChatService:
    system_prompt = (
        "你是一个专业、简洁、可靠的智能问答助手。"
        "用户问题不需要知识库检索时，直接基于通用能力回答；"
        "如果涉及需要规范、标准或内部文档依据的问题，应提醒用户切换或使用知识库问答。"
    )

    def __init__(self, db: Session | None = None):
        self.db = db

    async def answer(self, question: str) -> str:
        cfg = llm_factory.get_config(self.db)
        if not cfg.api_key:
            return f"我已收到你的问题：{question}"

        model = llm_factory.get_chat_model(self.db)
        result = await model.ainvoke(
            [
                SystemMessage(content=self.system_prompt),
                HumanMessage(content=question),
            ]
        )
        return str(getattr(result, "content", result) or "")
