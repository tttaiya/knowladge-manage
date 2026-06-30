# Day07 Bad Case 复盘模板与执行说明

## 为什么要做

Agent 项目最容易被问到的问题是“错了怎么办”。Day07 增加 bad case 机制，用来记录失败样例、根因、修复动作和验证结果。这样项目不只是能跑 demo，还具备持续改进闭环。

## 怎么加的

新增模型：

- `app/models/bad_case.py`
- `BadCaseCreateRequest`
- `BadCaseRecord`

新增服务：

- `app/services/bad_case_service.py`
- 本地 JSON 存储目录：`badcases/`

新增接口：

- `POST /api/badcases`
- `GET /api/badcases`
- `GET /api/badcases/{case_id}`

主应用已在 `app/main.py` 注册 `bad_case.router`。

## 操作步骤

启动服务后创建一个 bad case：

```powershell
$body = @{
  trace_id = "trace_demo"
  case_type = "workflow"
  symptom = "Replanner 提前结束，证据不足"
  root_cause = "重规划判断缺少证据完整性约束"
  fix_action = "记录 replanner_action 并补充证据判断"
  verification_result = "重新运行诊断，trace 中可看到重规划决策"
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri "http://localhost:9900/api/badcases" -ContentType "application/json" -Body $body
```

查看列表：

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:9900/api/badcases"
```

## 验收标准

- 创建接口返回 `case_id`。
- `badcases/` 目录生成对应 JSON 文件。
- 列表接口能按创建时间倒序返回案例。
- 单个查询接口能返回 root_cause 和 fix_action。

## 亮点和思考

这个设计强调“可复盘、可验证、可沉淀”。它把一次失败变成下一轮优化输入：先看 trace 定位失败，再写 bad case，最后根据 bad case 改 prompt、工具、流程或评测集。面试讲解时可以说：我的 Agent 不是只追求一次回答正确，而是具备错误样本闭环。

## 面试题

1. 为什么 Agent 项目需要 bad case 机制？
2. bad case 里为什么要保存 trace_id？
3. case_type 应该如何划分？
4. 如何从 bad case 反推测试用例？
5. 如果 bad case 越积越多，你会如何做检索和聚类？
