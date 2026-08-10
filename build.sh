#!/bin/bash
# SVN Deploy Plugin 构建脚本
# 用法: ./build.sh

set -e

# 项目根目录（脚本所在目录）
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

echo "========================================"
echo "  SVN Deploy Plugin 构建"
echo "========================================"
echo "项目目录: $PROJECT_DIR"
echo ""

# 检查 gradlew 是否存在
if [ ! -f "./gradlew" ]; then
    echo "[错误] 未找到 gradlew，请确认在项目根目录执行"
    exit 1
fi

# 检查 JDK 17（IntelliJ Platform 2023+ 强制要求）
JAVA_HOME_17="/Users/Admin/Library/Java/JavaVirtualMachines/azul-17.0.20/Contents/Home"
if [ ! -d "$JAVA_HOME_17" ]; then
    echo "[警告] 未找到 JDK 17: $JAVA_HOME_17"
    echo "       gradle.properties 中已配置 org.gradle.java.home，Gradle 会自动使用"
else
    echo "JDK 17: $JAVA_HOME_17"
fi
echo ""

# 执行构建
echo ">>> 开始构建..."
echo ""
./gradlew build --stacktrace 2>&1 | tee /tmp/gradle-build.log
BUILD_EXIT=${PIPESTATUS[0]}

echo ""
echo "========================================"

if [ $BUILD_EXIT -eq 0 ]; then
    echo "  ✅ 构建成功"
    echo ""
    # 从 gradle.properties 读取版本号，避免脚本与版本号不同步
    PLUGIN_VERSION=$(grep '^pluginVersion' gradle.properties | cut -d'=' -f2 | tr -d ' ')
    echo "  版本号: $PLUGIN_VERSION"
    echo "  插件包: $PROJECT_DIR/build/distributions/svn-deploy-plugin-${PLUGIN_VERSION}.zip"
    echo ""
    echo "  安装方式:"
    echo "    1. 打开 IDEA → Settings → Plugins"
    echo "    2. 卸载旧版 SVN Deploy（如已安装）→ 重启"
    echo "    3. ⚙️ → Install Plugin from Disk → 选择上面的 zip"
    echo "    4. 重启 IDEA"
else
    echo "  ❌ 构建失败"
    echo ""
    echo "  完整日志: /tmp/gradle-build.log"
    echo "  查看错误: tail -100 /tmp/gradle-build.log"
fi

echo "========================================"
exit $BUILD_EXIT
