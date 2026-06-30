"""ORM base and model registry."""

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass


def import_models() -> None:
    import app.models.orm.conversation  # noqa: F401
    import app.models.orm.knowledge  # noqa: F401
    import app.models.orm.metrics  # noqa: F401
    import app.models.orm.settings  # noqa: F401
    import app.models.orm.user  # noqa: F401


import_models()
