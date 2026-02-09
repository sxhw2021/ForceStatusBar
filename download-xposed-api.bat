@echo off
chcp 65001 >nul

echo ========================================
echo   下载 Xposed API
echo ========================================
echo.

set JAR_URL=https://github.com/rovo89/XposedBridge/releases/download/v82/api-82.jar
set LIBS_DIR=app\libs
set JAR_FILE=%LIBS_DIR%\api-82.jar

if not exist "%LIBS_DIR%" mkdir "%LIBS_DIR%"

if exist "%JAR_FILE%" (
    echo ✓ api-82.jar 已存在
    echo   位置: %JAR_FILE%
    for %%I in ("%JAR_FILE%") do echo   大小: %%~zI 字节
    exit /b 0
)

echo 正在下载 Xposed API...
echo URL: %JAR_URL%

where curl >nul 2>nul
if %errorlevel% == 0 (
    curl -L -o "%JAR_FILE%" "%JAR_URL%"
    goto :check
)

where wget >nul 2>nul
if %errorlevel% == 0 (
    wget -O "%JAR_FILE%" "%JAR_URL%"
    goto :check
)

echo ✗ 错误：需要 curl 或 wget 来下载文件
echo.
echo 请安装 curl 或 wget，或手动下载：
echo   1. 访问: https://github.com/rovo89/XposedBridge/releases/tag/v82
echo   2. 下载 api-82.jar
echo   3. 将文件放入 %LIBS_DIR% 目录
exit /b 1

:check
if exist "%JAR_FILE%" (
    echo.
    echo ✓ 下载成功: %JAR_FILE%
    for %%I in ("%JAR_FILE%") do echo   大小: %%~zI 字节
) else (
    echo.
    echo ✗ 下载失败
    echo.
    echo 请手动下载：
    echo   1. 访问: https://github.com/rovo89/XposedBridge/releases/tag/v82
echo   2. 下载 api-82.jar
echo   3. 将文件放入 %LIBS_DIR% 目录
    exit /b 1
)

echo.
echo ========================================
echo   完成！
echo ========================================
