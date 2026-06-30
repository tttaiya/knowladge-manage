"""Conversation context assembly and simple compression."""

from dataclasses import dataclass


@dataclass
class ContextBundle:
    system_prompt: str
    conversation_summary: str | None
    recent_messages: list
    current_question: str


class ContextService:
    def estimate_tokens(self, text: str) -> int:
        return max(1, len(text) // 2)

    def build_context(self, messages: list, current_question: str, summary: str | None = None, max_recent_rounds: int = 3) -> ContextBundle:
        keep = max_recent_rounds * 2
        recent = messages[-keep:] if keep > 0 else []
        return ContextBundle(
            system_prompt="你是专业、可靠的智能问答助手。",
            conversation_summary=summary,
            recent_messages=recent,
            current_question=current_question,
        )
