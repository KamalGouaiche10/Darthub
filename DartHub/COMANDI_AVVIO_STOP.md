# DartHub — comandi di avvio, arresto e riavvio

Questa guida usa il **Prompt dei comandi di Windows (CMD)**. Ogni componente avviato manualmente occupa una finestra separata: lascia la finestra aperta finché vuoi tenerlo acceso.

## 1. Preparazione

In **ogni nuova finestra**, entra nella cartella che contiene il `pom.xml` principale:

```bat
cd /d C:\Users\zakip\Desktop\darthub\DartHub
```

Su un altro computer sostituisci il percorso con quello della tua copia del progetto.

Controlla Java e Maven:

```bat
java -version
mvn -version
```

Il progetto compila e richiede il runtime **Java 25 LTS**. Maven e i servizi devono usare JDK 25 o successivo.

Per compilare tutti i moduli e creare i JAR, **prima ferma gli eventuali servizi in esecuzione**, poi esegui dalla radice Maven:

```bat
mvn clean package
```

Aspetta `BUILD SUCCESS`. Non occorre ricompilare a ogni avvio se il codice non è cambiato.

## 2. Avvio manuale: un processo per finestra

Segui l'ordine indicato. Se un componente è già acceso, non avviarne una seconda copia sulla stessa porta.

### 1 — Mosquitto: broker MQTT, porte 1883 e 9001

```bat
"C:\Program Files\mosquitto\mosquitto.exe" -c mosquitto/mosquitto.demo.conf -v
```

Se Mosquitto è installato altrove, modifica il percorso dell'eseguibile. Il file `mosquitto.demo.conf` è quello per le prove sul proprio computer.

**Stop:** premi `Ctrl+C` nella finestra di Mosquitto. Fermalo per ultimo, dopo i componenti che lo usano.

### 2 — User-service: porta 7001

```bat
java -jar user-service/target/user-service.jar
```

Aspetta `user-service avviato sulla porta 7001` prima di avviare gli altri servizi: recuperano da qui la chiave pubblica per verificare i token.

**Controllo:** <http://localhost:7001/health>. Con il broker acceso deve indicare `mqtt: CONNESSO`.

**Stop:** `Ctrl+C` nella finestra di user-service.

### 3 — Locale-service: porta 7002

```bat
java -jar locale-service/target/locale-service.jar
```

**Controllo:** <http://localhost:7002/health>.

**Stop:** `Ctrl+C` nella finestra di locale-service.

### 4 — Game-service: porta 7003

```bat
java -jar game-service/target/game-service.jar
```

**Controllo:** <http://localhost:7003/health>.

**Stop:** `Ctrl+C` nella finestra di game-service.

### 5 — Match-service: porta 7004

```bat
java -jar match-service/target/match-service.jar
```

**Controllo:** <http://localhost:7004/health>.

**Stop:** `Ctrl+C` nella finestra di match-service.

### 6 — Tournament-service: porta 7005

```bat
java -jar tournament-service/target/tournament-service.jar
```

**Controllo:** <http://localhost:7005/health>.

**Stop:** `Ctrl+C` nella finestra di tournament-service.

### 7 — Statistics-service: porta 7006

```bat
java -jar statistics-service/target/statistics-service.jar
```

**Controllo:** <http://localhost:7006/health>.

**Stop:** `Ctrl+C` nella finestra di statistics-service.

### 8 — Gateway HTTP locale: porta 8090

```bat
java -Dgateway.localHttp=true -jar api-gateway/target/api-gateway.jar
```

Aspetta `api-gateway avviato — HTTP loopback 8090`.

**Sito:** <http://127.0.0.1:8090/>.

**Controllo:** <http://127.0.0.1:8090/health>.

**Stop:** `Ctrl+C` nella finestra del gateway HTTP.

Questo gateway serve il sito locale e inoltra le richieste ai servizi. Non richiede il certificato HTTPS.

### 9 — Gateway HTTPS: porta 8443

```bat
java -jar api-gateway/target/api-gateway.jar
```

**Sito:** <https://localhost:8443/>.

**Controllo:** <https://localhost:8443/health>.

**Stop:** `Ctrl+C` nella finestra del gateway HTTPS.

HTTP locale e HTTPS sono **due processi distinti dello stesso JAR**. Per usare il sito locale basta il gateway HTTP; nella configurazione predefinita l'edge usa invece quello HTTPS per sincronizzarsi.

Il gateway HTTPS richiede `keystore.p12`, con la password configurata in `api-gateway/src/main/resources/gateway.properties`. Se il file manca, occorre configurare il certificato prima di avviare questa istanza. Un certificato autofirmato locale può richiedere la conferma nel browser.

