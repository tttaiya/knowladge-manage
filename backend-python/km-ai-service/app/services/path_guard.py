"""
F4 受限文件根目录守卫（commit #22）。

R-F4-4：filePath resolve 后必须位于 ALLOWED_DOCUMENT_ROOT
R-F4-5：拒绝 ../、宿主路径、任意文件读取
R-F4-6：扩展名白名单 + 与 filePath 后缀一致
R-F4-7：单文件 ≤ 50MB
R-F4-8：PDF 页数上限（maxPdfPages 默认 200，commit #26 验证用）
"""
import os
from pathlib import Path

from fastapi import HTTPException, status

ALLOWED_EXTS = {
    ".pdf", ".docx", ".txt", ".md",
    ".pptx", ".xlsx",
    ".png", ".jpg", ".jpeg", ".bmp", ".webp",
}

MAX_FILE_SIZE = int(os.environ.get("MAX_FILE_SIZE_MB", "50")) * 1024 * 1024
MAX_PDF_PAGES = int(os.environ.get("MAX_PDF_PAGES", "200"))


def allowed_root() -> Path:
    """动态读取受限根目录，保证测试隔离并支持容器滚动配置。"""
    return Path(os.environ.get("ALLOWED_DOCUMENT_ROOT", "/data/task-files")).resolve()


def resolve_safe_path(file_path: str, extension: str | None) -> Path:
    """校验 filePath 在 ALLOWED_ROOT 内、扩展名合法、大小可控。

    :raises HTTPException: 400/403/404/413
    :returns: 解析后的绝对 Path 对象（确保存在于 ALLOWED_ROOT 内）
    """
    if not file_path:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="filePath is required")

    p = Path(file_path).resolve()

    root = allowed_root()

    # R-F4-4：必须在 ALLOWED_DOCUMENT_ROOT 内
    try:
        p.relative_to(root)
    except ValueError:
        raise HTTPException(
            status.HTTP_403_FORBIDDEN,
            detail=f"filePath outside ALLOWED_DOCUMENT_ROOT ({root})",
        )

    # R-F4-6a：扩展名白名单
    actual_ext = p.suffix.lower()
    if actual_ext not in ALLOWED_EXTS:
        raise HTTPException(
            status.HTTP_400_BAD_REQUEST,
            detail=f"extension not allowed: {actual_ext} (allowed: {sorted(ALLOWED_EXTS)})",
        )

    # R-F4-6b：extension 与 filePath 后缀一致
    if extension:
        expected_ext = "." + extension.lower().lstrip(".")
        if actual_ext != expected_ext:
            raise HTTPException(
                status.HTTP_400_BAD_REQUEST,
                detail=f"extension mismatch: filePath has {actual_ext}, request says {expected_ext}",
            )

    # R-F4-7：大小校验
    if not p.exists():
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="file not found")
    if not p.is_file():
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="filePath is not a regular file")
    size = p.stat().st_size
    if size == 0:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="file is empty")
    if size > MAX_FILE_SIZE:
        raise HTTPException(
            status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"file too large: {size} bytes (max {MAX_FILE_SIZE})",
        )

    return p
