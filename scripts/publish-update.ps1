param([switch]$Publish)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$tools = 'C:\Users\Pawel\Downloads\chaos-android-build'
$privatePath = Join-Path $tools 'plan-update-private.pk8'
$publicPath = Join-Path $root 'android/update-public.der'
$rsa = [Security.Cryptography.RSA]::Create(2048)
try {
    if (Test-Path -LiteralPath $privatePath) {
        $keyBytes = [IO.File]::ReadAllBytes($privatePath)
        $bytesRead = 0
        $rsa.ImportPkcs8PrivateKey($keyBytes,[ref]$bytesRead)
    } else {
        if (Test-Path -LiteralPath $publicPath) { throw 'The private update key is missing; do not replace the existing public key.' }
        [IO.File]::WriteAllBytes($privatePath,$rsa.ExportPkcs8PrivateKey())
        [IO.File]::WriteAllBytes($publicPath,$rsa.ExportSubjectPublicKeyInfo())
    }
    $expectedPublic = [Convert]::ToBase64String([IO.File]::ReadAllBytes($publicPath))
    if ($expectedPublic -ne [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())) { throw 'Update signing key does not match the installed app.' }
    $updateFolder = Join-Path $root 'updates'
    New-Item -ItemType Directory -Path $updateFolder -Force | Out-Null
    $manifestPath = Join-Path $updateFolder 'latest.properties'
    $version = 1L
    if (Test-Path -LiteralPath $manifestPath) {
        $previous = Get-Content -LiteralPath $manifestPath -Raw
        if ($previous -match '(?m)^version=(\d+)') { $version = [long]$Matches[1] + 1 }
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $temporary = Join-Path $updateFolder (([Guid]::NewGuid().ToString('N')) + '.tmp')
    $archive = [IO.Compression.ZipFile]::Open($temporary,[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($name in @('index.html','style.css','app.js')) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $root "dist/$name"),$name,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $archive.Dispose() }
    $sha = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant()
    $payload = [Text.Encoding]::UTF8.GetBytes("$version`n$sha")
    $signature = [Convert]::ToBase64String($rsa.SignData($payload,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1))
    $bundle = Join-Path $updateFolder "$sha.zip"
    if (Test-Path -LiteralPath $bundle) { Remove-Item -LiteralPath $temporary } else { Move-Item -LiteralPath $temporary -Destination $bundle }
    $manifest = "version=$version`nsha256=$sha`nsignature=$signature`n"
    [IO.File]::WriteAllText($manifestPath,$manifest,[Text.UTF8Encoding]::new($false))
    Write-Output "Prepared signed web update $version"
    if ($Publish) {
        git -C $root add -- dist updates android/src android/tests android/AndroidManifest.xml android/build.ps1 android/SignApk.java android/update-public.der scripts .gitignore README.md
        if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
        git -C $root commit -m "Publish web update $version"
        if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
        git -C $root push origin main
        if ($LASTEXITCODE -ne 0) { throw 'git push failed' }
    }
} finally { $rsa.Dispose() }
