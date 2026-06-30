"""Small repository base helpers."""

from typing import Generic, TypeVar

from sqlalchemy.orm import Session

T = TypeVar("T")


class BaseRepository(Generic[T]):
    def __init__(self, db: Session, model: type[T]):
        self.db = db
        self.model = model

    def get(self, item_id: str) -> T | None:
        return self.db.get(self.model, item_id)

    def list(self) -> list[T]:
        from sqlalchemy import select

        return list(self.db.scalars(select(self.model)))

    def add(self, obj: T) -> T:
        self.db.add(obj)
        self.db.flush()
        return obj

    def delete(self, obj: T) -> None:
        self.db.delete(obj)
        self.db.flush()

    def commit(self) -> None:
        self.db.commit()
