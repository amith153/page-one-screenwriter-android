@echo off
REM Builds a signed, shrunk APK you can send to anyone (Android 8.0+).
REM Output: PageOne-share.apk in this folder.
setlocal
if "%JAVA_HOME%"=="" if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d "%~dp0"
call gradlew.bat assembleRelease
set BUILD_RESULT=%errorlevel%
REM Stop the Gradle background process so it doesn't keep ~2 GB of RAM busy.
call gradlew.bat --stop >nul 2>&1
if not "%BUILD_RESULT%"=="0" (
  echo.
  echo Build failed - scroll up for the first error.
  pause
  exit /b 1
)
copy /Y "app\build\outputs\apk\release\app-release.apk" "PageOne-share.apk" >nul
echo.
echo Done: %~dp0PageOne-share.apk
explorer /select,"%~dp0PageOne-share.apk"
pause
