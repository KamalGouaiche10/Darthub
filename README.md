# DartHub

Piattaforma a microservizi per la gestione di partite, tornei e bersagli da freccette connessi.

## Struttura

- `DartHub/`: codice sorgente, test, script di avvio e documentazione tecnica.
- `Visione/`: documentazione di visione del progetto.
- `Requisiti/`: specifica dei requisiti.
- `Progettazione/`: documentazione di progettazione.
- `Implementazione/`: documentazione di implementazione.

## Requisiti

- Java 21 o successivo.
- Maven 3.9 o successivo.
- Mosquitto per la comunicazione MQTT.

I servizi utilizzano database SQLite locali, inizializzati automaticamente all'avvio.

## Compilazione e test

```powershell
cd DartHub
mvn clean package
```

## Avvio locale

Dalla cartella `DartHub`, dopo aver configurato Mosquitto:

```powershell
.\scripts\start-all.ps1 -CoreOnly -LocalHttpOnly
```

Il sito locale risponde all'indirizzo `http://127.0.0.1:8090/`.

Le istruzioni complete sono in `DartHub/COMANDI_AVVIO_STOP.md`.

## Arresto

```powershell
.\scripts\stop-all.ps1
```
