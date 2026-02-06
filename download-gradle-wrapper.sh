#!/bin/bash

# 下载 Gradle Wrapper JAR 文件
# 用于 GitHub Actions 构建

set -e

WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.0.0/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_DIR="gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"

echo "========================================"
echo "  下载 Gradle Wrapper"
echo "========================================"
echo ""

# 创建目录
mkdir -p "$WRAPPER_DIR"

# 检查是否已存在
if [ -f "$WRAPPER_JAR" ]; then
    echo "✓ gradle-wrapper.jar 已存在"
    exit 0
fi

# 下载文件
echo "正在下载 gradle-wrapper.jar..."
echo "URL: $WRAPPER_URL"

if command -v curl &> /dev/null; then
    curl -L -o "$WRAPPER_JAR" "$WRAPPER_URL"
elif command -v wget &> /dev/null; then
    wget -O "$WRAPPER_JAR" "$WRAPPER_URL"
else
    echo "✗ 错误：需要 curl 或 wget 来下载文件"
    exit 1
fi

# 验证文件
if [ -f "$WRAPPER_JAR" ]; then
    FILE_SIZE=$(stat -f%z "$WRAPPER_JAR" 2>/dev/null || stat -c%s "$WRAPPER_JAR" 2>/dev/null || echo "未知")
    echo "✓ 下载成功: $WRAPPER_JAR (大小: $FILE_SIZE 字节)"
else
    echo "✗ 下载失败"
    exit 1
fi

echo ""
echo "========================================"
echo "  完成！"
echo "========================================"
