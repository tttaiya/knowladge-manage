"""Rule-first intent routing."""

from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.services.settings_service import SettingsService


@dataclass
class IntentResult:
    intent: str
    confidence: float
    reason: str


class IntentService:
    knowledge_keywords = (
        "电力",
        "规范",
        "标准",
        "制度",
        "技术监督",
        "sop",
        "条款",
        "规程",
        "导则",
        "细则",
        "验收",
        "检修",
        "运维",
        "继电保护",
        "变电",
        "输电",
        "配电",
        "调度",
        "两票三制",
        "安全规程",
        "反事故措施",
        "dl/t",
        "gb/t",
    )
    chat_keywords = ("你好", "您好", "早上好", "晚上好", "闲聊", "讲个笑话")

    def __init__(self, db: Session | None = None):
        self.db = db

    def detect(self, question: str, mode: str = "auto") -> IntentResult:
        if mode == "knowledge":
            return IntentResult("knowledge_qa", 1.0, "用户强制选择知识库模式")
        if mode == "chat":
            return IntentResult("general_chat", 1.0, "用户强制选择普通对话模式")
        text = question.lower()
        matched_term = self._match_dynamic_term(text)
        if matched_term:
            return IntentResult("knowledge_qa", 0.9, f"命中术语库关键词：{matched_term}")
        if any(keyword in text for keyword in self.knowledge_keywords):
            return IntentResult("knowledge_qa", 0.86, "命中知识库问答关键词")
        if any(keyword in text for keyword in self.chat_keywords):
            return IntentResult("general_chat", 0.82, "命中闲聊关键词")
        return IntentResult("general_chat", 0.65, "未命中特定知识库关键词，按普通对话处理")

    def _match_dynamic_term(self, text: str) -> str | None:
        if self.db is None:
            return None
        terms = SettingsService(self.db).get("intent.knowledge_terms", [])
        for term in sorted((item.strip().lower() for item in terms if isinstance(item, str)), key=len, reverse=True):
            if term and term in text:
                return term
        return None
