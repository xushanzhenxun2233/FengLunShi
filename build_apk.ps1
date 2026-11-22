$ErrorActionPreference = "Stop"

$ProjectDir = $PSScriptRoot
$SdkDir = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$BuildTools = Join-Path $SdkDir "build-tools\34.0.0"
$AndroidJar = Join-Path $SdkDir "platforms\android-34\android.jar"

$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$ZipAlign = Join-Path $BuildTools "zipalign.exe"
$ApkSigner = Join-Path $BuildTools "apksigner.bat"

$MainDir = Join-Path $ProjectDir "app\src\main"
$ResDir = Join-Path $MainDir "res"
$Manifest = Join-Path $MainDir "AndroidManifest.xml"
$JavaSrcDir = Join-Path $MainDir "java"

$OutDir = Join-Path $ProjectDir "build_out"
$CompiledRes = Join-Path $OutDir "compiled_res.zip"
$GenJava = Join-Path $OutDir "gen"
$ClassesDir = Join-Path $OutDir "classes"
$DexDir = Join-Path $OutDir "dex"
$UnalignedApk = Join-Path $OutDir "app-unaligned.apk"
$AlignedApk = Join-Path $OutDir "app-aligned.apk"
$FinalApk = Join-Path $OutDir "风轮时-v1.0.0-api14.apk"
$Keystore = Join-Path $ProjectDir "debug.keystore"

if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Force -Path $GenJava, $ClassesDir, $DexDir | Out-Null

Write-Host "[1/6] Compiling resources with aapt2..."
& $Aapt2 compile --dir $ResDir -o $CompiledRes
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/6] Linking resources & generating R.java (minSdk=14, targetSdk=34)..."
& $Aapt2 link `
    -I $AndroidJar `
    --manifest $Manifest `
    --min-sdk-version 14 `
    --target-sdk-version 34 `
    --version-code 1 `
    --version-name "1.0.0" `
    --java $GenJava `
    -o $UnalignedApk `
    $CompiledRes
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/6] Compiling Java sources (Java 8 target)..."
$LambdaStubs = Join-Path $BuildTools "core-lambda-stubs.jar"
$JavaFiles = @(Get-ChildItem -Path $JavaSrcDir, $GenJava -Filter "*.java" -Recurse | ForEach-Object { $_.FullName })
& javac -source 1.8 -target 1.8 -Xlint:-options -encoding UTF-8 -bootclasspath "$AndroidJar;$LambdaStubs" -d $ClassesDir @JavaFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/6] Converting bytecode to DEX (min-api 14)..."
$ClassesJar = Join-Path $OutDir "classes.jar"
& jar cf $ClassesJar -C $ClassesDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
& $D8 --release --min-api 14 --lib $AndroidJar --output $DexDir $ClassesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/6] Packaging classes.dex into APK & aligning..."
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($UnalignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $dexFiles = Get-ChildItem -Path $DexDir -Filter "*.dex"
    foreach ($df in $dexFiles) {
        $existing = $zip.GetEntry($df.Name)
        if ($existing) { $existing.Delete() }
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $df.FullName, $df.Name, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}

& $ZipAlign -f -p 4 $UnalignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[6/6] Signing APK (v1 + v2 for Android 4.0+ compatibility)..."
if (-not (Test-Path $Keystore)) {
    $ErrorActionPreference = "Continue"
    & keytool -genkeypair -keystore $Keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -sigalg SHA256withRSA -keysize 2048 -validity 10000 -dname "CN=OldAndroidClock,O=RetroClock,C=CN" 2>&1 | Out-Null
    $ErrorActionPreference = "Stop"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

# Enable both v1 (JAR signing, required for Android 4.0-6.0) and v2 (Android 7.0+)
& $ApkSigner sign --v1-signing-enabled true --v2-signing-enabled true --ks $Keystore --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out $FinalApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host ""
Write-Host "BUILD SUCCESSFUL!"
$sizeKb = [math]::Round((Get-Item $FinalApk).Length / 1024.0, 1)
Write-Host "APK Output: $FinalApk ($sizeKb KB)"
