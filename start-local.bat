@echo off
chcp 65001 >nul
title VT-Men Local Launcher (FE + BE)
cd /d "%~dp0"

echo ========================================================
echo   VT-MEN - KHOI DONG HE THONG LOCAL (FE + BE)
echo   - Frontend (Next.js):    http://localhost:3005
echo   - Backend (Spring Boot): http://localhost:8085
echo ========================================================
echo.

node start.mjs
pause

