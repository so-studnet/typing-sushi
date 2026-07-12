@echo off
setlocal

cd /d "%~dp0backend"

if not defined JAVA_HOME (
    echo JAVA_HOME is not set.
    echo.
    echo Set it once to your JDK folder, for example:
    echo   setx JAVA_HOME "C:\Users\254002\Desktop\jdk-21.0.11"
    echo Then close and reopen this terminal ^(or double-click run.bat again^) and try once more.
    pause
    exit /b 1
)

if not exist out mkdir out

"%JAVA_HOME%\bin\javac.exe" -d out src\main\java\com\typingsushi\*.java
if errorlevel 1 (
    echo.
    echo Build failed. See the errors above.
    pause
    exit /b 1
)

"%JAVA_HOME%\bin\java.exe" -cp out com.typingsushi.Main
pause
