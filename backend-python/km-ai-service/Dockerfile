# F4 整合 Dockerfile（commit #21 重写）
# 关键变更：
# - 从项目根目录作 build context（与现有 docker-compose.demo.yml 一致）
# - 合并 requirements-ocr.txt（杨家凤 F4 提供）
# - EXPOSE 8000（容器内统一端口，与现有 KM_AI_BASE_URL=http://fastapi-ai-service:8000 一致）
# - 安装 PaddleOCR/PaddlePaddle 所需系统库 libgl1/libglib2.0-0/libgomp1

FROM python:3.10-slim

WORKDIR /app

# PaddleOCR 运行时依赖（libgl1、libgomp1）；libglib2.0-0 兼容 Pillow
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# 依赖文件
COPY backend-python/km-ai-service/requirements.txt /app/requirements.txt
COPY backend-python/km-ai-service/requirements-ocr.txt /app/requirements-ocr.txt
RUN pip install --no-cache-dir -r /app/requirements.txt -r /app/requirements-ocr.txt

# 应用代码
COPY backend-python/km-ai-service/app /app/app
COPY backend-python/km-ai-service/tests /app/tests
COPY backend-python/km-ai-service/test-data /app/test-data

# 暴露端口（与现有 docker-compose KM_AI_BASE_URL 一致）
EXPOSE 8000

# 启动
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]