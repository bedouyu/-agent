$ErrorActionPreference = 'Stop'

$projectDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$cacheDirectory = Join-Path $projectDirectory '.cache'
$backendDirectory = Join-Path $projectDirectory 'backend'
$desktopProject = Join-Path $projectDirectory 'desktop\PaperAgent.Desktop\PaperAgent.Desktop.csproj'
$nugetConfig = Join-Path $projectDirectory 'NuGet.Config'

$javaHome = 'D:\AI-Toolchain\jdk-17.0.20.1+1'
$mavenHome = 'D:\DevTools\apache-maven-3.9.16'
$dotnetHome = 'D:\DevTools\dotnet'

$env:JAVA_HOME = $javaHome
$env:MAVEN_HOME = $mavenHome
$env:DOTNET_ROOT = $dotnetHome
$env:DOTNET_CLI_HOME = Join-Path $cacheDirectory 'dotnet-home'
$env:NUGET_PACKAGES = Join-Path $cacheDirectory 'nuget'
$env:APPDATA = Join-Path $cacheDirectory 'appdata'
$env:LOCALAPPDATA = Join-Path $cacheDirectory 'localappdata'
$env:DOTNET_NOLOGO = '1'
$env:DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'
$env:DOTNET_CLI_WORKLOAD_UPDATE_NOTIFY_DISABLE = '1'
$env:Path = "$javaHome\bin;$mavenHome\bin;$dotnetHome;$env:Path"

Write-Host 'Building and testing Java backend with JDK 17...' -ForegroundColor Cyan
Push-Location -LiteralPath $backendDirectory
try {
    & (Join-Path $mavenHome 'bin\mvn.cmd') clean test
    if ($LASTEXITCODE -ne 0) {
        throw 'Java backend build failed.'
    }
} finally {
    Pop-Location
}

Write-Host 'Building WPF desktop client with .NET 10...' -ForegroundColor Cyan
& (Join-Path $dotnetHome 'dotnet.exe') build $desktopProject --nologo --configfile $nugetConfig
if ($LASTEXITCODE -ne 0) {
    throw 'WPF client build failed.'
}

Write-Host 'All builds and tests passed.' -ForegroundColor Green
