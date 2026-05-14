@echo off
echo Deleting all .class files under src...
del /s /q src\*.class 2>nul
echo Done.
pause