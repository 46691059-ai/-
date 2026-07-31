-- 已部署旧版数据库的升级脚本。
-- 执行前必须完成全量备份；脚本保留旧表为 *_legacy，便于回滚和数据核验。
-- 新安装环境不要执行本文件。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

RENAME TABLE
    pm_project TO pm_project_legacy,
    pm_project_stage TO pm_project_stage_legacy,
    pm_project_task TO pm_project_task_legacy,
    pm_project_member TO pm_project_member_legacy;

-- 生产迁移采用“新建 V1.0 空库 -> 导入旧数据 -> 校验 -> 切换”的蓝绿方式。
-- 字段映射：
-- pm_project.project_code       -> project_info.project_no
-- pm_project.org_id             -> project_info.department_id
-- sys_user.employee_id          -> project_info.leader_id / project_member.employee_id
-- pm_project.investment_amount  -> project_info.budget_amount
-- pm_project_stage.owner_user_id-> project_stage.responsible_person（经 sys_user.employee_id 转换）
-- pm_project_task.assignee_user_id -> project_task.responsible_person（经 sys_user.employee_id 转换）
-- 无 employee_id 映射的数据必须进入迁移异常清单，不允许静默丢弃。

SET FOREIGN_KEY_CHECKS = 1;
