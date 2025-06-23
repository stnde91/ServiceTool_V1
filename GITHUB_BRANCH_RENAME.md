# GitHub Branch und Tag Umbenennung - Anleitung

## Problem
Die lokalen Tags wurden bereits umbenannt (V1.x → V0.10x), aber auf GitHub existieren noch die alten Branch-Namen.

## Lösung - Branches umbenennen

### Option 1: Über GitHub Web-Interface (Empfohlen)
1. Gehen Sie zu https://github.com/stnde91/ServiceTool_V1
2. Klicken Sie auf den Branch-Selector (zeigt aktuell "master" oder einen Branch-Namen)
3. Für jeden Branch (V1.0, V1.1, V1.102, V1.103, V1.104, V1.105):
   - Wählen Sie den Branch aus
   - Gehen Sie zu Settings → Branches
   - Klicken Sie auf das Stift-Symbol neben dem Branch-Namen
   - Ändern Sie den Namen:
     - V1.0 → V0.100
     - V1.1 → V0.101
     - V1.102 → V0.102
     - V1.103 → V0.103
     - V1.104 → V0.104
     - V1.105 → V0.105

### Option 2: Über Git Command Line (wenn authentifiziert)
```bash
# Für jeden Branch:
git push origin origin/V1.0:refs/heads/V0.100
git push origin :V1.0  # Löscht den alten Branch

git push origin origin/V1.1:refs/heads/V0.101
git push origin :V1.1

git push origin origin/V1.102:refs/heads/V0.102
git push origin :V1.102

git push origin origin/V1.103:refs/heads/V0.103
git push origin :V1.103

git push origin origin/V1.104:refs/heads/V0.104
git push origin :V1.104

git push origin origin/V1.105:refs/heads/V0.105
git push origin :V1.105
```

## Tags pushen
Nach der Authentifizierung:
```bash
# Neue Tags pushen
git push origin V0.100 V0.101 V0.102 V0.104

# Alte Tags löschen (falls vorhanden)
git push origin :refs/tags/V1.0
git push origin :refs/tags/V1.1
git push origin :refs/tags/V1.102
```

## Aktueller Branch
Sie arbeiten momentan auf Branch V1.103. Nach der Umbenennung auf GitHub sollten Sie lokal auch wechseln:
```bash
git branch -m V1.103 V0.103
git push origin V0.103
git push origin --set-upstream V0.103
```

## Wichtig
- Der Branch "1.01" kann gelöscht werden (scheint ein Fehler zu sein)
- "master" bleibt als Haupt-Branch bestehen