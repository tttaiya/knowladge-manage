"""Bad case 本地存储服务。"""

import json
import uuid
from pathlib import Path

from app.models.bad_case import BadCaseCreateRequest, BadCaseRecord
from app.models.trace import now_iso


class BadCaseService:
    """管理失败案例的创建、查询和列表展示。"""

    def __init__(self, base_dir: str = "badcases") -> None:
        self.base_dir = Path(base_dir)
        self.base_dir.mkdir(parents=True, exist_ok=True)

    def create(self, request: BadCaseCreateRequest) -> BadCaseRecord:
        now = now_iso()
        case = BadCaseRecord(
            case_id=f"case_{uuid.uuid4().hex[:10]}",
            trace_id=request.trace_id,
            case_type=request.case_type,
            symptom=request.symptom,
            root_cause=request.root_cause,
            fix_action=request.fix_action,
            verification_result=request.verification_result,
            created_at=now,
            updated_at=now,
        )
        self._save(case)
        return case

    def list_cases(self) -> list[BadCaseRecord]:
        cases = []
        for path in self.base_dir.glob("*.json"):
            cases.append(BadCaseRecord(**json.loads(path.read_text(encoding="utf-8"))))
        return sorted(cases, key=lambda item: item.created_at, reverse=True)

    def get(self, case_id: str) -> BadCaseRecord | None:
        path = self.base_dir / f"{case_id}.json"
        if not path.exists():
            return None
        return BadCaseRecord(**json.loads(path.read_text(encoding="utf-8")))

    def _save(self, case: BadCaseRecord) -> None:
        path = self.base_dir / f"{case.case_id}.json"
        path.write_text(
            json.dumps(case.model_dump(), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


bad_case_service = BadCaseService()
