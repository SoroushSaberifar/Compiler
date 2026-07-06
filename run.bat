@echo off
cls
if exist src\*.class del /s /q src\*.class >nul 2>&1
if exist src\grammar\*.class del /s /q src\grammar\*.class >nul 2>&1

java -jar lib/antlr-4.13.2-complete.jar -o src/grammar -package grammar src/javaMinusMinus2.g4

javac -cp "lib/antlr-4.13.2-complete.jar;src" src/*.java src/grammar/*.java -d src

java -cp "lib/antlr-4.13.2-complete.jar;src" Main test/test_correct.txt
java -cp "lib/antlr-4.13.2-complete.jar;src" Main test/test_errors.txt