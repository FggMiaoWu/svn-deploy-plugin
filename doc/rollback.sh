#!/bin/bash
# 回退脚本: 生产环境
# 对应 deploy.sh 的回退操作
# 用法: 在 deploy.sh 同目录下执行 bash rollback.sh
#   或指定备份时间戳: bash rollback.sh 20260827173350

TARGET_DIR="/mnt/tomcat/tomcat7_workorder_25100/webapps/workorder12345"

# 如果传入时间戳参数，使用指定时间戳；否则自动查找最新的备份文件
BACKUP_TS="$1"

echo "========================================"
echo "  回退: 生产环境"
echo "  目标目录: $TARGET_DIR"
if [ -n "$BACKUP_TS" ]; then
    echo "  指定备份时间戳: $BACKUP_TS"
else
    echo "  自动查找最新备份"
fi
echo "========================================"

# 回退函数: 查找备份文件并恢复，找不到备份则删除(新文件部署的情况)
rollback_file() {
    local target_path="$1"
    local file_name
    file_name=$(basename "$target_path")

    if [ -n "$BACKUP_TS" ]; then
        # 指定时间戳模式
        local backup_path="${target_path}_bak_${BACKUP_TS}"
        if [ -f "$backup_path" ]; then
            echo "回退文件(指定备份): $file_name"
            cp "$backup_path" "$target_path"
            if [ $? -eq 0 ]; then
                echo "  -> 回退成功"
            else
                echo "  -> 回退失败!"
            fi
        else
            echo "警告: 备份文件不存在: $file_name (可能是新文件部署)"
            echo "  -> 删除新部署的文件"
            rm -f "$target_path"
        fi
    else
        # 自动查找最新备份模式
        local backup_path
        backup_path=$(ls -t "${target_path}_bak_"* 2>/dev/null | head -1)
        if [ -n "$backup_path" ]; then
            echo "回退文件(自动备份): $file_name <- $(basename "$backup_path")"
            cp "$backup_path" "$target_path"
            if [ $? -eq 0 ]; then
                echo "  -> 回退成功"
            else
                echo "  -> 回退失败!"
            fi
        else
            echo "警告: 无备份文件: $file_name (可能是新文件部署)"
            echo "  -> 删除新部署的文件"
            rm -f "$target_path"
        fi
    fi
}

# ===== 1. CalloutJobConversation.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/CalloutJobConversation.class"

# ===== 2. CalloutJobTask.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/CalloutJobTask.class"

# ===== 3. WpController.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/WpController.class"

# ===== 4. WpController$1.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/WpController\$1.class"

# ===== 5. WpController$2.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/WpController\$2.class"

# ===== 6. WorkOderInfo.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/WorkOderInfo.class"

# ===== 7. VisitServiceImpl.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/service/impl/VisitServiceImpl.class"

# ===== 8. VisitMapper.xml =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/mappers/oracle/VisitMapper.xml"

# ===== 9. WpMapper.xml =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/mappers/oracle/WpMapper.xml"

# ===== 10. WpWeightMMapper.xml =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/mappers/oracle/WpWeightMMapper.xml"

# ===== 11. AcceptanceOrder.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/AcceptanceOrder.jsp"

# ===== 12. Backstage_EndexDeal.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Backstage_EndexDeal.jsp"

# ===== 13. OutboundManageAcceptanceOrder.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/OutboundManageAcceptanceOrder.jsp"

# ===== 14. Reception_dealAllTempOrder.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Reception_dealAllTempOrder.jsp"

# ===== 15. Reception_dealPreDispatchAudit.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Reception_dealPreDispatchAudit.jsp"

# ===== 16. Reception_dealTempOrder.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Reception_dealTempOrder.jsp"

# ===== 17. Reception_dealWebTempOrder.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Reception_dealWebTempOrder.jsp"

# ===== 18. Reception_queryVisitList.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Reception_queryVisitList.jsp"

# ===== 19. Reception_queryYiqiVisitList.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Reception_queryYiqiVisitList.jsp"

# ===== 20. ZXAcceptanceOrder.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/ZXAcceptanceOrder.jsp"

# ===== 21. queryOrderInfoByWpid.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/queryOrderInfoByWpid.jsp"

# ===== 22. unWeighed.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/unWeighed.jsp"

# ===== 23. weitVisit.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/weitVisit.jsp"

# ===== 24. VisitController.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/VisitController.class"

# ===== 25. VisitMapper.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/dao/VisitMapper.class"

# ===== 26. VisitService.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/service/VisitService.class"

# ===== 27. Reception_queryIntelVisitList.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Reception_queryIntelVisitList.jsp"

# ===== 28. CenterAndFunDeptController.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/CenterAndFunDeptController.class"

# ===== 29. CenterAndFunDeptController$5.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/CenterAndFunDeptController\$5.class"

# ===== 30. CenterAndFunDeptController$3.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/CenterAndFunDeptController\$3.class"

# ===== 31. CenterAndFunDeptController$1.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/CenterAndFunDeptController\$1.class"

# ===== 32. CenterAndFunDeptController$4.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/CenterAndFunDeptController\$4.class"

# ===== 33. CenterAndFunDeptController$2.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/CenterAndFunDeptController\$2.class"

# ===== 34. IntelNavTestController.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/IntelNavTestController.class"

# ===== 35. WpYiqiVisitController.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/controller/WpYiqiVisitController.class"

# ===== 36. WpYiqiMapper.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/dao/WpYiqiMapper.class"

# ===== 37. YiqiVisitRemindTask.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class"

# ===== 38. WpBeInfo.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfo.class"

# ===== 39. WpBeInfoReviewLog.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfoReviewLog.class"

# ===== 40. WpYiqiService.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/service/WpYiqiService.class"

# ===== 41. WpYiqiServiceImpl.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/service/impl/WpYiqiServiceImpl.class"

# ===== 42. WpYiqiMapper.xml =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml"

# ===== 43. Reception_dealYiqiVisitOrder.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/Reception_dealYiqiVisitOrder.jsp"

# ===== 44. queryOrderhandleByWpid.jsp =====
rollback_file "${TARGET_DIR}/wp/jsp/queryOrderhandleByWpid.jsp"

# ===== 45. IntellVisitRsultEnum.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/enums/IntellVisitRsultEnum.class"

# ===== 46. IntellVisitResultTask.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/job/IntellVisitResultTask.class"

# ===== 47. CallOutResponse.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/CallOutResponse.class"

# ===== 48. CallOutResponse$JobData.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/CallOutResponse\$JobData.class"

# ===== 49. SendOutbound.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/SendOutbound.class"

# ===== 50. WpIntelVisitCallback.class =====
rollback_file "${TARGET_DIR}/WEB-INF/classes/cn/sh/ideal/wp/model/WpIntelVisitCallback.class"

echo "========================================"
echo "回退完成!"
echo "========================================"
