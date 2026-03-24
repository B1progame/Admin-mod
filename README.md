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
=======
# Admin Mod (Fabric) - v2.0.0

`adminmod` is a server-focused Fabric admin utility mod for Minecraft `1.21.11`.

It provides moderation tools, vanish controls, admin chest-style GUI menus, scheduled server stop controls, and investigation utilities for staff workflows.

## Main Features

- Admin GUI (`/admin`) with chest-style clickable menus
- Vanish system with:
- Visibility hiding from normal players
- Optional spectator no-clip while vanished
- Fly speed levels and optional night vision
- Silent join / silent disconnect tools
- Scheduled server stop (`/sstop`) with:
- Countdown announcements (`1m`, `30s`, `15s`, `10 -> 0`)
- Bossbar modes (`off`, `self`, `all`)
- Kick policy modes (`all`, `non_admin`, `nobody`)
- Final `[ServerStop]` disconnect handling before shutdown
- Moderation utilities (freeze, mute, temp actions, history/session tracking)
- Xray tracking / replay tools
- Heatmap / lag identify tools

## Project Versions

- Minecraft: `1.21.11`
- Fabric Loader: `0.18.4`
- Fabric API: `0.141.3+1.21.11`
- Yarn mappings: `1.21.11+build.4`
- Java: `21`

## Build

```powershell
.\gradlew.bat build
```

Built jars are created in:

- `build/libs/`

## Usage (Core Commands)

- `/admin` - open admin GUI
- `/vanish` - toggle vanish for self
- `/sstop <duration>` - schedule server stop (example: `/sstop 15m`)
- `/sstop bossbar <off|self|all>` - set stop bossbar visibility
- `/sstop kick <all|non_admin|nobody>` - set 5-second pre-stop kick policy
- `/sstop cancel` - cancel scheduled stop

## Notes

- This mod is intended for server-side admin usage.
- Keep operator/admin permissions restricted.
- If testing unstable movement behavior, see:
- `MODE5_DISABLE_RUNBOOK.md`