### 10 — Edge predefinito: locale 1, bersaglio 1, monitor 7100

```bat
java -jar edge-component/target/edge-component.jar
```

**Monitor:** <http://localhost:7100/>.

**Stop:** `Ctrl+C` nella finestra dell'edge.

Gli ID, il database e gli indirizzi usati sono impostati in `edge-component/src/main/resources/edge.properties`. Per autenticarsi sul centrale serve il token del dispositivo.

#### Prima registrazione dell'edge

Avvia prima user-service e locale-service. Il locale e il bersaglio devono già esistere. Usa un gestore autorizzato sul bersaglio configurato:

```bat
java -jar edge-component/target/edge-component.jar --register --username NOME_UTENTE_GESTORE
```

Sostituisci `NOME_UTENTE_GESTORE` con il nome utente reale. La password viene richiesta nel terminale; non occorre scriverla nel comando.

La registrazione salva il token locale e termina. Poi esegui il normale comando di avvio dell'edge. Non occorre ripetere la registrazione a ogni riavvio se il token è ancora valido.

### 11 — Simulatore predefinito: locale 1, bersaglio 1, pannello 7200

```bat
java -jar sensor-simulator/target/sensor-simulator.jar --localeId 1 --dartboardId 1 --portaWeb 7200
```

**Pannello:** <http://localhost:7200/>.

**Stop:** `Ctrl+C` nella finestra del simulatore.

Gli ID del simulatore devono corrispondere a quelli del bersaglio gestito dall'edge. Dal sito puoi anche usare **Apri pannello simulatore**, che ne gestisce l'avvio locale: non avviare due simulatori per lo stesso bersaglio.

## 3. Altre istanze edge e simulatori

Questi comandi sono aggiuntivi: usa soltanto le istanze dei bersagli che hai effettivamente configurato e registrato. Ogni edge deve avere il proprio token e database; ogni simulatore deve usare gli stessi ID del relativo edge.

### Locale 5, bersaglio 7

Avvio edge con il file di configurazione dedicato:

```bat
java -Dconfig.file=edge-component/edge-5-7.properties -jar edge-component/target/edge-component.jar
```

**Monitor edge:** <http://localhost:7107/>. **Stop:** `Ctrl+C` nella sua finestra.

Avvio simulatore in un'altra finestra:

```bat
java -jar sensor-simulator/target/sensor-simulator.jar --localeId 5 --dartboardId 7 --portaWeb 7207
```

**Pannello simulatore:** <http://localhost:7207/>. **Stop:** `Ctrl+C` nella sua finestra.

### Locale 2, bersaglio 3

Avvio edge con il file di configurazione dedicato:

```bat
java -Dconfig.file=edge-component/edge-2-3.properties -jar edge-component/target/edge-component.jar
```

**Monitor edge:** <http://localhost:7103/>. **Stop:** `Ctrl+C` nella sua finestra.

Avvio simulatore in un'altra finestra, usando una porta libera distinta:

```bat
java -jar sensor-simulator/target/sensor-simulator.jar --localeId 2 --dartboardId 3 --portaWeb 7203
```

**Pannello simulatore:** <http://localhost:7203/>. **Stop:** `Ctrl+C` nella sua finestra.

Per registrare un edge con un file dedicato, passa lo stesso `-Dconfig.file=...` anche al comando `--register`.

## 4. Fermare un solo processo da un'altra finestra

Per i processi avviati manualmente, usa prima `Ctrl+C` nella loro finestra. Se non hai accesso a quella finestra, puoi identificare il processo dalla porta e arrestare soltanto quello.

### Individua il PID del servizio

Esegui **solo la riga relativa al componente che vuoi fermare**:

| Componente | Comando CMD |
| --- | --- |
| Mosquitto | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":1883 "` |
| User-service | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7001 "` |
| Locale-service | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7002 "` |
| Game-service | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7003 "` |
| Match-service | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7004 "` |
| Tournament-service | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7005 "` |
| Statistics-service | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7006 "` |
| Gateway HTTP locale | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":8090 "` |
| Gateway HTTPS | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":8443 "` |
| Edge predefinito | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7100 "` |
| Simulatore predefinito | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7200 "` |
| Edge locale 5 / bersaglio 7 | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7107 "` |
| Simulatore locale 5 / bersaglio 7 | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7207 "` |
| Edge locale 2 / bersaglio 3 | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7103 "` |
| Simulatore locale 2 / bersaglio 3 | `netstat -ano -p tcp \| findstr LISTENING \| findstr ":7203 "` |

Il **PID è l'ultimo numero della riga**. Se non compare nessuna riga, non c'è un processo in ascolto su quella porta.

### Verifica il processo e arrestalo

Esempio: se il PID trovato è `12345`, controllalo prima:

