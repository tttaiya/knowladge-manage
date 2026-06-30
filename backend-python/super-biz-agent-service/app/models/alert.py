"""告警截图解析模型。"""

from pydantic import BaseModel


class AlertParseResult(BaseModel):
    alert_name: str = ""
    service: str = ""
    metric: str = ""
    current_value: str = ""
    threshold: str = ""
    time_range: str = ""
    raw_text: str = ""
    confidence: float = 0.0
