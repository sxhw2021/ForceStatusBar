#!/bin/bash

echo "=========================================="
echo "  发布 ForceStatusBar v1.0.0"
echo "=========================================="
echo ""

cd "$(dirname "$0")"

# 检查 Git 状态
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
git commit -m "Release v1.0.0: 正式版发布"

# 推送到 main
echo ""
echo "3. 推送到 main 分支..."
git push origin main

# 创建 tag
echo ""
echo "4. 创建标签 v1.0.0..."
git tag -a v1.0.0 -m "Release version 1.0.0

强制显示状态栏 - 正式版

功能：
- 在选定的应用中强制显示状态栏
- Hook Window 的 setFlags/addFlags/clearFlags 方法
- Hook Activity.onResume/onCreate 主动恢复状态栏
- 支持 Android 8.0+ 和 LSPosed 框架

使用方法：
1. 在 LSPosed 中启用模块
2. 选择目标应用
3. 重启目标应用即可生效"

# 推送 tag
echo ""
echo "5. 推送标签到 GitHub..."
git push origin v1.0.0

echo ""
echo "=========================================="
echo "  完成！"
echo "=========================================="
echo ""
echo "GitHub Actions 将自动构建 Release 版本。"
echo "请等待几分钟后访问："
echo "https://github.com/sxhw2021/ForceStatusBar/releases"
echo ""
echo "或者查看构建进度："
echo "https://github.com/sxhw2021/ForceStatusBar/actions"
