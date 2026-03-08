# 🍌 BananaAntiCheat

[![Version](https://img.shields.io/badge/version-1.0.0-brightgreen.svg)](https://github.com/ALEeMAYAyt/BananaAntiCheat/releases)
[![Minecraft](https://img.shields.io/badge/minecraft-1.8+-blue.svg)](https://www.spigotmc.org/)
[![License](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-8+-orange.svg)](https://www.oracle.com/java/)

Un anticheat **semplice e basilare** per server Minecraft 1.8+, ottimizzato per PvP e modalità competitive come BedWars e Bridging.

## ✨ Caratteristiche

- 🎯 **Zero falsi positivi** - Calibrato per gameplay competitivo e tecniche avanzate
- ⚡ **Leggero e performante** - Nessun impatto sulle performance del server
- 🔧 **Facile da configurare** - Config.yml intuitivo con valori predefiniti ottimizzati
- 🛡️ **17 check implementati** - Protezione completa contro i cheat più comuni
- 📊 **Sistema VL graduato** - Accumulo violazioni prima del ban automatico
- 🔔 **Alert in tempo reale** - Notifiche immediate per lo staff

## 📋 Check Implementati

### Movement (7)
| Check | Descrizione |
|-------|-------------|
| **Speed** | Rileva movimento troppo veloce |
| **Fly** | Previene volo non autorizzato |
| **NoFall** | Blocca rimozione danno da caduta |
| **Jesus** | Impedisce camminata sull'acqua |
| **Spider** | Rileva scalata muri senza ladder |
| **Step** | Previene salti troppo alti |
| **NoSlow** | Blocca movimento veloce mentre si blocca |

### Combat (6)
| Check | Descrizione |
|-------|-------------|
| **KillAura** | Rileva attacchi a entità multiple |
| **Reach** | Previene hit a distanza eccessiva (max 4.2 blocchi) |
| **Aimbot** | Rileva aim perfetto sospetto |
| **Velocity** | Previene riduzione knockback |
| **Criticals** | Blocca critici falsi |
| **AutoClicker** | Rileva CPS inumani (max 37 CPS) |

### World Interaction (4)
| Check | Descrizione |
|-------|-------------|
| **Scaffold** | Previene piazzamento blocchi automatico |
| **FastPlace** | Rileva piazzamento troppo veloce |
| **FastBreak** | Blocca rottura blocchi istantanea |
| **Nuker** | Previene rottura massiva blocchi |

## 🚀 Installazione

### Requisiti
- Java 8 o superiore
- Spigot/Paper 1.8+
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) (dipendenza richiesta)

### Passi
1. Scarica [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
2. Scarica l'ultima versione di BananaAntiCheat dalla sezione [Releases](https://github.com/yourusername/BananaAntiCheat/releases)
3. Metti entrambi i file JAR nella cartella `plugins/`
4. Riavvia il server
5. Modifica `plugins/BananaAntiCheat/config.yml` se necessario
6. Usa `/banana reload` per applicare le modifiche

## ⚙️ Configurazione

Ogni check può essere personalizzato nel `config.yml`:

```yaml
check-settings:
  speed:
    enabled: true        # Abilita/disabilita il check
    maxSpeed: 0.5       # Velocità massima consentita
    vl: 1               # Violation Level per trigger
    vl-threshold: 10    # VL necessari per ban
```

### Ottimizzazioni Speciali

BananaAntiCheat include ottimizzazioni per:
- ✅ **Telly Bridge** - FastPlace non dovrebbe flaggare durante bridging veloce
- ✅ **Blocchi Soft** - FastBreak ignora slime, leaves, wool, carpet
- ✅ **Scale e Lastre** - Step rileva e ignora situazioni legittime
- ✅ **Mining** - AutoClicker separato dai click di mining
- ✅ **Freezati** - Timer ignora giocatori fermi

## 🎮 Comandi & Permessi

### Comandi
| Comando | Descrizione |
|---------|-------------|
| `/banana` | Mostra informazioni sul plugin |
| `/banana reload` | Ricarica la configurazione |

### Permessi
| Permesso | Descrizione |
|----------|-------------|
| `bananaac.admin` | Accesso ai comandi admin |
| `bananaac.alerts` | Ricevi notifiche quando un player flagga |
| `bananaac.bypass` | Bypassa tutti i check |

## 🔨 Build dal Codice Sorgente

```bash
# Clona il repository
git clone https://github.com/yourusername/BananaAntiCheat.git
cd BananaAntiCheat

# Compila con Maven
mvn clean package

# Il JAR si troverà in target/BananaAntiCheat-1.0.0.jar
```

## 📊 Sistema VL (Violation Level)

BananaAntiCheat usa un sistema graduato per evitare falsi positivi:

1. Player trigga un check → riceve VL configurati (es. +1)
2. VL si accumula ad ogni violazione
3. Staff riceve alert immediato con VL corrente
4. Al raggiungimento della soglia → ban automatico

**Esempio:** Speed check con `vl: 1` e `vl-threshold: 10`
- Trigger #1-9: Alert allo staff, nessun ban
- Trigger #10: Ban automatico

## 🤝 Contribuire

I contributi sono benvenuti! Per favore:

1. Fai una fork del progetto
2. Crea un branch per la tua feature (`git checkout -b feature/AmazingFeature`)
3. Committa i tuoi cambiamenti (`git commit -m 'Add some AmazingFeature'`)
4. Pusha al branch (`git push origin feature/AmazingFeature`)
5. Apri una Pull Request

### Linee Guida
- Mantieni il codice pulito e commentato
- Testa le modifiche prima di fare PR
- Segui lo stile di codice esistente
- Aggiorna la documentazione se necessario

## 🐛 Segnalare Bug

Hai trovato un bug? [Apri una issue](https://github.com/yourusername/BananaAntiCheat/issues/new) con:
- Descrizione dettagliata del problema
- Passi per riprodurlo
- Versione di Minecraft e del plugin
- Log di errore (se presente)

## 📝 Changelog

### v1.0.0 (2025-XX-XX)
- ✨ Release iniziale
- 🛡️ 17 check implementati
- ⚙️ Sistema di configurazione completo
- 📊 Sistema VL graduato
- 🔔 Alert in tempo reale per staff

## 👥 Autori

- **ALEeMAYAyt** - *Developer principale* - [GitHub](https://github.com/yourusername)
- **nicoltao330** - *Idee e naming*
- **ItzN3xi** - *Aiuto generale e Testing*
- **JustCuz_** - *Aiuto generale e Testing*

## 📄 Licenza

Questo progetto è licenziato sotto la Licenza MIT - vedi il file [LICENSE](LICENSE) per i dettagli.

## 🙏 Ringraziamenti

- [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) per l'API packet handling
- La community di SpigotMC per il supporto
- Tutti i tester che hanno contribuito al miglioramento

---

<div align="center">

**Se ti piace BananaAntiCheat, lascia una ⭐!**

[Report Bug](https://github.com/yourusername/BananaAntiCheat/issues) · [Request Feature](https://github.com/yourusername/BananaAntiCheat/issues) · [SpigotMC](https://www.spigotmc.org/resources/)

</div>
