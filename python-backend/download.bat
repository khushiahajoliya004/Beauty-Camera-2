@echo off
:: ─────────────────────────────────────────────────────────────────────────────
:: Beauty Camera — Model Downloader
:: Redirects ALL HuggingFace cache to D: drive (avoids filling C: with 7+ GB)
:: Run this ONCE before starting the server.
:: ─────────────────────────────────────────────────────────────────────────────

set HF_HOME=D:\HuggingFaceCache
set HUGGINGFACE_HUB_CACHE=D:\HuggingFaceCache\hub
set XDG_CACHE_HOME=D:\HuggingFaceCache

echo.
echo ============================================================
echo   HF cache redirected to: %HF_HOME%
echo ============================================================
echo.

.\venv\Scripts\python.exe download_models.py
pause
