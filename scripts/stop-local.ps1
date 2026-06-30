param(
    [switch]$PurgeData
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root "infra\docker\docker-compose.demo.yml"

if ($PurgeData) {
    Write-Host "停止环境并清理数据卷..."
    docker compose -f $ComposeFile down -v
} else {
    Write-Host "停止环境，保留 MySQL/MinIO/ChromaDB 数据卷..."
    docker compose -f $ComposeFile down
}

Write-Host "已停止。"

