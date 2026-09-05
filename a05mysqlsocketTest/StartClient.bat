@echo off
chcp 65001 >nul
title 聊天室客户端
cd /d "%~dp0"

rem 检查 ChatRoom.jar 是否在本文件夹
if not exist "ChatRoom.jar" (
    echo [错误] 当前文件夹找不到 ChatRoom.jar
    echo 请把 StartClient.bat 和 ChatRoom.jar 放在同一个文件夹里，再双击本文件。
    echo.
    pause
    exit /b 1
)

rem 检查是否安装了 Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Java 环境，请先安装 JDK 8 或更高版本。
    echo 下载地址：https://www.oracle.com/java/technologies/downloads/
    echo.
    pause
    exit /b 1
)

echo 正在启动聊天室客户端，请稍候...
java -cp ChatRoom.jar org.example.LoginUI

echo.
echo 客户端已关闭。
pause
