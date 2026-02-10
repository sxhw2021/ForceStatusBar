#!/bin/bash

# GitHub Actions 构建配置指南

echo "=========================================="
echo "  GitHub Actions 构建配置"
echo "=========================================="
echo ""

# 检查目录结构
echo "✓ 检查项目结构..."
if [ ! -f "build.gradle" ]; then
    echo "✗ 错误：未找到 build.gradle"
    exit 1
fi

if [ ! -f "app/build.gradle" ]; then
    echo "✗ 错误：未找到 app/build.gradle"
    exit 1
fi

if [ ! -f ".github/workflows/build.yml" ]; then
    echo "✗ 错误：未找到 GitHub Actions 工作流"
    exit 1
fi

echo "✓ 项目结构检查通过"
echo ""

# 创建 Gradle Wrapper（如果不存在）
if [ ! -f "gradlew" ]; then
    echo "⚠ 未找到 Gradle Wrapper，正在创建..."
    
    # 创建基本的 gradlew 脚本
    cat > gradlew << 'EOF'
#!/bin/sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" -a "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ $? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

# For Darwin, add options to specify how the application appears in the dock
if [ "$darwin" = "true" ]; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
fi

# For Cygwin or MSYS, switch paths to Windows format before running java
if [ "$cygwin" = "true" -o "$msys" = "true" ] ; then
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    JAVACMD=`cygpath --unix "$JAVACMD"`
fi

exec "$JAVACMD" ${DEFAULT_JVM_OPTS} ${JAVA_OPTS} ${GRADLE_OPTS} -classpath "${CLASSPATH}" org.gradle.wrapper.GradleWrapperMain "$@"
EOF

    chmod +x gradlew
    echo "✓ 已创建 gradlew"
fi

# 创建 gradle wrapper 目录和文件
if [ ! -d "gradle/wrapper" ]; then
    mkdir -p gradle/wrapper
fi

if [ ! -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.0-bin.zip
networkTimeout=10000
timeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
    echo "✓ 已创建 gradle-wrapper.properties"
fi

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "⚠ 正在下载 gradle-wrapper.jar..."
    curl -L -o gradle/wrapper/gradle-wrapper.jar \
        https://raw.githubusercontent.com/gradle/gradle/v8.0.0/gradle/wrapper/gradle-wrapper.jar
    echo "✓ 已下载 gradle-wrapper.jar"
fi

echo ""
echo "=========================================="
echo "  配置完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo ""
echo "1. 创建 GitHub 仓库"
echo "   - 访问 https://github.com/new"
echo "   - 创建新仓库"
echo ""
echo "2. 推送代码到 GitHub"
echo "   git init"
echo "   git add ."
echo "   git commit -m \"Initial commit\""
echo "   git branch -M main"
echo "   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git"
echo "   git push -u origin main"
echo ""
echo "3. 配置签名密钥（用于发布版本）"
echo "   访问仓库 Settings -> Secrets and variables -> Actions"
echo "   添加以下 secrets："
echo "   - SIGNING_KEY: Base64 编码的 keystore 文件"
echo "   - ALIAS: 密钥别名"
echo "   - KEY_STORE_PASSWORD: Keystore 密码"
echo "   - KEY_PASSWORD: 密钥密码"
echo ""
echo "4. 创建 Release"
echo "   git tag -a v1.0.0 -m \"Release version 1.0.0\""
echo "   git push origin v1.0.0"
echo ""
echo "自动构建将在推送 tag 后触发！"
echo ""
