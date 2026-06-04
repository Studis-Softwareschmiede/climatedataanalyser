# CLAUDE.md — Arbeitsweise in diesem Repo

## Vorgehen bei Problemen/Fehlern (verbindlich)

Wenn etwas schiefläuft (CI-Fehler, Bug, kaputter Lauf), gilt **strikt dieser Ablauf** —
und nur so weit, wie der Auftrag reicht:

1. **Nur das Beauftragte liefern.** Lautet der Auftrag „nenne mir die Fehlermeldung" /
   „schau nach, was schieflief", dann **berichte ausschließlich Befund + Ursache** — sonst nichts.
2. **Ursache finden** (Root Cause), bevor irgendeine Lösung vorgeschlagen wird.
3. **Maßgeschneiderte Lösung konzipieren** und vorlegen.
4. **Umsetzung durchdenken** (Optionen, Risiken, Aufwand) und abstimmen.
5. **Erst nach expliziter Freigabe handeln.**

### NICHT ungefragt tun
- Keinen Fix schreiben, committen, pushen, **mergen**.
- Keinen Lauf/Workflow **neu starten** oder triggern.
- Keine Branch/PR anlegen.
- Keine „Verbesserung" mitnehmen, die nicht beauftragt wurde.

Auch wenn die Lösung „offensichtlich" erscheint: **erst Diagnose vorlegen, dann auf den
nächsten Auftrag warten.** Der Mensch entscheidet über Lösung und Umsetzung, nicht ich.

> Merksatz: **Diagnose ≠ Mandat zu handeln.** Ein Auftrag zum Anschauen ist kein Auftrag
> zum Reparieren.
