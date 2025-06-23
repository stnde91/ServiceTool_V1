# GitHub Update Setup für ServiceTool

## 1. Repository vorbereiten

### GitHub Repository URL aktualisieren
In `GitHubUpdateService.kt` Zeile 24 die Repository-URL anpassen:
```kotlin
private const val GITHUB_API_URL = "https://api.github.com/repos/IHR_USERNAME/ServiceTool_V1/releases/latest"
```

Ersetzen Sie `IHR_USERNAME` durch Ihren GitHub-Benutzernamen oder Organisationsnamen.

## 2. Release erstellen

### APK Build vorbereiten
1. **Release APK generieren:**
   ```bash
   ./gradlew assembleRelease
   ```

2. **APK signieren** (falls noch nicht automatisch signiert)

### GitHub Release erstellen
1. **Neue Version taggen:**
   ```bash
   git tag v0.106
   git push origin v0.106
   ```

2. **GitHub Release erstellen:**
   - Gehen Sie zu: `https://github.com/IHR_USERNAME/ServiceTool_V1/releases`
   - Klicken Sie auf "Create a new release"
   - **Tag version:** `v0.106`
   - **Release title:** `ServiceTool v0.106`
   - **Description:** Beschreibung der Änderungen
   - **Assets:** Laden Sie die `app-release.apk` hoch

### Wichtige Hinweise für Releases:
- **APK-Datei muss `.apk` Extension haben**
- **Tag muss mit 'v' beginnen** (z.B. v1.105)
- **Release muss "published" sein** (nicht Draft)

## 3. Berechtigungen konfigurieren

### Android-Berechtigungen
Die folgenden Berechtigungen sind bereits in der `AndroidManifest.xml` enthalten:
- `INTERNET` - Für GitHub API-Aufrufe
- `WRITE_EXTERNAL_STORAGE` - Für Download (nur Android ≤ 28)
- `REQUEST_INSTALL_PACKAGES` - Für APK-Installation

### Geräte-Einstellungen
Mitarbeiter müssen auf ihren Geräten:
1. **"Installation aus unbekannten Quellen"** für die App aktivieren
2. **Oder:** In Sicherheitseinstellungen → "ServiceTool" die Installation erlauben

## 4. Verwendung

### Update-Check durchführen
1. App öffnen
2. **Navigation Drawer** → **"App Einstellungen"**
3. Oben rechts **"Update prüfen"** Button
4. Falls Update verfügbar → **"Jetzt aktualisieren"**

### Automatischer Ablauf
1. **GitHub API abfragen** → Neueste Release-Info
2. **Versionsnummer vergleichen** → Aktuelle vs. Verfügbare
3. **APK herunterladen** → In Downloads-Ordner
4. **Installation starten** → Android Package Manager

## 5. Versionsnummern

### Aktuelle App-Version anpassen
In `app/build.gradle.kts`:
```kotlin
versionCode = 106
versionName = "0.106"
```

### Versions-Vergleich
- Format: `0.105` → Wird als `[0, 105]` geparst
- Vergleich: Numerisch pro Segment
- Beispiel: `0.106` > `0.105` ✓

## 6. Troubleshooting

### Häufige Probleme:
1. **"Keine APK gefunden"** → APK-Datei im Release hochladen
2. **"Verbindungsfehler"** → Internet-Verbindung prüfen
3. **"Installation fehlgeschlagen"** → Unbekannte Quellen aktivieren
4. **"Update nicht erkannt"** → Versionsnummer in build.gradle erhöhen

### Logs überprüfen:
- Updates werden in der App geloggt
- **Dashboard** → System-Logs für Details

## 7. Security Hinweise

- APKs sollten **signiert** sein für Produktionsumgebung
- **GitHub Token** ist nicht erforderlich für öffentliche Releases
- Für private Repositories: GitHub API-Token implementieren
- Regelmäßige **Sicherheitsupdates** durchführen

## 8. Testing

### Test-Workflow:
1. **Niedrigere Version** in build.gradle setzen
2. **Test-Release** mit höherer Version erstellen
3. **Update-Flow** in App testen
4. **APK-Installation** verifizieren