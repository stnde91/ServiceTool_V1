# ServiceTool V0.106 - Release Notes

## Übersicht
ServiceTool V0.106 bringt eine vollständige Überarbeitung der Benutzeroberfläche mit Material 3 Design (Material You), die Rückkehr der Telnet-Funktionalität für erweiterte Moxa-Konfiguration und verbesserte Filter-Einstellungen für Zellen.

## 🎨 Material 3 Design - Komplette Überarbeitung

### Dynamic Colors (Material You)
- **Automatische Farbharmonie**: Die App passt sich automatisch an die Systemfarben Ihres Android 14+ Geräts an
- **Dynamische Themenerkennung**: Farben werden basierend auf Ihrem Wallpaper und Systemeinstellungen generiert
- **Intelligente Verfügbarkeitsprüfung**: Aktiviert sich nur auf unterstützten Geräten

### Edge-to-Edge Display
- **Vollbilddarstellung**: Maximale Bildschirmnutzung durch nahtlose Integration mit Systemleisten
- **Intelligente Insets-Behandlung**: Automatische Anpassung von Abständen für optimale Darstellung
- **Verbesserte Navigation**: Schwebende Navigation mit korrekter Positionierung

### Material 3 Komponenten
- **Moderne Button-Styles**: Konsistente Material 3 Button-Designs mit korrekten Farben und Animationen
- **Verbesserte Karten**: MaterialCardView mit optimierten Abständen und Schatten
- **Navigation-Updates**: Bottom Navigation mit Material 3 Farbschema und Indikatoren

### Typography System
- **Material 3 Schriften**: Vollständige Umstellung auf das Material 3 Typography-System
- **Optimierte Lesbarkeit**: Verbesserte Schriftgrößen und -gewichte für bessere Benutzerfreundlichkeit
- **Konsistente Hierarchie**: Einheitliche Typographie-Hierarchie durch die gesamte App

### Farbsystem-Optimierung
- **Theme-Attribute Integration**: Vollständige Umstellung von hardcodierten Farben auf Theme-Attribute
- **Verbesserte Farbkontraste**: Optimierte Farben für bessere Zugänglichkeit
- **Konsistente Farbpalette**: Einheitliche Farbverwendung durch alle UI-Komponenten

## 🌐 Telnet-Funktionalität - Rückkehr der erweiterten Konfiguration

### Moxa Port-Konfiguration
- **Direkte Port-Einstellungen**: Bearbeitung von Baudrate, Datenbits, Stoppbits und Parität über Telnet
- **Live-Konfiguration**: Echtzeitänderungen der seriellen Port-Parameter
- **Visueller Editor**: Benutzerfreundliche Dropdown-Menüs für alle Port-Parameter

### Erweiterte Moxa-Verwaltung
- **Device-Status Monitoring**: Vollständige Übersicht über Moxa-Gerätestatus und Konfiguration
- **Remote-Neustart**: Sicherer Neustart der Moxa über Telnet-Verbindung
- **Verbindungsdiagnostik**: Erweiterte Ping- und Verbindungstests

### Port-Details Interface
- **Card-basierte Darstellung**: Übersichtliche Anzeige aller Port-Konfigurationen
- **Inline-Bearbeitung**: Direktes Bearbeiten von Port-Einstellungen ohne separate Screens
- **Automatic Reload**: Automatisches Nachladen der Konfiguration nach Änderungen

## 🔧 Filter-Einstellungen Verbesserungen

### Progress-Indikatoren
- **Sequenzielle Zellen-Abfrage**: Verbesserte Stabilität durch sequenzielle statt parallele Abfragen
- **Live-Status Updates**: Echtzeitanzeige welche Zelle gerade abgefragt wird
- **Erweiterte Fehlerbehandlung**: Bessere Diagnose bei Verbindungsproblemen

### Verbesserte Kommunikation
- **Timing-Optimierung**: 250ms Verzögerung zwischen Zellen-Abfragen für stabilere Kommunikation
- **Robuste Fehlerbehandlung**: Verbesserte Wiederherstellung bei Kommunikationsfehlern
- **Status-Tracking**: Detailliertes Tracking des Verbindungsstatus pro Zelle

### Multi-Cell Layout
- **Optimierte Anordnung**: Verbesserte visuelle Anordnung für 4, 6 und 8-Zellen-Konfigurationen
- **Dynamische Labels**: Automatische Anpassung der Zellen-Beschriftungen je nach Konfiguration
- **Konsistente Darstellung**: Einheitliche Status-Anzeige über alle Zellen-Layouts

## 🛠️ Technische Verbesserungen

### Android 14 Optimierungen
- **Min SDK 34**: Vollständige Nutzung von Android 14 Features
- **Target SDK 35**: Neueste Android-Funktionen und Sicherheitsfeatures
- **Moderne APIs**: Verwendung aktueller Android-Entwicklungsstandards

### Performance-Optimierungen
- **Verbesserte Netzwerk-Performance**: Optimierte TCP-Socket-Verbindungen
- **Reduzierte UI-Latenz**: Flüssigere Animationen und Übergänge
- **Speicher-Optimierung**: Effizientere Ressourcennutzung

### Stabilität
- **Exception Handling**: Robustere Fehlerbehandlung in allen Netzwerk-Operationen
- **Lifecycle Management**: Verbesserte Fragment- und Activity-Lifecycle-Behandlung
- **Resource Management**: Optimierte Verwaltung von Netzwerk-Ressourcen

## 📱 Benutzerfreundlichkeit

### Visuelle Verbesserungen
- **Konsistente Icons**: Einheitliche Icon-Verwendung durch die gesamte App
- **Verbesserte Abstände**: Optimierte Padding und Margins für bessere Touch-Targets
- **Responsive Design**: Bessere Anpassung an verschiedene Bildschirmgrößen

### Feedback-Systeme
- **Live-Status Updates**: Sofortiges visuelles Feedback bei allen Operationen
- **Progress Indicators**: Klare Fortschrittsanzeigen bei längeren Operationen
- **Error Messages**: Verständliche Fehlermeldungen mit Lösungsvorschlägen

## 🔄 Migration und Kompatibilität

### Einstellungs-Migration
- **Automatische Migration**: Alle bestehenden Einstellungen werden automatisch übernommen
- **Backward Compatibility**: Kompatibilität mit bestehenden Konfigurationsdateien
- **Seamless Upgrade**: Nahtlose Aktualisierung ohne Datenverlust

### Hardware-Kompatibilität
- **Moxa-Kompatibilität**: Vollständige Kompatibilität mit bestehenden Moxa-Installationen
- **Flintec RC3D**: Unveränderte Kompatibilität mit allen RC3D-Protokollversionen
- **Netzwerk-Standards**: Unterstützung aller industriellen Ethernet-Standards

---

**Version**: 0.106  
**Build**: 106  
**Veröffentlichungsdatum**: Juni 2025  
**Mindest-Android-Version**: Android 14 (API 34)  
**Empfohlene Android-Version**: Android 14+ (API 35)

---

*Diese Version markiert einen wichtigen Meilenstein in der Evolution von ServiceTool mit modernem Material 3 Design, erweiterten Konfigurationsmöglichkeiten und verbesserter industrieller Zuverlässigkeit.*