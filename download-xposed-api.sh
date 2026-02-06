#!/bin/bash

# 下载 Xposed API JAR 文件
# 用于本地开发

set -e

JAR_URL="https://github.com/rovo89/XposedBridge/releases/download/v82/api-82.jar"
LIBS_DIR="app/libs"
JAR_FILE="$LIBS_DIR/api-82.jar"

echo "========================================"
echo "  下载 Xposed API"
echo "========================================"
echo ""

# 创建目录
mkdir -p "$LIBS_DIR"

# 检查是否已存在
if [ -f "$JAR_FILE" ]; then
    echo "✓ api-82.jar 已存在"
    echo "  位置: $JAR_FILE"
    echo "  大小: $(stat -f%z "$JAR_FILE" 2>/dev/null || stat -c%s "$JAR_FILE" 2>/dev/null) 字节"
    exit 0
fi

# 下载文件
echo "正在下载 Xposed API..."
echo "URL: $JAR_URL"

if command -v curl &> /dev/null; then
    curl -L -o "$JAR_FILE" "$JAR_URL"
elif command -v wget &> /dev/null; then
    wget -O "$JAR_FILE" "$JAR_URL"
else
    echo "✗ 错误：需要 curl 或 wget 来下载文件"
    exit 1
fi

# 验证文件
if [ -f "$JAR_FILE" ]; then
    FILE_SIZE=$(stat -f%z "$JAR_FILE" 2>/dev/null || stat -c%s "$JAR_FILE" 2>/dev/null || echo "未知")
    echo ""
    echo "✓ 下载成功: $JAR_FILE"
    echo "  大小: $FILE_SIZE 字节"
else
    echo ""
    echo "✗ 下载失败"
    echo ""
    echo "请手动下载:"
    echo "  1. 访问: https://github.com/rovo89/XposedBridge/releases/tag/v82"
    echo "  2. 下载 api-82.jar"
    echo "  3. 将文件放入 $LIBS_DIR 目录"
    exit 1
fi

echo ""
echo "========================================"
echo "  完成！"
echo "========================================"
