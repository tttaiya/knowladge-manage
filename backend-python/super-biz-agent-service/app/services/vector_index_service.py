"""向量索引服务模块"""

from datetime import datetime
from pathlib import Path
import json
from typing import Any, Dict, Optional

from loguru import logger

from app.core.milvus_client import milvus_manager
from app.models.orm.knowledge import KnowledgeChunk
from app.services.document_splitter_service import document_splitter_service
from app.services.vector_embedding_service import vector_embedding_service
from app.services.vector_store_manager import vector_store_manager


class IndexingResult:
    """索引结果类"""

    def __init__(self):
        self.success = False
        self.directory_path = ""
        self.total_files = 0
        self.success_count = 0
        self.fail_count = 0
        self.start_time: Optional[datetime] = None
        self.end_time: Optional[datetime] = None
        self.error_message = ""
        self.failed_files: Dict[str, str] = {}

    def increment_success_count(self):
        """增加成功计数"""
        self.success_count += 1

    def increment_fail_count(self):
        """增加失败计数"""
        self.fail_count += 1

    def add_failed_file(self, file_path: str, error: str):
        """添加失败文件"""
        self.failed_files[file_path] = error

    def get_duration_ms(self) -> int:
        """获取耗时（毫秒）"""
        if self.start_time and self.end_time:
            return int((self.end_time - self.start_time).total_seconds() * 1000)
        return 0

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            "success": self.success,
            "directory_path": self.directory_path,
            "total_files": self.total_files,
            "success_count": self.success_count,
            "fail_count": self.fail_count,
            "duration_ms": self.get_duration_ms(),
            "error_message": self.error_message,
            "failed_files": self.failed_files,
        }


