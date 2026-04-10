@echo off
:: ─────────────────────────────────────────────────────────────────────────────
:: Beauty Camera — AI Backend Server
:: Redirects ALL HuggingFace cache to D: drive (avoids filling C: with 7+ GB)
:: Run AFTER download.bat has completed.
:: ─────────────────────────────────────────────────────────────────────────────

set HF_HOME=D:\HuggingFaceCache
set HUGGINGFACE_HUB_CACHE=D:\HuggingFaceCache\hub
set XDG_CACHE_HOME=D:\HuggingFaceCache

echo.
echo ============================================================
echo   Beauty Camera AI Backend — Starting on http://0.0.0.0:8000
echo   HF cache: %HF_HOME%
echo   Device:   Will auto-detect (CPU if no NVIDIA GPU)
echo ============================================================
echo.

.\venv\Scripts\python.exe main.py > server.log 2>&1
echo.
echo ===== SERVER EXITED - Last 50 lines of log =====
powershell -Command "Get-Content server.log -Tail 50"
pause
