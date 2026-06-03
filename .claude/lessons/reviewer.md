# Reviewer Lessons — climatedataanalyser (newest first)

## 2026-06-02 — Uncommitted-Diff-Reviews: Zustand in HEAD vs. Arbeitsverzeichnis trennen

Wenn ein Branch (z.B. item-1-creds-envonly) auf demselben Commit-SHA steht wie master,
bedeutet das nicht "keine Änderungen". Die tatsächlichen Änderungen können als
**uncommitted working-directory modifications** vorliegen (`git status` zeigt "modified").
Vorgehen:
1. `git diff master...branch` → 0 Output? → dann `git status` + `git diff HEAD`.
2. Bewertungs-Objekt ist der vollständige Diff: committed (git-tracked) + uncommitted (working copy).
3. Der commitierte HEAD gilt dabei als "Vor-Zustand" — kritische Funde im HEAD (z.B. noch vorhandene
   Plaintext-Credentials) sind im Review zu adressieren, auch wenn die uncommitted Changes sie entfernen.

## 2026-06-02 — git-History: Credentials bleiben nach Commit dauerhaft im git-Log

Wenn ein Secret bereits committed war (auch in älteren Commits), bleibt es in der git-History
dauerhaft sichtbar — das Entfernen in einem späteren Commit löscht es nicht aus der History.
Bei Security-PRs, die Credentials entfernen, immer prüfen:
- War das Secret bereits in einem früheren Commit? → Important: git-History-Cleanup nötig (BFG/git-filter-repo).
- Ist das Secret möglicherweise noch in gepushten Remotes (GitHub/GHCR)? → Rotation + History-Purge empfehlen.

## 2026-06-02 — Reine Deletion-PRs: Kontext aus git-History, nicht nur Diff

Bei PRs, die nur Dateien löschen, muss der Reviewer den **Inhalt der gelöschten Dateien**
aus der git-History laden (`git show <commit>:<path>`), um hartkodierte Secrets,
Inline-URLs oder Security-relevante Muster zu beurteilen — der Diff zeigt nur Minus-Zeilen,
die im Kontext-Puffer möglicherweise nicht vollständig erscheinen.

Vorgehen: `git log --all --follow -- <file>` → ältesten/letzten Commit mit der Datei
ermitteln → `git show <sha>:<path>` für den vollständigen Inhalt.

## 2026-06-02 — Coverage-Lücken in CI sind Folge-Items, kein CHANGES-REQUIRED-Grund

Wenn ein PR bekannte Coverage-Lücken (z.B. kein Test-Gate in CI, kein Sonar)
im Review-Output dokumentiert und als Folge-Items benennt, ist das kein Blocker.
`CHANGES-REQUIRED` nur, wenn der PR selbst neue Lücken *einführt* oder bestehende
Lücken verschleiert — nicht, wenn sie pre-exist und im Review transparent gemacht werden.
