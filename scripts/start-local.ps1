param(
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root "infra\docker\docker-compose.demo.yml"

Write-Host "检查 Docker..."
docker version | Out-Null
docker compose version | Out-Null

Write-Host "启动知识管理全量环境..."
if ($NoBuild) {
    docker compose -f $ComposeFile up -d
} else {
    docker compose -f $ComposeFile up -d --build
}

Write-Host "等待服务健康，首次构建可能需要数分钟..."
$services = @(
    "km-mysql",
    "km-redis",
    "km-rabbitmq",
    "km-minio",
    "km-ai-service",
    "km-admin-service",
    "km-worker-service",
    "km-search-service",
    "km-gateway-service"
)

foreach ($svc in $services) {
    $deadline = (Get-Date).AddMinutes(8)
    do {
        $state = docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $svc 2>$null
        if ($state -eq "healthy" -or $state -eq "running") {
            Write-Host "$svc 已就绪"
            break
        }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
}

Write-Host ""
Write-Host "启动完成："
Write-Host "Gateway:  http://localhost:9000"
Write-Host "Nginx:    http://localhost:8080"
Write-Host "RabbitMQ: http://localhost:15672  用户 km / km123456"
Write-Host "Nacos:    http://localhost:8848/nacos"
Write-Host "MinIO:    http://localhost:9001  用户 kmminio / kmminio123"
Write-Host "Admin:    http://localhost:9101/actuator/health"
Write-Host "Worker:   http://localhost:9102/actuator/health"

