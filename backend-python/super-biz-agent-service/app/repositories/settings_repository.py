"""Settings repository."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.orm.settings import SystemSetting


class SettingsRepository:
    def __init__(self, db: Session):
        self.db = db

    def get(self, key: str) -> SystemSetting | None:
        return self.db.scalar(select(SystemSetting).where(SystemSetting.key == key))

    def list(self) -> list[SystemSetting]:
        return list(self.db.scalars(select(SystemSetting).order_by(SystemSetting.scope, SystemSetting.key)))

    def upsert_default(self, item: dict) -> SystemSetting:
        row = self.get(item["key"])
        if row:
            return row
        row = SystemSetting(**item)
        self.db.add(row)
        self.db.flush()
        return row
