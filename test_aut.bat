@echo off
setlocal enabledelayedexpansion

if "%1"=="" (
    echo USING: %~nx0 ^<email^> ^<password^>
    echo EXAMPLE: %~nx0 test@example.com mypassword123
    pause
    exit /b 1
)

set EMAIL=%1
set PASSWORD=%2
set DEVICE_INFO=WindowsBatchScript

echo ===AUTHORIZATION===
echo Email: %EMAIL%
echo Password: [hidden]
echo Device: %DEVICE_INFO%
echo.

:: STEP 1: LOGIN
echo [1/4] GET REFRESH-TOKEN...

:: Create Unique tmp-file
set TEMP_FILE=%TEMP%\login_%RANDOM%.json

curl -s -X POST http://localhost/api/momentum/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"%EMAIL%\",\"phone\":null,\"password\":\"%PASSWORD%\",\"deviceInfo\":\"%DEVICE_INFO%\"}" > %TEMP_FILE%

if %errorlevel% neq 0 (
    echo ERROR: connection to server
    del %TEMP_FILE% 2>nul
    pause
    exit /b 1
)

:: PowerShell for JSON-parsing
for /f "usebackq delims=" %%i in (`powershell -Command "$json = Get-Content '%TEMP_FILE%' -Raw | ConvertFrom-Json; if ($json.jwt) { Write-Output $json.jwt } else { Write-Output '' }"`) do (
    set REFRESH_TOKEN=%%i
)

:: CHECK: GET TOKEN
if "!REFRESH_TOKEN!"=="" (
    echo ERROR: Token didn't get
    echo SERVER RESPONSE:
    type %TEMP_FILE%
    del %TEMP_FILE% 2>nul
    pause
    exit /b 1
)

echo REFRESH TOKEN RECEIVED
echo !REFRESH_TOKEN!
echo.

:: STEP 2: GET ACCESS TOKEN
echo [2/4] Get access token...

set AUTH_TEMP=%TEMP%\auth_%RANDOM%.json

curl -s -X POST http://localhost/api/momentum/auth ^
  -H "Content-Type: application/json" ^
  -d "{\"token\":\"!REFRESH_TOKEN!\"}" > %AUTH_TEMP%

:: Extract access token using PowerShell
for /f "usebackq delims=" %%i in (`powershell -Command "$json = Get-Content '%AUTH_TEMP%' -Raw | ConvertFrom-Json; if ($json.token) { Write-Output $json.token } else { Write-Output '' }"`) do (
    set ACCESS_TOKEN=%%i
)

if "!ACCESS_TOKEN!"=="" (
    echo ERROR: Extract access token
    echo SERVER RESPONSE:
    type %AUTH_TEMP%
    del %TEMP_FILE% %AUTH_TEMP% 2>nul
    pause
    exit /b 1
)

echo ACCESS TOKEN RECEIVED
echo !ACCESS_TOKEN!
echo.

:: STEP 3: CHECK AUTHORIZE
echo [3/4] CHECK /hello2...

set HELLO_TEMP=%TEMP%\hello_%RANDOM%.txt

curl -s http://localhost/api/momentum/hello2 ^
  -H "Authorization: Bearer !ACCESS_TOKEN!" > %HELLO_TEMP%

echo Result:
type %HELLO_TEMP%
echo.
echo.

:: PRINT TOKEN INFO (first 50 chars)
echo TOKEN INFO:
echo Access token (first 50 chars): !ACCESS_TOKEN:~0,50!...
echo.

:: Del tmp
del %TEMP_FILE% %AUTH_TEMP% %HELLO_TEMP% 2>nul

echo ========================================
echo SUCCESS AUTHORIZE
echo ========================================
echo.
echo AVAILABLE COMMANDS:
echo ----------------------------------------
echo curl -X GET http://localhost/api/momentum/friends -H "Authorization: Bearer !ACCESS_TOKEN!"
echo.
echo curl -X POST http://localhost/api/momentum/friends/request/by-email -H "Content-Type: application/json" -H "Authorization: Bearer !ACCESS_TOKEN!" -d "{\"email\":\"friend@example.com\"}"
echo.
echo curl -X GET http://localhost/api/momentum/friends/requests/incoming -H "Authorization: Bearer !ACCESS_TOKEN!"
echo.
echo curl -X POST http://localhost/api/momentum/logout -H "Content-Type: application/json" -d "{\"refreshToken\":\"!REFRESH_TOKEN!\"}"
echo.
echo curl -X GET http://localhost/api/momentum/friends/requests/outgoing -H "Authorization: Bearer !ACCESS_TOKEN!"
echo.
echo curl -X DELETE http://localhost/api/momentum/friends/request/REQUEST_ID -H "Authorization: Bearer !ACCESS_TOKEN!"
echo.
echo curl -X PATCH http://localhost/api/momentum/friends/request/REQUEST_ID/accept -H "Authorization: Bearer !ACCESS_TOKEN!"
echo.
echo curl -X DELETE http://localhost/api/momentum/friends/FRIEND_ID -H "Authorization: Bearer !ACCESS_TOKEN!"
echo.
echo curl -X PATCH http://localhost/api/momentum/friends/request/REQUEST_ID/reject -H "Authorization: Bearer !ACCESS_TOKEN!"
echo.