#!/bin/bash
# 部署环境: 生产环境
# 生成时间: Thu Aug 27 17:00:33 CST 2026

TARGET_DIR="/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345"
BACKUP_SUFFIX="_bak_$(date +%Y%m%d%H%M%S)"

echo "========================================"
echo "  部署到: 生产环境"
echo "  目标目录: $TARGET_DIR"
echo "========================================"

# 部署文件: HztUtil.class
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/utils/HztUtil.class' ]; then
    echo '备份文件:HztUtil.class'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/utils/HztUtil.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/utils/HztUtil.class'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:HztUtil.class'
        mv './HztUtil.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/utils/HztUtil.class'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/utils/HztUtil.class')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:HztUtil.class'
    mv './HztUtil.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/utils/HztUtil.class'
fi

# 部署文件: YiqiVisitRemindTask.class
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class' ]; then
    echo '备份文件:YiqiVisitRemindTask.class'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:YiqiVisitRemindTask.class'
        mv './YiqiVisitRemindTask.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:YiqiVisitRemindTask.class'
    mv './YiqiVisitRemindTask.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class'
fi

# 部署文件: YiqiVisitRemindTask.class
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class' ]; then
    echo '备份文件:YiqiVisitRemindTask.class'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:YiqiVisitRemindTask.class'
        mv './YiqiVisitRemindTask.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:YiqiVisitRemindTask.class'
    mv './YiqiVisitRemindTask.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/job/YiqiVisitRemindTask.class'
fi

# 部署文件: WpYiqiMapper.xml
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml' ]; then
    echo '备份文件:WpYiqiMapper.xml'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:WpYiqiMapper.xml'
        mv './WpYiqiMapper.xml' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:WpYiqiMapper.xml'
    mv './WpYiqiMapper.xml' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml'
fi

# 部署文件: WpYiqiMapper.xml
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml' ]; then
    echo '备份文件:WpYiqiMapper.xml'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:WpYiqiMapper.xml'
        mv './WpYiqiMapper.xml' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:WpYiqiMapper.xml'
    mv './WpYiqiMapper.xml' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/mappers/oracle/WpYiqiMapper.xml'
fi

# 部署文件: WpYiqiMapper.class
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/dao/WpYiqiMapper.class' ]; then
    echo '备份文件:WpYiqiMapper.class'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/dao/WpYiqiMapper.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/dao/WpYiqiMapper.class'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:WpYiqiMapper.class'
        mv './WpYiqiMapper.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/dao/WpYiqiMapper.class'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/dao/WpYiqiMapper.class')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:WpYiqiMapper.class'
    mv './WpYiqiMapper.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/dao/WpYiqiMapper.class'
fi

# 部署文件: WpBeInfo.class
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfo.class' ]; then
    echo '备份文件:WpBeInfo.class'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfo.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfo.class'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:WpBeInfo.class'
        mv './WpBeInfo.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfo.class'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfo.class')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:WpBeInfo.class'
    mv './WpBeInfo.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfo.class'
fi

# 部署文件: WpBeInfoReviewLog.class
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfoReviewLog.class' ]; then
    echo '备份文件:WpBeInfoReviewLog.class'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfoReviewLog.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfoReviewLog.class'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:WpBeInfoReviewLog.class'
        mv './WpBeInfoReviewLog.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfoReviewLog.class'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfoReviewLog.class')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:WpBeInfoReviewLog.class'
    mv './WpBeInfoReviewLog.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/model/WpBeInfoReviewLog.class'
fi

# 部署文件: WpYiqiService.class
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/WpYiqiService.class' ]; then
    echo '备份文件:WpYiqiService.class'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/WpYiqiService.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/WpYiqiService.class'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:WpYiqiService.class'
        mv './WpYiqiService.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/WpYiqiService.class'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/WpYiqiService.class')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:WpYiqiService.class'
    mv './WpYiqiService.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/WpYiqiService.class'
fi

# 部署文件: WpYiqiServiceImpl.class
if [ -f '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/impl/WpYiqiServiceImpl.class' ]; then
    echo '备份文件:WpYiqiServiceImpl.class'
    cp '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/impl/WpYiqiServiceImpl.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/impl/WpYiqiServiceImpl.class'"$BACKUP_SUFFIX"
    if [ $? -eq 0 ]; then
        echo '部署文件:WpYiqiServiceImpl.class'
        mv './WpYiqiServiceImpl.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/impl/WpYiqiServiceImpl.class'
    else
        echo '备份失败，跳过部署'
    fi
else
    target_dir=$(dirname '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/impl/WpYiqiServiceImpl.class')
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
    fi
    echo '新文件，直接部署:WpYiqiServiceImpl.class'
    mv './WpYiqiServiceImpl.class' '/mnt/tomcat2/tomcat7_workorder_25400/webapps/workorder12345/WEB-INF/classes/cn/sh/ideal/wp/service/impl/WpYiqiServiceImpl.class'
fi

echo "========================================"
echo "部署完成!"
echo "备份文件后缀: $BACKUP_SUFFIX"
echo "========================================"

