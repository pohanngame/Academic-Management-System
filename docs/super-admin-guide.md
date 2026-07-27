# 超级管理员使用与验收指南

## 权限矩阵

| 能力 | TEACHER | SUPER_ADMIN |
| --- | --- | --- |
| 使用自己的教师档案、附件和 AI 配置 | 是 | 是 |
| 访问 `/api/admin/**` | 否（403） | 是 |
| 查看系统概览、用户列表和管理员审计 | 否 | 是 |
| 修改其他账户的角色或状态 | 否 | 受保护规则约束 |
| 创建和下载完整备份 | 否 | 是，创建前重新验证密码 |
| 查看密码哈希、教师 AI Key 明文 | 否 | 否 |
| 冒充教师或任意编辑教师私人数据 | 否 | 否 |

前端菜单和路由守卫只用于体验；真正权限由后端 Spring Security 强制执行。

## 创建首位 SUPER_ADMIN

系统不会创建默认管理员，也不会把第一个注册用户自动提升。部署者必须在服务器终端运行一次性
bootstrap。变量值由部署者现场提供，不要写入 Git、命令历史或文档。`docker compose run -e`
显式把这些短生命周期变量传入 one-off 容器；当前 `docker-compose.yml` 不会默认传递未声明的宿主机变量：

```bash
export APP_ADMIN_BOOTSTRAP_ENABLED=true
export APP_ADMIN_BOOTSTRAP_ACTION=create
export APP_ADMIN_BOOTSTRAP_USERNAME='<管理员用户名>'
export APP_ADMIN_BOOTSTRAP_EMAIL='<管理员邮箱>'
export APP_ADMIN_BOOTSTRAP_DISPLAY_NAME='<显示名称>'
read -r -s -p '管理员密码（8–64 字符）: ' APP_ADMIN_BOOTSTRAP_PASSWORD
echo
export APP_ADMIN_BOOTSTRAP_PASSWORD

docker compose --profile full run --rm --no-deps \
  -e APP_ADMIN_BOOTSTRAP_ENABLED \
  -e APP_ADMIN_BOOTSTRAP_ACTION \
  -e APP_ADMIN_BOOTSTRAP_USERNAME \
  -e APP_ADMIN_BOOTSTRAP_EMAIL \
  -e APP_ADMIN_BOOTSTRAP_PASSWORD \
  -e APP_ADMIN_BOOTSTRAP_DISPLAY_NAME \
  backend

unset APP_ADMIN_BOOTSTRAP_ENABLED APP_ADMIN_BOOTSTRAP_ACTION
unset APP_ADMIN_BOOTSTRAP_USERNAME APP_ADMIN_BOOTSTRAP_EMAIL
unset APP_ADMIN_BOOTSTRAP_PASSWORD APP_ADMIN_BOOTSTRAP_DISPLAY_NAME
```

命令使用 BCrypt 保存密码，事务成功后自动退出，不输出密码或哈希。创建的管理员仍有
`teacher_profile`，因此既有教师功能和 `teacherId` 隔离保持不变。

如需把已有教师提升为首位管理员，将 action 设为 `promote`，并显式提供该账户当前的用户名、邮箱及
要设置的新强密码；`promote` 不需要显示名称，可省略对应变量和 `-e` 参数。参数冲突或不完整会安全失败。
命令必须输出 `SUPER_ADMIN bootstrap completed.` 且退出码为 0 才算成功。

## 增加第二名管理员

1. 让第二名用户通过普通注册成为 TEACHER。
2. 当前 SUPER_ADMIN 打开“系统管理 → 用户管理”。
3. 找到目标账户，确认后将角色改为 SUPER_ADMIN。
4. 检查审计日志存在成功记录。
5. 让第二名管理员重新登录并验证管理员页面。

注册接口始终创建 TEACHER，前端提交 role 字段也不能提升权限。

## 保护规则与 JWT 立即失效

- 不能禁用自己。
- 不能撤销自己的 SUPER_ADMIN 角色。
- 不能禁用或降级最后一名 ACTIVE SUPER_ADMIN。
- 不物理删除账户。
- 不允许查看教师 AI Key、密码哈希，不提供教师冒充。

JWT 带有 `auth_version`，但每次认证都会重新读取当前账户状态、角色和版本。禁用账户或改变角色时
后端同步增加 `auth_version`，因此旧 JWT 立即失效；伪造或过期的 role claim 不能获得管理员权限。

角色/状态、`auth_version` 和成功审计处于同一事务；成功审计失败时全部回滚。业务失败审计在原事务
结束后独立尝试，审计自身失败不会覆盖原始业务错误。

## 用户管理人工验收

1. 注册普通账户，确认角色为 TEACHER。
2. 用教师 Token 请求 `/api/admin/overview`，确认返回 403。
3. 用 SUPER_ADMIN 查看概览、用户列表和审计日志。
4. 尝试禁用自己、降级自己，确认均被拒绝。
5. 只有一名 ACTIVE SUPER_ADMIN 时，确认不能禁用或降级该账户。
6. 创建第二名管理员后，修改其中一名的角色和状态，确认成功。
7. 使用变更前旧 JWT 再访问，确认立即失效。
8. 检查管理响应不含密码哈希、API Key、密文或教师私人学术数据。
9. 检查成功和失败操作均有脱敏审计记录。
