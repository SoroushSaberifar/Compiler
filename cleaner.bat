@echo off
cls
if exist src\*.class del /s /q src\*.class >nul 2>&1
if exist src\grammar\*.class del /s /q src\grammar\*.class >nul 2>&1