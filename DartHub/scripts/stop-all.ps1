$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$pidDir = Join-Path $root 'logs/pid'
if (-not (Test-Path $pidDir)) {
    Write-Output 'Nessun processo DartHub registrato.'
    return
}

$files = Get-ChildItem $pidDir -Filter '*.pid' -File | Sort-Object {
    switch -Regex ($_.BaseName) {
        '^sensor-simulator' { 0 }
        '^edge-component' { 1 }
        '^api-gateway' { 2 }
        '^mosquitto$' { 4 }
        default { 3 }
    }
}
$modules = @('sensor-simulator', 'edge-component', 'api-gateway', 'user-service',
    'locale-service', 'game-service', 'match-service', 'tournament-service', 'statistics-service', 'mosquitto')
foreach ($file in $files) {
    $name = $file.BaseName
    $module = $name -replace '-(auto-\d+|\d+-\d+|local)$', ''
    if ($module -notin $modules) { continue }
    $processId = 0
    if (-not [int]::TryParse((Get-Content $file.FullName -Raw).Trim(), [ref]$processId) -or $processId -le 0) {
        Write-Warning "PID non valido: $($file.Name)"
        continue
    }
    $info = Get-CimInstance Win32_Process -Filter "ProcessId = $processId"
    if (-not $info) {
        Remove-Item -LiteralPath $file.FullName
        continue
    }

    $command = $info.CommandLine -replace '\\', '/'
    $valid = if ($module -eq 'mosquitto') {
        $info.Name -eq 'mosquitto.exe' -and $command.Contains('mosquitto.demo.conf')
    } else {
        $info.Name -in @('java.exe', 'javaw.exe') -and $command.Contains("$module/target/$module.jar")
    }
    if ($name -match '^edge-component-auto-(\d+)$') {
        $valid = $valid -and $command.Contains("edge-$($Matches[1]).properties")
    }
    if ($name -match '^sensor-simulator-auto-(\d+)$') {
        $configFile = "$root/data/edge-instances/edge-$($Matches[1]).properties"
        if (-not (Test-Path $configFile)) {
            $valid = $false
        } else {
            $settings = Get-Content $configFile | Where-Object { $_ -match '^edge\.(localeId|dartboardId)=\d+$' }
            $settings = ConvertFrom-StringData ($settings -join "`n")
            $valid = $valid -and $settings.Count -eq 2 -and
                $command -match "--localeId\s+$($settings['edge.localeId'])(\s|$)" -and
                $command -match "--dartboardId\s+$($settings['edge.dartboardId'])(\s|$)"
        }
    }
    if (-not $valid) {
        Write-Warning "$name non corrisponde al processo registrato: lo lascio attivo."
        continue
    }
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $processId -Force
        if (-not $process.WaitForExit(15000)) { throw "$name non si e fermato." }
    }
    Remove-Item -LiteralPath $file.FullName
    Write-Output "$name fermato."
}
