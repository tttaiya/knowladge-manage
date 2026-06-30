"""Tests for the database foundation module."""

from time import sleep

import pytest
from sqlalchemy import create_engine, inspect
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker

from app.db.base import Base
from app.db.session import init_db
from app.models.orm.knowledge import KnowledgeBase
from app.models.orm.settings import SystemSetting
from app.models.orm.user import User


@pytest.fixture()
def db_session():
    engine = create_engine("sqlite:///:memory:", future=True)
    init_db(engine)
    SessionLocal = sessionmaker(bind=engine, expire_on_commit=False, future=True)
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=engine)
        engine.dispose()


def test_sqlite_memory_database_can_create_all_tables(db_session):
    inspector = inspect(db_session.bind)

    assert set(inspector.get_table_names()) == {
        "users",
        "refresh_tokens",
        "chat_sessions",
        "chat_messages",
        "chat_message_citations",
        "knowledge_bases",
        "knowledge_documents",
        "knowledge_chunks",
        "system_settings",
        "qa_metrics",
    }


def test_unique_constraints_are_enforced(db_session):
    db_session.add_all(
        [
            User(username="alice", password_hash="hash-1", display_name="Alice"),
            User(username="alice", password_hash="hash-2", display_name="Alice 2"),
        ]
    )

    with pytest.raises(IntegrityError):
        db_session.commit()

    db_session.rollback()

    owner = User(username="owner", password_hash="hash", display_name="Owner")
    db_session.add(owner)
    db_session.commit()
    db_session.refresh(owner)

    db_session.add_all(
        [
            KnowledgeBase(name="知识库 A", code="kb-code", created_by=owner.id),
            KnowledgeBase(name="知识库 B", code="kb-code", created_by=owner.id),
        ]
    )

    with pytest.raises(IntegrityError):
        db_session.commit()

    db_session.rollback()

    db_session.add_all(
        [
            SystemSetting(key="rag.vector_top_k", value="3", default_value="3"),
            SystemSetting(key="rag.vector_top_k", value="5", default_value="3"),
        ]
    )

    with pytest.raises(IntegrityError):
        db_session.commit()


def test_updated_at_changes_on_update(db_session):
    user = User(username="bob", password_hash="hash", display_name="Bob")
    db_session.add(user)
    db_session.commit()
    db_session.refresh(user)
    original_updated_at = user.updated_at

    sleep(0.01)
    user.display_name = "Bobby"
    db_session.commit()
    db_session.refresh(user)

    assert user.updated_at > original_updated_at
