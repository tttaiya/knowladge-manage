# knowledge-management-jdk8

独立重写版知识管理工程，不依赖旧工程，不在旧工程基础上修改。

本工程用于实现 **F2 知识库管理**，包含：

- 后端：`Spring Boot 2.6.4 + MyBatis + MySQL`
- 前端：`Vue 3 + Vite`
- 数据库脚本：`sql/`
- 测试脚本：`test/`

---

## 1. 版本与硬性约束

### 1.1 Java 约束

本工程按 **JDK 1.8.0_181 语法约束** 编写。

也就是说：

- 只能使用 Java 8 语法和标准库能力
- 禁止使用 `Map.of()`、`List.of()`、`Set.of()`
- 禁止使用 `var`、`record`
- 禁止使用任何 Java 9+ 新 API

### 1.2 当前本机已验证环境

当前机器可直接使用的 Java 8 环境：

- `JAVA_HOME=C:\Program Files\Amazon Corretto\jdk1.8.0_492`
- `MAVEN_HOME=C:\tools\apache-maven-3.9.16`

说明：

- 你的要求是 **JDK 1.8.0_181 约束**
- 当前本机实际已验证的是 **Corretto 1.8.0_492（仍然属于 Java 8）**
- 因此：**代码语法和编译目标满足 Java 8 约束，当前机器可直接编译运行**

---

## 2. 项目目录说明

```text
knowledge-management-jdk8/
├─ km-admin-service/         后端服务
├─ frontend-web/             前端页面
├─ sql/                      数据库脚本
├─ test/                     测试脚本
└─ README.md                 当前说明文档
```

---

## 3. 你要先准备的前置条件

在运行前，请确认以下环境已经具备：

### 3.1 必须具备

- Java 8 环境
- Maven 3.8+
- MySQL 5.7+
- Node.js 18+（建议）
- npm 或 pnpm

### 3.2 推荐使用当前本机环境

建议直接使用当前机器上已经确认存在的环境：

```powershell
$env:JAVA_HOME='C:\Program Files\Amazon Corretto\jdk1.8.0_492'
$env:MAVEN_HOME='C:\tools\apache-maven-3.9.16'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
```

### 3.3 环境自检命令

请先执行：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:MAVEN_HOME\bin\mvn.cmd" -version
```

预期结果：

- `java version "1.8.x"`
- Maven 输出中使用的 Java home 指向 Java 8

---

## 4. 数据库初始化步骤

### 4.1 创建数据库

建议数据库名使用：

```sql
knowledge_management
```

你可以在 MySQL 中执行：

```sql
CREATE DATABASE IF NOT EXISTS knowledge_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 4.2 执行基础脚本

先执行：

- `sql/bootstrap-minimal.sql`
- `sql/knowledge-base.sql`

### 4.3 为什么要执行 bootstrap-minimal.sql

因为当前 F2 独立工程里，重处理接口会统计 `km_document` 表中 `READY` 文档数量。

如果你不建这个表，那么：

- 列表、新建、编辑、删除大概率可运行
- 但 `/api/v1/knowledge-bases/{id}/reprocess` 在执行时会因为缺少 `km_document` 表而报错

所以为了能完整联调，建议一起执行最小兼容脚本。

---

## 5. 后端配置步骤

### 5.1 application.yml 位置

后端配置文件在：

- `km-admin-service/src/main/resources/application.yml`

### 5.2 需要你修改的配置

请确认以下配置与你本机一致：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/knowledge_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
```

如果你的数据库账号密码不是 `root/root`，请按实际值修改。

### 5.3 后端端口

当前后端端口配置为：

```yaml
server:
  port: 18081
```

---

## 6. 后端编译步骤

进入后端目录：

```powershell
cd C:\Users\25100\Documents\Codex\2026-06-29\new-chat-3\knowledge-management-jdk8\km-admin-service
```

执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Amazon Corretto\jdk1.8.0_492'
$env:MAVEN_HOME='C:\tools\apache-maven-3.9.16'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
& "$env:MAVEN_HOME\bin\mvn.cmd" -B clean -DskipTests package
```

### 6.1 当前状态

这一步我已经替你验证过，当前结果是：

- `BUILD SUCCESS`

生成产物位置：

- `km-admin-service/target/km-admin-service-1.0.0.jar`

---

## 7. 后端启动步骤

### 7.1 直接启动 jar

```powershell
cd C:\Users\25100\Documents\Codex\2026-06-29\new-chat-3\knowledge-management-jdk8\km-admin-service
$env:JAVA_HOME='C:\Program Files\Amazon Corretto\jdk1.8.0_492'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\km-admin-service-1.0.0.jar
```

### 7.2 或者使用 Maven 启动

