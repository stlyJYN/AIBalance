param(
    [string]$SdkRoot = "",
    [string]$JdkRoot = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$AndroidBuildRoot = (Resolve-Path (Join-Path $ProjectRoot "..\android-build")).Path

if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
    $SdkRoot = Join-Path $AndroidBuildRoot "sdk"
}
if ([string]::IsNullOrWhiteSpace($JdkRoot)) {
    $JdkRoot = (Get-ChildItem -Directory (Join-Path $AndroidBuildRoot "jdk-extract") | Select-Object -First 1).FullName
}

$BuildTools = Join-Path $SdkRoot "build-tools\35.0.0"

$env:JAVA_HOME = $JdkRoot
$env:Path = (Join-Path $JdkRoot "bin") + [IO.Path]::PathSeparator + $env:Path
$AndroidJar = Join-Path $SdkRoot "platforms\android-35\android.jar"
$BuildDir = Join-Path $ProjectRoot "build"
$ClassesDir = Join-Path $BuildDir "classes"
$DexDir = Join-Path $BuildDir "dex"
$GeneratedDir = Join-Path $BuildDir "generated"
$ResourceZip = Join-Path $BuildDir "resources.zip"
$UnsignedApk = Join-Path $BuildDir "unsigned.apk"
$AlignedApk = Join-Path $BuildDir "aligned.apk"
$OutputApk = Join-Path $BuildDir "aibalance-V4.2.apk"
$Keystore = Join-Path $BuildDir "debug.keystore"

$projectRootFull = [IO.Path]::GetFullPath($ProjectRoot)
foreach ($directory in @($ClassesDir, $DexDir, $GeneratedDir)) {
    $directoryFull = [IO.Path]::GetFullPath($directory)
    if (-not $directoryFull.StartsWith($projectRootFull + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean outside project root: $directoryFull"
    }
    if (Test-Path -LiteralPath $directoryFull) {
        Remove-Item -LiteralPath $directoryFull -Recurse -Force
    }
}
foreach ($file in @($ResourceZip, $UnsignedApk, $AlignedApk, $OutputApk)) {
    if (Test-Path -LiteralPath $file) {
        Remove-Item -LiteralPath $file -Force
    }
}
foreach ($directory in @($BuildDir, $ClassesDir, $DexDir, $GeneratedDir)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

& (Join-Path $BuildTools "aapt2.exe") compile --dir (Join-Path $ProjectRoot "res") -o $ResourceZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

& (Join-Path $BuildTools "aapt2.exe") link `
    -o $UnsignedApk `
    -I $AndroidJar `
    --manifest (Join-Path $ProjectRoot "AndroidManifest.xml") `
    -R $ResourceZip `
    --java $GeneratedDir `
    --min-sdk-version 23 `
    --target-sdk-version 35 `
    --version-code 62 `
    --version-name "4.2" `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$sourceFiles = @(
    Get-ChildItem -Path (Join-Path $ProjectRoot "src") -Recurse -Filter "*.java" |
        Select-Object -ExpandProperty FullName
)
$sourceFiles += @(
    Get-ChildItem -Path $GeneratedDir -Recurse -Filter "R.java" |
        Select-Object -ExpandProperty FullName
)
& (Join-Path $JdkRoot "bin\javac.exe") `
    -encoding UTF-8 `
    --release 8 `
    -classpath $AndroidJar `
    -d $ClassesDir `
    $sourceFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$classFiles = Get-ChildItem -Path $ClassesDir -Recurse -Filter "*.class" |
    Select-Object -ExpandProperty FullName
& (Join-Path $BuildTools "d8.bat") --lib $AndroidJar --min-api 23 --output $DexDir $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Push-Location $DexDir
try {
    & (Join-Path $JdkRoot "bin\jar.exe") uf $UnsignedApk classes.dex
    if ($LASTEXITCODE -ne 0) { throw "adding classes.dex failed" }
} finally {
    Pop-Location
}

& (Join-Path $BuildTools "zipalign.exe") -f 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

if (-not (Test-Path $Keystore)) {
    & (Join-Path $JdkRoot "bin\keytool.exe") `
        -genkeypair `
        -keystore $Keystore `
        -storepass android `
        -alias androiddebugkey `
        -keypass android `
        -dname "CN=Android Debug,O=Android,C=US" `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000
    if ($LASTEXITCODE -ne 0) { throw "debug keystore generation failed" }
}

& (Join-Path $BuildTools "apksigner.bat") sign `
    --ks $Keystore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $OutputApk `
    $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

& (Join-Path $BuildTools "apksigner.bat") verify --verbose $OutputApk
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

$size = (Get-Item $OutputApk).Length
Write-Output ("APK={0}" -f $OutputApk)
Write-Output ("SIZE_BYTES={0}" -f $size)
