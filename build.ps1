param(
    [Parameter(Mandatory=$true)][string]$SdkRoot,
    [Parameter(Mandatory=$true)][string]$JdkRoot,
    [string]$BuildToolsVersion = "34.0.0"
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$out = Join-Path $root build
$tools = Join-Path $SdkRoot "build-tools/$BuildToolsVersion"
$android = Join-Path $SdkRoot "platforms/android-34/android.jar"
$env:JAVA_HOME = $JdkRoot
$env:Path = "$(Join-Path $JdkRoot bin);$env:Path"
function Invoke-Step($Program, $Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build step failed: $Program" }
}
New-Item -ItemType Directory -Force "$out/classes","$out/dex" | Out-Null
Invoke-Step javac @("-encoding","UTF-8","--release","8","-classpath",$android,"-d","$out/classes","$root/MainActivity.java")
$classes = @(Get-ChildItem "$out/classes" -Recurse -Filter *.class | ForEach-Object FullName)
Invoke-Step "$tools/d8.bat" (@("--min-api","31","--lib",$android,"--output","$out/dex") + $classes)
Invoke-Step "$tools/aapt2.exe" @("compile","--dir","$root/res","-o","$out/resources.zip")
Invoke-Step "$tools/aapt2.exe" @("link","-I",$android,"--manifest","$root/AndroidManifest.xml","-o","$out/unsigned.apk","$out/resources.zip")
Invoke-Step jar @("--update","--file","$out/unsigned.apk","-C","$out/dex","classes.dex")
Invoke-Step "$tools/zipalign.exe" @("-f","4","$out/unsigned.apk","$out/swoo-changer-unsigned.apk")
