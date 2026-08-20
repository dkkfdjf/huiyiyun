@echo off
chcp 65001 >nul
REM ============================================================
REM  慧医云 Redis(Windows 移植版)开机自启服务 —— 一键安装脚本
REM  给队友用:把本脚本放到 redis-server.exe 所在目录,
REM  右键"以管理员身份运行"即可(需管理员权限装服务)。
REM ============================================================
cd /d "%~dp0"

echo [1/4] 检查 redis-server.exe ...
if not exist "redis-server.exe" (
  echo [X] 当前目录找不到 redis-server.exe,请把本脚本放到 Redis 解压目录后再运行。
  pause & exit /b 1
)

echo [2/4] 安装 Redis 服务(服务名 Redis)...
redis-server.exe --service-install redis.windows-service.conf --service-name Redis
if errorlevel 1 ( echo [X] 安装失败:可能未用管理员身份运行,或服务已存在。 & pause & exit /b 1 )

echo [3/4] 设为开机自动启动 ...
sc config Redis start= auto

echo [4/4] 启动服务 ...
sc start Redis

echo.
echo --- 当前服务状态 ---
sc query Redis
echo.
echo 完成。Redis 已装成开机自启服务(端口 6379),以后开机自动起,不用手动开。
echo 卸载:redis-server.exe --service-uninstall
pause