class VectorIndexService:
    """向量索引服务 - 负责读取文件、生成向量、存储到 Milvus"""

    def __init__(self):
        """初始化向量索引服务"""
        self.upload_path = "./uploads"
        logger.info("向量索引服务初始化完成")

    def index_chunks(self, chunks: list[KnowledgeChunk], document_name: str = "") -> dict[str, str]:
        """
        将知识库 chunk 向量化并写入 Milvus。

        Returns:
            dict[str, str]: chunk_id -> vector_id 映射
        """
        if not chunks:
            return {}

        milvus_manager.connect()
        collection = milvus_manager.get_collection()
        texts = [chunk.content for chunk in chunks]
        vectors = vector_embedding_service.embed_documents(texts)
        payload = []
        result: dict[str, str] = {}

        for chunk, vector in zip(chunks, vectors, strict=False):
            vector_id = chunk.id
            payload.append(
                {
                    "id": vector_id,
                    "chunk_id": chunk.id,
                    "document_id": chunk.document_id,
                    "knowledge_base_id": chunk.knowledge_base_id,
                    "content": chunk.content,
                    "section_path": chunk.section_path or "全文",
                    "file_name": document_name or "",
                    "metadata": {
                        "chunk_id": chunk.id,
                        "document_id": chunk.document_id,
                        "knowledge_base_id": chunk.knowledge_base_id,
                        "section_path": chunk.section_path or "全文",
                        "file_name": document_name or "",
                    },
                    "vector": vector,
                }
            )
            result[chunk.id] = vector_id

        collection.insert(payload)
        collection.flush()
        logger.info(f"知识库向量写入完成: {len(payload)} 个 chunk, document={document_name or 'unknown'}")
        return result

    def delete_by_document_id(self, document_id: str) -> int:
        return self._delete_by_field_values("document_id", [document_id])

    def delete_by_knowledge_base_id(self, knowledge_base_id: str) -> int:
        return self._delete_by_field_values("knowledge_base_id", [knowledge_base_id])

    def delete_by_chunk_ids(self, chunk_ids: list[str]) -> int:
        return self._delete_by_field_values("chunk_id", chunk_ids)

    def index_directory(self, directory_path: Optional[str] = None) -> IndexingResult:
        """
        索引指定目录下的所有文件

        Args:
            directory_path: 目录路径（可选，默认使用配置的上传目录）

        Returns:
            IndexingResult: 索引结果
        """
        result = IndexingResult()
        result.start_time = datetime.now()

        try:
            # 使用指定目录或默认上传目录
            target_path = directory_path if directory_path else self.upload_path
            dir_path = Path(target_path).resolve()

            if not dir_path.exists() or not dir_path.is_dir():
                raise ValueError(f"目录不存在或不是有效目录: {target_path}")

            result.directory_path = str(dir_path)

            # 获取所有支持的文件
            files = list(dir_path.glob("*.txt")) + list(dir_path.glob("*.md"))

            if not files:
                logger.warning(f"目录中没有找到支持的文件: {target_path}")
                result.total_files = 0
                result.success = True
                result.end_time = datetime.now()
                return result

            result.total_files = len(files)
            logger.info(f"开始索引目录: {target_path}, 找到 {len(files)} 个文件")

            # 遍历并索引每个文件
            for file_path in files:
                try:
                    self.index_single_file(str(file_path))
                    result.increment_success_count()
                    logger.info(f"✓ 文件索引成功: {file_path.name}")
                except Exception as e:
                    result.increment_fail_count()
                    result.add_failed_file(str(file_path), str(e))
                    logger.error(f"✗ 文件索引失败: {file_path.name}, 错误: {e}")

            result.success = result.fail_count == 0
            result.end_time = datetime.now()

            logger.info(
                f"目录索引完成: 总数={result.total_files}, "
                f"成功={result.success_count}, 失败={result.fail_count}"
            )

            return result

        except Exception as e:
            logger.error(f"索引目录失败: {e}")
            result.success = False
            result.error_message = str(e)
            result.end_time = datetime.now()
            return result

    def index_single_file(self, file_path: str):
        """
        索引单个文件 (使用新的 LangChain 分割器)

        Args:
            file_path: 文件路径

        Raises:
            ValueError: 文件不存在时抛出
            RuntimeError: 索引失败时抛出
        """
        path = Path(file_path).resolve()

        if not path.exists() or not path.is_file():
            raise ValueError(f"文件不存在: {file_path}")

        logger.info(f"开始索引文件: {path}")

        try:
            # 1. 读取文件内容
            content = path.read_text(encoding="utf-8")
            logger.info(f"读取文件: {path}, 内容长度: {len(content)} 字符")

            # 2. 删除该文件的旧数据（如果存在）
            normalized_path = path.as_posix()
            vector_store_manager.delete_by_source(normalized_path)

            # 3. 使用新的文档分割器
            documents = document_splitter_service.split_document(content, normalized_path)
            logger.info(f"文档分割完成: {file_path} -> {len(documents)} 个分片")

            # 4. 添加文档到向量存储
            if documents:
                vector_store_manager.add_documents(documents)
                logger.info(f"文件索引完成: {file_path}, 共 {len(documents)} 个分片")
            else:
                logger.warning(f"文件内容为空或无法分割: {file_path}")

        except Exception as e:
            logger.error(f"索引文件失败: {file_path}, 错误: {e}")
            raise RuntimeError(f"索引文件失败: {e}") from e

    def _delete_by_field_values(self, field_name: str, values: list[str]) -> int:
        if not values:
            return 0
        try:
            milvus_manager.connect()
            collection = milvus_manager.get_collection()
            quoted = ", ".join(json.dumps(value, ensure_ascii=False) for value in values if value)
            if not quoted:
                return 0
            expr = f"{field_name} in [{quoted}]"
            result = collection.delete(expr)
            deleted_count = result.delete_count if hasattr(result, "delete_count") else 0
            collection.flush()
            logger.info(f"Milvus 删除完成: field={field_name}, count={deleted_count}")
            return deleted_count
        except Exception as e:
            logger.warning(f"Milvus 删除失败: field={field_name}, error={e}")
            return 0


# 全局单例
vector_index_service = VectorIndexService()
