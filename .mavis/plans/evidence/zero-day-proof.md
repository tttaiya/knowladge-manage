# Zero-day proof — 2026-07-01

> 证明：在 `days=30` 返回的 `documentTrend` 中，
> **`{ "date": "2026-07-01", "count": 0 }`** 这一数据点
> 同时被（a）SQL 真实查询 与 （b）API 真实响应 双向确认为 0。
> 这一天既没有任何文档新增，也没有补零逻辑上的伪数据。

## 1. 双重证明

### 1.1 接口真实返回

curl 调用（见 `api-response-sample.json`）：

```
$ curl -s -m 5 "http://localhost:19101/api/v1/stats/overview?days=30" \
    -H "Authorization: Bearer <token>"
HTTP 200
```

响应中第 29 个数据点（按 `date` 升序）：

```json
{ "date": "2026-07-01", "count": 0 }
```

完整响应文档化在 `api-response-sample.json`，camelCase 字段名 + 30 天
连续日期均与服务端实现一致。

### 1.2 MySQL 真实查询

```sql
-- 命令（MySQL 8.0.32 客户端连接 km-mysql:3306）
SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS cnt
FROM km_document
WHERE is_deleted = 0
  AND created_at >= '2026-07-01 00:00:00'
  AND created_at <  '2026-07-02 00:00:00'
GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d');
```

执行：

```bash
docker exec km-mysql mysql -u km -pkm123456 km -e \
  "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS cnt
   FROM km_document
   WHERE is_deleted = 0
     AND created_at >= '2026-07-01 00:00:00'
     AND created_at <  '2026-07-02 00:00:00'
   GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d');"
```

执行结果（`docker exec km-mysql mysql -u km -pkm123456 km -t -e "..."`）：

```
mysql: [Warning] Using a password on the command line interface can be insecure.
```

> MySQL 客户端在 `WHERE` 过滤后没有返回任何行（连表头都没有打印），
> 等价于 "Empty set"。这与 `count=0` 完全一致。

## 1.3 对照：全量按天聚合（证明 SQL 管道本身是工作的）

```sql
SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS cnt
FROM km_document WHERE is_deleted = 0
GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') ORDER BY day ASC;
```

```
+------------+-----+
| day        | cnt |
+------------+-----+
| 2026-06-30 |   1 |
+------------+-----+
```

> 数据库中只有 1 条文档，落在 `2026-06-30`。
> 完整原始输出见 `zero-day-mysql-stdout.txt` / `zero-day-only.txt`（workspace）。

## 2. 完整性交叉检查

- `documentTotal = 1`（API 返回）= `SELECT COUNT(*) FROM km_document WHERE is_deleted = 0` 的结果
- API 中 `2026-06-30 count = 1` 恰好对应那唯一一条文档的 `created_at`
- 其余 29 天 count = 0，包括 `2026-07-01`

## 3. 实现侧佐证

`backend-java/knowledge-management/km-admin-service/src/main/resources/mapper/StatsMapper.xml`
`documentTrend` 段（节选）：

```xml
<select id="selectDocumentTrend" resultType="com.km.admin.stats.dto.TrendDataDTO">
  SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COUNT(*) AS count
  FROM km_document
  WHERE is_deleted = 0
    AND created_at &gt;= #{fromDate}
    AND created_at &lt;  #{toDateExclusive}
  GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')
  ORDER BY date ASC
</select>
```

`StatsServiceImpl` 在 SQL 返回后用 `while` 循环补齐缺失日期，count 默认 `0L`。
所以 `2026-07-01 count=0` 是**真实补零**而非"找不到聚合行被当成 NULL"。

## 4. 结论

- API 与 SQL 双向证明 2026-07-01 当日无新增文档
- 前端 `StatisticsPage.vue` 收到 `count: 0` 会在 ECharts 折线图上对应点
  渲染为 y=0（`yAxis.minInterval = 1`，X 轴连续日期完整）
- 与 `2026-06-30 count = 1` 形成单点孤峰，验证了趋势 SQL 的正确性
