@echo off
setlocal
cd /d "%~dp0"

if "%JWT_SECRET%"=="" (
  echo ERROR: JWT_SECRET is required before starting the server.
  echo.
  echo Example for cmd.exe:
  echo   set JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
  echo   server-start.bat
  echo.
  echo Example for PowerShell:
  echo   $env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"
  echo   .\server-start.bat
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$secret = [Environment]::GetEnvironmentVariable('JWT_SECRET');" ^
  "if ([Text.Encoding]::UTF8.GetByteCount($secret) -lt 32) { Write-Host 'ERROR: JWT_SECRET must be at least 32 bytes long when encoded as UTF-8.'; exit 1 }" ^
  "$port = 8080;" ^
  "$root = (Resolve-Path .).Path;" ^
  "$health = $null;" ^
  "try { $health = Invoke-RestMethod -Uri ('http://localhost:' + $port + '/api/health') -TimeoutSec 2 } catch {}" ^
  "if ($health -and $health.status -eq 'ok') { Write-Host ('Server is already running at http://localhost:' + $port + ' (PID ' + $health.pid + ')'); exit 0 }" ^
  "if (-not (Test-Path '.\build\libs')) { New-Item -ItemType Directory -Path '.\build\libs' -Force | Out-Null }" ^
  "$jar = Get-ChildItem '.\build\libs' -Filter 'auction-server*.jar' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1;" ^
  "if (-not $jar) { Write-Host 'Server JAR not found. Building auction-server JAR...'; .\gradlew.bat shadowJar; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; $jar = Get-ChildItem '.\build\libs' -Filter 'auction-server*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 }" ^
  "New-Item -ItemType Directory -Path '.\data' -Force | Out-Null;" ^
  "New-Item -ItemType Directory -Path '.\logs' -Force | Out-Null;" ^
  "$out = Join-Path $root 'logs\server.out.log';" ^
  "$err = Join-Path $root 'logs\server.err.log';" ^
  "$proc = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar.FullName) -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru;" ^
  "Set-Content -Path '.\data\launcher.pid' -Value $proc.Id -Encoding ASCII;" ^
  "Write-Host ('Starting server at http://localhost:' + $port + ' (launcher PID ' + $proc.Id + ')');" ^
  "$deadline = (Get-Date).AddSeconds(45);" ^
  "do { Start-Sleep -Milliseconds 500; try { $health = Invoke-RestMethod -Uri ('http://localhost:' + $port + '/api/health') -TimeoutSec 2 } catch { $health = $null } } while ((-not $health -or $health.status -ne 'ok') -and (Get-Date) -lt $deadline);" ^
  "if ($health -and $health.status -eq 'ok') { Write-Host ('Server ready at http://localhost:' + $port + ' (PID ' + $health.pid + ')'); exit 0 }" ^
  "Write-Host 'Server did not become healthy in time. See logs\server.out.log and logs\server.err.log'; exit 1"

exit /b %ERRORLEVEL%