```bat
tasklist /FI "PID eq 12345"
```

Per controllare anche il comando di avvio, dalla stessa finestra CMD:

```bat
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter 'ProcessId = 12345' | Select-Object ProcessId, Name, CommandLine | Format-List"
```

Verifica che il comando corrisponda al JAR atteso, oppure a Mosquitto. **Sostituisci `12345` con il PID appena trovato** in tutti i comandi.

Se è il processo giusto, puoi forzarne l'arresto:

```bat
taskkill /PID 12345 /F
```

Questo è uno stop forzato: usalo quando non puoi usare `Ctrl+C`. Non usare `taskkill /IM java.exe /F`, perché fermerebbe anche gli altri servizi e programmi Java.

## 5. Riavvio e manutenzione di un singolo servizio

1. Concludi le operazioni in corso che dipendono dal servizio.
2. Fermalo con `Ctrl+C`, oppure identifica e arresta il suo processo come descritto sopra.
3. Esegui l'intervento. Se devi ricompilare un solo modulo, dalla radice Maven puoi usare, per esempio:

   ```bat
   mvn -pl user-service -am package
   ```

   `-am` compila anche i moduli Maven necessari. Prima di ricompilarli, ferma eventuali processi che ne tengono aperti i JAR; per una modifica a `darts-common` ricompila e riavvia anche i servizi che lo incorporano.

4. Ripeti il comando di avvio del servizio nella sua finestra.
5. Controlla il relativo indirizzo `/health`.

Fermare un servizio lascia accesi gli altri processi, ma le funzioni che lo chiamano possono risultare indisponibili. Lo stato `MANUTENZIONE` nell'interfaccia riguarda i bersagli: non mette automaticamente un microservizio in manutenzione. Non è garantito il recupero di tutti gli eventi MQTT pubblicati mentre un consumatore è spento.

## 6. Avvio e arresto collettivo con gli script esistenti

Questi comandi sono un'alternativa alle finestre manuali. Evita di mescolare i due metodi per gli stessi processi.

### Avvio in background

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-all.ps1
```

Avvia broker, sei servizi, gateway HTTPS e HTTP locale e gli edge per cui trova i token. Il gateway HTTPS richiede il keystore. Nella versione attuale dello script, il simulatore si avvia dal sito tramite **Apri pannello simulatore**.

Per avviare broker, sei servizi e solo gateway HTTP locale, senza edge e senza gateway HTTPS:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-all.ps1 -CoreOnly -LocalHttpOnly
```

### Stop dei processi registrati dagli script

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stop-all.ps1
```

Lo script usa i PID salvati in `logs/pid` e verifica i processi prima di fermarli. **Non arresta automaticamente i servizi che hai avviato a mano**, perché i comandi manuali di questa guida non registrano quei PID. Anche un broker già esistente e riutilizzato può restare acceso se non è stato registrato dallo script.

### Log dei processi in background

Esempio per user-service:

```bat
type logs\user-service.out.log
type logs\user-service.err.log
```

Sostituisci `user-service` con il nome del componente; per il gateway HTTP locale usa `api-gateway-local`.

## 7. Problemi comuni

| Sintomo | Cosa controllare |
| --- | --- |
| `mqtt: DISCONNESSO` | Avvia prima Mosquitto, poi riavvia il servizio se era partito senza broker. |
| Il sito si vede ma il login dice che non contatta il server | Controlla `http://127.0.0.1:8090/health` e `http://localhost:7001/health`. La pagina può essere visibile dalla cache anche con il gateway spento. |
| Porta già occupata | Non avviare una seconda istanza; identifica il PID della porta prima di decidere quale processo fermare. |
| Errore sulla chiave pubblica JWT durante l'avvio | Avvia user-service e attendi che risponda, poi avvia o riavvia il servizio interessato. |
| Il gateway HTTPS non parte | Controlla `keystore.p12`, percorso e password configurati. Il gateway HTTP locale non richiede quel file. |
| Edge non autenticato o non sincronizzato | Controlla token, ID del bersaglio, broker e gateway HTTPS configurato per quell'istanza. |
| Il JAR non esiste | Compila dalla radice Maven con `mvn clean package`, dopo aver fermato i servizi interessati. |

## 8. Ordine di spegnimento completo

Per spegnere tutto manualmente, usa `Ctrl+C` nelle rispettive finestre seguendo questo ordine:

1. Simulatori.
2. Edge.
3. Gateway HTTP e HTTPS.
4. Tournament-service, match-service e statistics-service.
5. Game-service e locale-service.
6. User-service.
7. Mosquitto.

I database restano nella cartella `data`: fermare e riavviare i servizi non richiede di eliminarli.
