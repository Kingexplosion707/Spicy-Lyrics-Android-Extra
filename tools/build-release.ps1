# Builds a release APK signed with the real release key, the same key CI uses.
# The passwords come from the credentials backup (Export-Clixml, readable only by this Windows
# account) and go straight into the Gradle process environment. They are never printed.
param(
    [string]$KeyDir = "Z:\Spicy-Player-key-backup-2026-09-25-230143\new-release"
)
$ErrorActionPreference = "Stop"

function Plain($v) {
    if ($v -is [securestring]) { return [Net.NetworkCredential]::new("", $v).Password }
    return [string]$v
}

# Finds a field whose name contains one of the patterns, whatever shape the backup was saved in.
function Field($obj, [string[]]$patterns) {
    $names = if ($obj -is [hashtable]) { $obj.Keys } else { $obj.PSObject.Properties.Name }
    foreach ($p in $patterns) {
        $hit = $names | Where-Object { $_ -match $p } | Select-Object -First 1
        if ($hit) { return Plain $obj.$hit }
    }
    return $null
}

$store = Join-Path $KeyDir "spicy-player-release.p12"
$c = Import-Clixml (Join-Path $KeyDir "spicy-player-release-credentials.clixml")

if ($c -is [pscredential]) {
    $alias = $c.UserName
    $storePass = $c.GetNetworkCredential().Password
    $keyPass = $storePass
} else {
    $alias = Field $c @("alias", "user")
    $storePass = Field $c @("store.*pass", "^password$", "pass")
    $keyPass = Field $c @("key.*pass")
    if (-not $keyPass) { $keyPass = $storePass }
}
if (-not ($alias -and $storePass)) {
    $fields = if ($c -is [hashtable]) { $c.Keys } else { $c.PSObject.Properties.Name }
    throw "Could not find alias/password in the backup. Field names: $($fields -join ', ')"
}

$env:ANDROID_RELEASE_KEYSTORE = $store
$env:ANDROID_RELEASE_STORE_PASSWORD = $storePass
$env:ANDROID_RELEASE_KEY_ALIAS = $alias
$env:ANDROID_RELEASE_KEY_PASSWORD = $keyPass
try {
    & "$PSScriptRoot\..\gradlew.bat" -p "$PSScriptRoot\.." assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed" }
} finally {
    Remove-Item Env:ANDROID_RELEASE_* -ErrorAction SilentlyContinue
}

$apk = Get-ChildItem "$PSScriptRoot\..\app\build\outputs\apk\release\*.apk" | Select-Object -First 1
Write-Host "Built $($apk.FullName)"
keytool -printcert -jarfile $apk.FullName | Select-String "SHA256:"
Write-Host "Release key fingerprint starts 51:2E:39 and ends 2F:B5."
