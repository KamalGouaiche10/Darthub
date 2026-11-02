param([switch]$CoreOnly, [switch]$LocalHttpOnly)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$logDir = Join-Path $root 'logs'
$pidDir = Join-Path $logDir 'pid'
$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $java) {
    $javaHome = $env:JAVA_HOME
    if (-not $javaHome -or -not (Test-Path "$javaHome/bin/java.exe")) {
        $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    }
    $java = "$javaHome/bin/java.exe"
}
if (-not (Test-Path $java)) { throw 'Java non trovato: verifica PATH o JAVA_HOME.' }
$mosquitto = (Get-Command mosquitto -ErrorAction SilentlyContinue).Source
if (-not $mosquitto) { $mosquitto = 'C:\Program Files\mosquitto\mosquitto.exe' }
if (-not (Test-Path $mosquitto)) { throw 'Mosquitto non trovato: installalo o aggiungilo al PATH.' }

$services = [ordered]@{
    'user-service' = 7001
    'locale-service' = 7002
    'game-service' = 7003
    'match-service' = 7004
    'tournament-service' = 7005
    'statistics-service' = 7006
}
foreach ($module in @($services.Keys) + @('api-gateway', 'edge-component')) {
    if (-not (Test-Path "$root/$module/target/$module.jar")) {
        throw "Manca $module.jar. Compila prima con: mvn package"
    }
}
New-Item -ItemType Directory -Path $pidDir, "$root/data" -Force | Out-Null

function Start-Component($name, $port, $program, [string[]]$options) {
    $pidFile = Join-Path $pidDir "$name.pid"
    $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($listener) {
        $savedId = if (Test-Path $pidFile) { (Get-Content $pidFile -Raw).Trim() } else { '' }
        $owner = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        if ($savedId -eq "$($listener.OwningProcess)" -or
            ($name -eq 'mosquitto' -and $owner.ProcessName -eq 'mosquitto')) {
            Write-Output "$name gia avviato."
            return
        }
        throw "Porta $port occupata da un altro processo."
    }

    $startOptions = @{
        FilePath = $program
        ArgumentList = $options
        WorkingDirectory = $root
        WindowStyle = 'Hidden'
        RedirectStandardOutput = "$logDir/$name.out.log"
        RedirectStandardError = "$logDir/$name.err.log"
        PassThru = $true
    }
    $process = Start-Process @startOptions
    Set-Content $pidFile $process.Id
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($process.HasExited) { throw "$name si e fermato. Controlla logs/$name.err.log." }
        $ready = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
            Where-Object OwningProcess -eq $process.Id
        if ($ready) {
            Write-Output "$name avviato sulla porta $port."
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "$name non risponde sulla porta $port. Controlla i log."
}

Start-Component 'mosquitto' 1883 $mosquitto @('-c', "`"$root/mosquitto/mosquitto.demo.conf`"")
foreach ($service in $services.GetEnumerator()) {
    $name = $service.Key
    Start-Component $name $service.Value $java @('-jar', "$name/target/$name.jar")
}
if (-not $LocalHttpOnly) {
    Start-Component 'api-gateway' 8443 $java @('-jar', 'api-gateway/target/api-gateway.jar')
}
Start-Component 'api-gateway-local' 8090 $java @('-Dgateway.localHttp=true', '-jar', 'api-gateway/target/api-gateway.jar')

if (-not $CoreOnly) {
    if (Test-Path "$root/data/edge-token.secret") {
        Start-Component 'edge-component' 7100 $java @('-jar', 'edge-component/target/edge-component.jar')
    }
    foreach ($board in ([ordered]@{ '5-7' = 7107; '2-3' = 7103 }).GetEnumerator()) {
        $id = $board.Key
        if (Test-Path "$root/data/edge-$id-token.secret") {
            Start-Component "edge-component-$id" $board.Value $java @("-Dconfig.file=edge-component/edge-$id.properties", '-jar', 'edge-component/target/edge-component.jar')
        }
    }
    if (Test-Path "$root/data/edge-instances") {
        foreach ($file in Get-ChildItem "$root/data/edge-instances" -Filter 'edge-*.properties' -File) {
            if ($file.BaseName -notmatch '^edge-(\d+)$') { continue }
            $deviceId = $Matches[1]
            if (-not (Test-Path "$root/data/edge-device-$deviceId.secret")) { continue }
            $displaySetting = Get-Content $file.FullName |
                Where-Object { $_ -match '^edge\.portaDisplay=\d+$' } | Select-Object -First 1
            $displayPort = [int]($displaySetting -replace '^edge\.portaDisplay=', '')
            if ($displayPort -lt 7300 -or $displayPort -ge 7400) {
                throw "Porta monitor non valida: $($file.Name)"
            }
            Start-Component "edge-component-auto-$deviceId" $displayPort $java @("-Dconfig.file=data/edge-instances/$($file.Name)", '-jar', 'edge-component/target/edge-component.jar')
        }
    }
}
Write-Output 'DartHub pronto: http://127.0.0.1:8090/'
Write-Output 'Il simulatore si avvia dalla partita con "Apri pannello simulatore".'
Write-Output "Log: $logDir"
