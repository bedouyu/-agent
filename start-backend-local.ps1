$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDirectory = Join-Path $projectDirectory 'backend'
$javaHome = 'D:\AI-Toolchain\jdk-17.0.20.1+1'
$mavenHome = 'D:\DevTools\apache-maven-3.9.16'

$env:JAVA_HOME = $javaHome
$env:MAVEN_HOME = $mavenHome
$env:Path = "$javaHome\bin;$mavenHome\bin;$env:Path"
$env:APP_DATA_DIR = Join-Path $projectDirectory 'data'

$existingBackend = $null
try {
    $existingBackend = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/health' -TimeoutSec 2
} catch {
    $existingBackend = $null
}
if ($existingBackend.status -eq 'UP') {
    Write-Host 'The local backend is already running on http://127.0.0.1:18080' -ForegroundColor Green
    exit 0
}

Set-Location -LiteralPath $backendDirectory
Write-Host 'Starting local Java backend with H2...' -ForegroundColor Green
Write-Host 'Health check: http://127.0.0.1:18080/api/health'
& (Join-Path $mavenHome 'bin\mvn.cmd') spring-boot:run
