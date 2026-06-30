"""Bad case 复盘接口。"""

from fastapi import APIRouter, HTTPException

from app.models.bad_case import BadCaseCreateRequest
from app.services.bad_case_service import bad_case_service

router = APIRouter()


@router.post("/badcases")
async def create_bad_case(request: BadCaseCreateRequest):
    case = bad_case_service.create(request)
    return {"code": 200, "message": "success", "data": case.model_dump()}


@router.get("/badcases")
async def list_bad_cases():
    cases = bad_case_service.list_cases()
    return {"code": 200, "message": "success", "data": [case.model_dump() for case in cases]}


@router.get("/badcases/{case_id}")
async def get_bad_case(case_id: str):
    case = bad_case_service.get(case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="bad case 不存在")
    return {"code": 200, "message": "success", "data": case.model_dump()}
