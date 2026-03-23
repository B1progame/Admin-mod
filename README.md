# Admin Utility Mod (AdminMod)

Ein **serverseitiger Fabric-Admin-Mod** für Minecraft mit Fokus auf Moderation, Sicherheits-Workflows und GUI-gestützten Verwaltungsfunktionen.

## Überblick

AdminMod bündelt viele typische Team- und Staff-Tools in einem Modul:

- Admin-GUI (`/admin`, `/admingui`) für schnellen Zugriff auf Aktionen
- Moderation: Ban/Tempban, Freeze, Mute, Watchlist, Notizen
- Vanish inkl. optionaler Fake-Join/-Leave-Messages
- Staff-Chat, Staff-Mail und Session-Historie
- Xray-Tracker + Xray-Replay
- Lag-Analyse (`/lagidentify`)
- Optionales Heatmap-System
- Wartungsmodus und geplanter Serverstop (`/maintenance`, `/sstop`)

## Kompatibilität

Aktuell im Projekt konfiguriert:

- **Minecraft:** `1.21.11`
- **Fabric Loader:** `0.18.4`
- **Fabric API:** `0.141.3+1.21.11`
- **Java:** `21`

## Installation

1. Projekt bauen:
   ```bash
   ./gradlew build
   ```
2. Das erzeugte JAR aus `build/libs/` in den `mods/`-Ordner deines Fabric-Servers kopieren.
3. Server starten.
4. Beim ersten Start wird die Konfiguration auf Basis der Default-Werte erstellt.

> Hinweis: Der Mod ist als serverseitiges Utility gedacht.

## Konfiguration

Die Standard-Konfiguration liegt im Projekt unter:

- `src/main/resources/default_adminmod_config.json`

Dort findest du u. a. Einstellungen für:

- erlaubte Admin-UUIDs (`allowed_admin_uuids`)
- Vanish/Freeze/Mute-Verhalten
- Join-Benachrichtigungen
- Xray-Tracker / Xray-Replay
- Lag-Identify
- Heatmap
- Logging und GUI-Titel

## Wichtige Befehle (Auszug)

| Bereich | Befehle |
|---|---|
| GUI | `/admin`, `/admingui`, `/admin search <text>` |
| Wartung | `/maintenance on/off`, `/sstop <dauer>`, `/sstop cancel` |
| Vanish | `/vanish`, `/vanish <player>` |
| Moderation | `/ban`, `/tempban`, `/unban`, `/freeze`, `/unfreeze`, `/mute`, `/unmute` |
| Team-Kommunikation | `/staffchat`, `/sc <nachricht>`, `/staffmail` |
| Notizen / Watchlist | `/pnote ...`, `/watchlist ...` |
| Diagnose | `/lagidentify ...`, `/rollbacklite ...`, `/heatmap ...` |
| Xray | `/xraytracker ...`, `/xrayreplay ...` |

## Entwicklung

### Voraussetzungen

- JDK 21
- Gradle (Wrapper vorhanden)

### Build

```bash
./gradlew build
```

### Nützliche Tasks

```bash
./gradlew tasks
```

## Lizenz

Dieses Projekt steht unter der **MIT-Lizenz** (siehe `LICENSE` und `LICENSE.txt`).
