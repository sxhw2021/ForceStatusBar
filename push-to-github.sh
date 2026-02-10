#!/bin/bash

echo "=========================================="
echo "  推送代码到 GitHub"
echo "=========================================="
echo ""

cd "$(dirname "$0")"

# 检查 git 状态
echo "检查 Git 状态..."
git status

echo ""
echo "=========================================="

# 添加所有更改
echo ""
echo "1. 添加所有更改..."
git add .

# 提交
echo ""
echo "2. 提交更改..."
git commit -m "Fix: 使用 Xposed Maven 仓库自动下载依赖，移除手动下载"

# 推送
echo ""
echo "3. 推送到 GitHub..."
git push origin main

echo ""
echo "=========================================="
echo "  完成！"
echo "=========================================="
echo ""
echo "GitHub Actions 将在几分钟内自动构建。"
echo "请访问 https://github.com/sxhw2021/ForceStatusBar/actions 查看进度"
