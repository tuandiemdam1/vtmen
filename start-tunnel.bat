@echo off
chcp 65001 >nul
title VT-Men All-in-One (FE + BE + Ngrok)
cd /d "%~dp0"

:: Tu dong tao .env tu .env.example neu chua co
if not exist ".env" (
    if exist ".env.example" (
        copy /y ".env.example" ".env" >nul
        echo [INFO] Da tu dong tao file .env tu .env.example
    )
)

:: Doc NGROK_DOMAIN va BACKEND_PORT tu .env
set "BE_PORT=8085"
set "FE_PORT=3005"
set "NGROK_DOMAIN=cabalistic-grisly-kolby.ngrok-free.dev"

if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        if /i "%%A"=="BACKEND_PORT" set "BE_PORT=%%B"
        if /i "%%A"=="FRONTEND_PORT" set "FE_PORT=%%B"
        if /i "%%A"=="NGROK_DOMAIN" set "NGROK_DOMAIN=%%B"
    )
)

echo ========================================================
echo   VT-MEN - KHOI DONG TOAN BO HE THONG (1-CLICK LAUNCHER)
echo   - Backend (Spring Boot): http://localhost:%BE_PORT%
echo   - Frontend (Next.js):    http://localhost:%FE_PORT%
if not "%NGROK_DOMAIN%"=="" (
    echo   - Ngrok Tunnel:          https://%NGROK_DOMAIN%
) else (
    echo   - Ngrok Tunnel:          Random Domain
)
echo ========================================================
echo.

:: 1. Dong cac tien trinh ngrok va giai phong port (neu co)
taskkill /f /im ngrok.exe >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%BE_PORT% " ^| findstr "LISTENING"') do taskkill /f /t /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%FE_PORT% " ^| findstr "LISTENING"') do taskkill /f /t /pid %%a >nul 2>&1
timeout /t 1 /nobreak >nul

:: 2. Khoi dong Ngrok Tunnel trong cua so rieng de theo doi traffic
echo [1/2] Dang bat Ngrok Tunnel...
if not "%NGROK_DOMAIN%"=="" (
    start "Ngrok Tunnel VT-Men (Port %BE_PORT%)" ngrok http --url=%NGROK_DOMAIN% --traffic-policy-file ngrok-policy.yml %BE_PORT%
) else (
    start "Ngrok Tunnel VT-Men (Port %BE_PORT%)" ngrok http --traffic-policy-file ngrok-policy.yml %BE_PORT%
)

:: 3. Khoi dong Backend va Frontend qua start.mjs trong cua so nay
echo [2/2] Dang khoi dong Backend va Frontend...
echo.
node start.mjs

:: Tu dong don dep khi dong ung dung
taskkill /f /im ngrok.exe >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%BE_PORT% " ^| findstr "LISTENING"') do taskkill /f /t /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%FE_PORT% " ^| findstr "LISTENING"') do taskkill /f /t /pid %%a >nul 2>&1
pause
