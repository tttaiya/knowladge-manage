FROM python:3.11-slim

WORKDIR /app

COPY backend-python/super-biz-agent-service/pyproject.toml /app/pyproject.toml
COPY backend-python/super-biz-agent-service/README.md /app/README.md
COPY backend-python/super-biz-agent-service/app /app/app
COPY backend-python/super-biz-agent-service/static /app/static
COPY backend-python/super-biz-agent-service/mcp_servers /app/mcp_servers

RUN pip install --no-cache-dir -e .

EXPOSE 9900

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9900"]
