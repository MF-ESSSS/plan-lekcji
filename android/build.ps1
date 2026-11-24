$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$tools = 'C:\Users\Pawel\Downloads\chaos-android-build'
$javaHome = 'C:\Program Files\Java\jdk-25.0.5.0.2'
$buildRoot = Join-Path $projectRoot 'build'
$classes = Join-Path $buildRoot 'classes'
$assets = Join-Path $buildRoot 'assets'
$res = Join-Path $buildRoot 'res'
New-Item -ItemType Directory -Force -Path $classes,$assets,(Join-Path $res 'drawable'),(Join-Path $buildRoot 'dex') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot '../dist/index.html'),(Join-Path $projectRoot '../dist/style.css'),(Join-Path $projectRoot '../dist/app.js') -Destination $assets
Copy-Item -LiteralPath (Join-Path $projectRoot 'update-public.der') -Destination $assets
$stylePath = Join-Path $assets 'style.css'
$style = Get-Content -LiteralPath $stylePath -Raw
$style = $style -replace "@import url\('[^']+'\);", ''
[IO.File]::WriteAllText($stylePath, $style, [Text.UTF8Encoding]::new($false))
Add-Type -AssemblyName System.Drawing
$icon = [Drawing.Bitmap]::new(192,192)
$graphics = [Drawing.Graphics]::FromImage($icon)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([Drawing.Color]::FromArgb(15,8,28))
$brush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(182,139,245))
$graphics.FillRectangle($brush,30,37,132,126)
$pen = [Drawing.Pen]::new([Drawing.Color]::FromArgb(35,17,55),10)
$pen.StartCap = [Drawing.Drawing2D.LineCap]::Round
$pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
$graphics.DrawLine($pen,46,76,146,76)
$graphics.DrawLine($pen,65,28,65,49); $graphics.DrawLine($pen,127,28,127,49); foreach ($planX in @(62,96,130)) { foreach ($planY in @(101,133)) { $graphics.FillRectangle($pen.Brush,$planX-5,$planY-5,10,10) } }
$icon.Save((Join-Path $res 'drawable/icon.png'),[Drawing.Imaging.ImageFormat]::Png)
$pen.Dispose(); $brush.Dispose(); $graphics.Dispose(); $icon.Dispose()
function Run-Native([string]$executable,[string[]]$arguments) {
    & $executable @arguments
    if ($LASTEXITCODE -ne 0) { throw "$executable failed: $LASTEXITCODE" }
}
Run-Native (Join-Path $tools 'aapt2/aapt2.exe') @('compile','--dir',$res,'-o',(Join-Path $buildRoot 'resources.zip'))
Run-Native (Join-Path $tools 'aapt2/aapt2.exe') @('link','-o',(Join-Path $buildRoot 'base.apk'),'--manifest',(Join-Path $projectRoot 'AndroidManifest.xml'),'-I',(Join-Path $tools 'android.jar'),'-A',$assets,(Join-Path $buildRoot 'resources.zip'))
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Run-Native (Join-Path $javaHome 'bin/javac.exe') (@('--release','8','-classpath',(Join-Path $tools 'android.jar'),'-d',$classes) + $sources)
$classFiles = @(Get-ChildItem -LiteralPath (Join-Path $classes 'com') -Recurse -Filter '*.class' | ForEach-Object { $_.FullName })
Run-Native (Join-Path $javaHome 'bin/java.exe') (@('-cp',(Join-Path $tools 'r8.jar'),'com.android.tools.r8.D8','--min-api','23','--lib',(Join-Path $tools 'android.jar'),'--output',(Join-Path $buildRoot 'dex')) + $classFiles)
Copy-Item -LiteralPath (Join-Path $buildRoot 'base.apk') -Destination (Join-Path $buildRoot 'unsigned.apk') -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open((Join-Path $buildRoot 'unsigned.apk'),[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $buildRoot 'dex/classes.dex'),'classes.dex') | Out-Null } finally { $archive.Dispose() }
$passwordPath = Join-Path $tools 'signing-password.txt'
if (!(Test-Path -LiteralPath $passwordPath)) { [IO.File]::WriteAllText($passwordPath,[Guid]::NewGuid().ToString('N')) }
$env:CHAOS_SIGN_PASSWORD = [IO.File]::ReadAllText($passwordPath)
$keyPath = Join-Path $tools 'plan-lekcji-signing.p12'
try {
    if (!(Test-Path -LiteralPath $keyPath)) {
        Run-Native (Join-Path $javaHome 'bin/keytool.exe') @('-genkeypair','-keystore',$keyPath,'-storetype','PKCS12','-alias','chaos','-storepass:env','CHAOS_SIGN_PASSWORD','-keypass:env','CHAOS_SIGN_PASSWORD','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Plan Lekcji')
    }
    Run-Native (Join-Path $javaHome 'bin/javac.exe') @('-classpath',(Join-Path $tools 'apksig.jar'),'-d',$classes,(Join-Path $projectRoot 'SignApk.java'))
    Run-Native (Join-Path $javaHome 'bin/java.exe') @('-cp',"$classes;$(Join-Path $tools 'apksig.jar')",'SignApk',$keyPath,(Join-Path $buildRoot 'unsigned.apk'),(Join-Path $projectRoot 'Plan-lekcji.apk'))
} finally { Remove-Item Env:CHAOS_SIGN_PASSWORD -ErrorAction SilentlyContinue }
Write-Output (Join-Path $projectRoot 'Plan-lekcji.apk')