```powershell
cd C:\Users\25100\Documents\Codex\2026-06-29\new-chat-3\knowledge-management-jdk8\km-admin-service
$env:JAVA_HOME='C:\Program Files\Amazon Corretto\jdk1.8.0_492'
$env:MAVEN_HOME='C:\tools\apache-maven-3.9.16'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
& "$env:MAVEN_HOME\bin\mvn.cmd" spring-boot:run
```

### 7.3 启动成功后的检查

浏览器或接口工具访问：

```text
http://127.0.0.1:18081/api/v1/knowledge-bases?page=1&pageSize=10
```

如果数据库初始化成功，你会拿到统一 JSON 返回。

---

## 8. 前端配置步骤

### 8.1 进入前端目录

```powershell
cd C:\Users\25100\Documents\Codex\2026-06-29\new-chat-3\knowledge-management-jdk8\frontend-web
```

### 8.2 安装依赖

```powershell
npm install
```

### 8.3 启动前端

```powershell
npm run dev
```

### 8.4 访问页面

默认访问：

```text
http://127.0.0.1:5173/knowledge-management/bases
```

### 8.5 前端当前请求地址

当前前端默认调用：

```text
http://127.0.0.1:18081/api/v1/knowledge-bases
```

如果你改了后端端口，需要同步修改：

- `frontend-web/src/api/modules/knowledge-base.ts`

---

## 9. 测试步骤

### 9.1 执行接口测试脚本

先确保后端已启动，再执行：

```powershell
cd C:\Users\25100\Documents\Codex\2026-06-29\new-chat-3\knowledge-management-jdk8\test
python .\knowledge-base-test.py
```

或者：

```powershell
cd C:\Users\25100\Documents\Codex\2026-06-29\new-chat-3\knowledge-management-jdk8\test
.\run-tests.ps1
```

### 9.2 测试覆盖内容

当前脚本覆盖：

- 创建知识库
- 查询详情
- 分页查询
- 未确认时策略变更失败
- 确认后策略变更成功
- 重处理接口可调用
- 删除成功

---

## 10. 推荐你的实际配置顺序

请按下面顺序做：

1. 配置 `JAVA_HOME` 和 `MAVEN_HOME`
2. 执行 `java -version` 和 `mvn -version`
3. 启动 MySQL
4. 创建 `knowledge_management` 数据库
5. 执行 `sql/bootstrap-minimal.sql`
6. 执行 `sql/knowledge-base.sql`
7. 检查并修改 `application.yml` 数据库账号密码
8. 执行后端 `mvn clean package`
9. 启动后端服务
10. 用浏览器或 Postman 验证列表接口
11. 进入前端目录执行 `npm install`
12. 执行 `npm run dev`
13. 打开 `/knowledge-management/bases`
14. 执行测试脚本做回归验证

---

## 11. 常见问题

### 11.1 mvn 使用的不是 Java 8

解决方式：

```powershell
$env:JAVA_HOME='C:\Program Files\Amazon Corretto\jdk1.8.0_492'
$env:MAVEN_HOME='C:\tools\apache-maven-3.9.16'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
```

然后重新执行：

```powershell
& "$env:MAVEN_HOME\bin\mvn.cmd" -version
```

### 11.2 数据库连接失败

请检查：

- MySQL 是否启动
- 库名是否为 `knowledge_management`
- 用户名和密码是否与 `application.yml` 一致
- 端口是否为 `3306`

### 11.3 reprocess 接口报 `km_document` 不存在

说明你没有执行：

- `sql/bootstrap-minimal.sql`

### 11.4 前端打不开接口

请检查：

- 后端是否启动在 `18081`
- 前端 API 地址是否与后端端口一致
- 浏览器控制台是否有跨域或网络错误

---

## 12. 关键文件路径

- 后端入口：`km-admin-service/src/main/java/com/km/admin/KnowledgeBaseApplication.java`
- Controller：`km-admin-service/src/main/java/com/km/admin/controller/KnowledgeBaseController.java`
- Service：`km-admin-service/src/main/java/com/km/admin/service/KnowledgeBaseService.java`
- Mapper XML：`km-admin-service/src/main/resources/mapper/KnowledgeBaseMapper.xml`
- 后端配置：`km-admin-service/src/main/resources/application.yml`
- 前端列表页：`frontend-web/src/views/knowledge/KnowledgeBaseList.vue`
- 前端详情页：`frontend-web/src/views/knowledge/KnowledgeBaseDetail.vue`
- 表单组件：`frontend-web/src/components/knowledge/KnowledgeBaseFormDialog.vue`
- 知识库建表：`sql/knowledge-base.sql`
- 最小兼容表：`sql/bootstrap-minimal.sql`

---

## 13. 当前结论

你现在**确实需要前置条件配置步骤**，否则代码虽然已经写好并且后端已编译通过，但你在本机仍可能卡在：

- Java 版本不一致
- Maven 未走 Java 8
- MySQL 未建库建表
- 前后端端口不一致
- `km_document` 缺失导致重处理接口报错

因此：

**请优先按本 README 的第 3～10 节完成配置。**
