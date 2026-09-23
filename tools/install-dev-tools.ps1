$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$toolsRoot = 'D:\DevTools'
$downloadsDirectory = Join-Path $toolsRoot 'downloads'
$existingJavaHome = 'D:\AI-Toolchain\jdk-17.0.20.1+1'
$managedJavaHome = Join-Path $toolsRoot 'jdk-17'
$javaHome = if (Test-Path -LiteralPath (Join-Path $existingJavaHome 'bin\javac.exe')) {
    $existingJavaHome
} else {
    $managedJavaHome
}
$mavenVersion = '3.9.16'
$mavenHome = Join-Path $toolsRoot "apache-maven-$mavenVersion"
$dotnetHome = Join-Path $toolsRoot 'dotnet'

function Write-Step([string]$message) {
    Write-Host "`n==> $message" -ForegroundColor Cyan
}

function Add-UserPathEntries([string[]]$desiredEntries) {
    $existingPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $existingEntries = @($existingPath -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $result = New-Object System.Collections.Generic.List[string]

    foreach ($entry in @($desiredEntries) + $existingEntries) {
        $normalizedEntry = $entry.TrimEnd('\')
        $alreadyAdded = $result | Where-Object {
            $_.TrimEnd('\').Equals($normalizedEntry, [StringComparison]::OrdinalIgnoreCase)
        }

        if (-not $alreadyAdded) {
            $result.Add($entry)
        }
    }

    [Environment]::SetEnvironmentVariable('Path', ($result -join ';'), 'User')
}

New-Item -ItemType Directory -Path $downloadsDirectory -Force | Out-Null

Write-Step 'Installing Eclipse Temurin JDK 17'
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
    $adoptiumApi = 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&project=jdk&vendor=eclipse'
    $release = @(Invoke-RestMethod -Uri $adoptiumApi)[0]
    $javaPackage = $release.binary.package
    $javaArchive = Join-Path $downloadsDirectory $javaPackage.name

    Invoke-WebRequest -Uri $javaPackage.link -OutFile $javaArchive -UseBasicParsing
    $actualJavaHash = (Get-FileHash -LiteralPath $javaArchive -Algorithm SHA256).Hash
    if (-not $actualJavaHash.Equals($javaPackage.checksum, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'JDK SHA-256 verification failed. Installation stopped.'
    }

    $javaStaging = Join-Path $toolsRoot ("staging-jdk-" + [Guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $javaArchive -DestinationPath $javaStaging
    $extractedJavaDirectory = Get-ChildItem -LiteralPath $javaStaging -Directory | Select-Object -First 1
    if ($null -eq $extractedJavaDirectory) {
        throw 'The expected JDK directory was not found in the archive.'
    }
    Move-Item -LiteralPath $extractedJavaDirectory.FullName -Destination $managedJavaHome
    $javaHome = $managedJavaHome
    Remove-Item -LiteralPath $javaStaging -Recurse -Force
} else {
    Write-Host 'JDK 17 already exists; download skipped.'
}

Write-Step "Installing Apache Maven $mavenVersion"
if (-not (Test-Path -LiteralPath (Join-Path $mavenHome 'bin\mvn.cmd'))) {
    $mavenFileName = "apache-maven-$mavenVersion-bin.zip"
    $mavenBaseUrl = "https://dlcdn.apache.org/maven/maven-3/$mavenVersion/binaries"
    $mavenArchive = Join-Path $downloadsDirectory $mavenFileName
    $mavenChecksumFile = "$mavenArchive.sha512"

    Invoke-WebRequest -Uri "$mavenBaseUrl/$mavenFileName" -OutFile $mavenArchive -UseBasicParsing
    Invoke-WebRequest -Uri "$mavenBaseUrl/$mavenFileName.sha512" -OutFile $mavenChecksumFile -UseBasicParsing

    $expectedMavenHash = ((Get-Content -Raw -LiteralPath $mavenChecksumFile).Trim() -split '\s+')[0]
    $actualMavenHash = (Get-FileHash -LiteralPath $mavenArchive -Algorithm SHA512).Hash
    if (-not $actualMavenHash.Equals($expectedMavenHash, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Maven SHA-512 verification failed. Installation stopped.'
    }

    Expand-Archive -LiteralPath $mavenArchive -DestinationPath $toolsRoot
} else {
    Write-Host 'Maven already exists; download skipped.'
}

Write-Step 'Installing .NET 10 SDK'
if (-not (Test-Path -LiteralPath (Join-Path $dotnetHome 'sdk'))) {
    $dotnetInstallScript = Join-Path $downloadsDirectory 'dotnet-install.ps1'
    Invoke-WebRequest -Uri 'https://dot.net/v1/dotnet-install.ps1' -OutFile $dotnetInstallScript -UseBasicParsing
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $dotnetInstallScript `
        -Channel '10.0' `
        -Quality 'GA' `
        -InstallDir $dotnetHome `
        -NoPath
    if ($LASTEXITCODE -ne 0) {
        throw '.NET 10 SDK installation failed.'
    }
} else {
    Write-Host '.NET SDK already exists; download skipped.'
}

Write-Step 'Configuring user environment variables'
[Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'User')
[Environment]::SetEnvironmentVariable('MAVEN_HOME', $mavenHome, 'User')
[Environment]::SetEnvironmentVariable('DOTNET_ROOT', $dotnetHome, 'User')

$pathEntries = @(
    (Join-Path $javaHome 'bin'),
    (Join-Path $mavenHome 'bin'),
    $dotnetHome
)
Add-UserPathEntries $pathEntries
$env:JAVA_HOME = $javaHome
$env:MAVEN_HOME = $mavenHome
$env:DOTNET_ROOT = $dotnetHome
$env:Path = ($pathEntries -join ';') + ';' + $env:Path

Write-Step 'Verifying installations'
& (Join-Path $javaHome 'bin\java.exe') -version
& (Join-Path $mavenHome 'bin\mvn.cmd') -version
& (Join-Path $dotnetHome 'dotnet.exe') --info

Write-Host "`nDevelopment tools were installed in $toolsRoot" -ForegroundColor Green
Write-Host 'New terminal and IDE processes will read the updated environment variables.'
