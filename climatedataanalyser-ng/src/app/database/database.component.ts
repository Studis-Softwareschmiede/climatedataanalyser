import {ChangeDetectorRef, Component, NgZone, OnDestroy, OnInit} from '@angular/core';
import {ApiService} from '../shared/api.service';
import {HttpEventType} from '@angular/common/http';
import {DbLoadResponseDto, DbLoadSteps, SkippedRecord} from './model/DbLoadResponseDto';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

// Spring-Batch BatchStatus-Werte, die "Job läuft gerade" bedeuten.
const RUNNING_STATES = new Set(['STARTING', 'STARTED', 'UNKNOWN']);

// Dashboard-Refresh-Rate während ein Job läuft (Requirement: alle 5 s).
const POLL_MS = 5000;

@Component({
    selector: 'app-database',
    templateUrl: './database.component.html',
    styleUrls: ['./database.component.css'],
    standalone: false
})
export class DatabaseComponent implements OnInit, OnDestroy {
  message: string = '';
  dbLoadResponseDto: DbLoadResponseDto | null = null;
  useFTP: boolean = false;
  isClearing: boolean = false;

  // Live-Laufzeit: vom Server (elapsedSeconds) bei jedem Poll authoritativ gesetzt,
  // dazwischen sekündlich hochgetickt → smoothe Uhr zwischen den 5s-Datenpolls.
  displayElapsed: number = 0;

  // Kurzes Force-Window direkt nach einem Trigger: weiterpollen, bis der neue Job
  // serverseitig in BATCH_JOB_EXECUTION sichtbar ist (Race zwischen Trigger-Return
  // und Job-Insert). Danach bestimmt allein der Server-Status, ob weiter gepollt wird.
  private forcePollUntil: number = 0;
  private static readonly FORCE_WINDOW_MS = 15000;

  // Pipeline-Skeleton: fixe 5 Steps, auch wenn das Backend noch keine Job-Run-Daten hat.
  private static readonly PIPELINE_SKELETON = [
    'download',
    'unzipFiles',
    'import-temperature-records',
    'import-station-records',
    'import-climate-records',
  ];

  private destroy$ = new Subject<void>();
  private poller: any = null;     // 5s-Datenpoller (nur aktiv solange RUNNING/Force)
  private tickHandle: any = null; // 1s-Uhr-Ticker

  constructor(private apiService: ApiService, private cdr: ChangeDetectorRef, private zone: NgZone) {
  }

  ngOnInit(): void {
    // Ticker + Poller bewusst OUTSIDE Angular zone betreiben und nach jedem Update
    // explizit Change Detection auslösen (refreshView). Sonst rendert die Seite bei
    // setInterval-Updates nicht zuverlässig neu (beobachtet: Timer/Files eingefroren,
    // erst Browser-Refresh half) — robust gegen Zone-Eigenheiten.
    this.zone.runOutsideAngular(() => {
      this.tickHandle = setInterval(() => {
        if (this.isRunning()) { this.displayElapsed++; this.refreshView(); }
      }, 1000);
    });
    // Initial-Fetch spiegelt den ECHTEN Server-Zustand: läuft ein Job → Live-Tracking
    // aufnehmen (auch nach Browser-Refresh), sonst Ergebnis/„nie geladen" zeigen.
    this.pollOnce().then(() => {
      if (this.isRunning() || Date.now() < this.forcePollUntil) this.startPolling();
    });
  }

  /** View explizit neu rendern (für setInterval-/Promise-Updates ausserhalb der Zone). */
  private refreshView(): void {
    try { this.cdr.detectChanges(); } catch { /* CD evtl. schon laufend → ignorieren */ }
  }

  ngOnDestroy(): void {
    this.stopPolling();
    if (this.tickHandle) { clearInterval(this.tickHandle); this.tickHandle = null; }
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ───────────────────────── abgeleiteter Zustand (Server = Wahrheit) ─────────────────────────

  /** True solange ein Job-Run läuft (Server-Status STARTING/STARTED). */
  isRunning(): boolean {
    return RUNNING_STATES.has(this.dbLoadResponseDto?.status || '');
  }

  /** Lädt gerade (Job läuft ODER Force-Window nach Trigger noch offen). */
  get isLoading(): boolean {
    return this.isRunning() || Date.now() < this.forcePollUntil;
  }

  /** Buttons disabled, solange irgendetwas läuft (Single-Job-Anker). */
  isBusy(): boolean {
    return this.isLoading || this.isClearing;
  }

  // ───────────────────────── Polling (robust, terminiert sauber) ─────────────────────────

  private startPolling(): void {
    if (this.poller || this.destroy$.closed) return;
    this.zone.runOutsideAngular(() => {
      this.poller = setInterval(async () => {
        await this.pollOnce();
        // Stoppen, sobald terminal UND Force-Window abgelaufen — sonst läuft der Poller
        // zuverlässig weiter (behebt "Refresh nur einmalig").
        if (!this.isRunning() && Date.now() >= this.forcePollUntil) this.stopPolling();
      }, POLL_MS);
    });
  }

  private stopPolling(): void {
    if (this.poller) { clearInterval(this.poller); this.poller = null; }
  }

  private pollOnce(): Promise<void> {
    return new Promise(resolve => {
      this.apiService.isDbLoaded()
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          complete: () => resolve(),
          error: () => {
            this.message = '⚠ Backend nicht erreichbar (oder /api/database/ liefert Fehler).';
            resolve();
          },
          next: (value) => {
            if (value.type === HttpEventType.Response && value.body != null) {
              this.dbLoadResponseDto = value.body;
              // Laufzeit authoritativ vom Server (Server-Zeit, kein Uhren-Skew).
              if (value.body.elapsedSeconds != null) {
                this.displayElapsed = value.body.elapsedSeconds;
              }
              this.message = this.statusMessage();
              this.refreshView();   // Poller läuft outside zone → CD explizit triggern
            }
          },
        });
    });
  }

  /** Anzeige für einen NEUEN Lauf zurücksetzen: Timer 0, alle Steps pending, Status STARTING. */
  private resetForNewRun(): void {
    this.displayElapsed = 0;
    this.dbLoadResponseDto = {
      ...(this.dbLoadResponseDto ?? new DbLoadResponseDto()),
      status: 'STARTING',
      elapsedSeconds: 0,
      lastLoad: this.dbLoadResponseDto?.lastLoad ?? '',
      dbLoadSteps: [],   // Skeleton → alle 'pending'
    } as DbLoadResponseDto;
  }

  /**
   * Anzeige für die Truncate-Phase leeren — Timer 0, Steps pending, ABER status NICHT
   * 'STARTING' (sonst würde der isBusy()-Guard das anschliessende loadDataBase() blocken).
   */
  private clearDisplay(): void {
    this.displayElapsed = 0;
    this.dbLoadResponseDto = {
      ...(this.dbLoadResponseDto ?? new DbLoadResponseDto()),
      status: 'NEVER_RUN',
      elapsedSeconds: undefined,
      lastLoad: '',
      dbLoadSteps: [],
    } as DbLoadResponseDto;
  }

  // ───────────────────────── Aktionen ─────────────────────────

  loadDataBase(): void {
    if (this.isBusy()) { this.message = '⚠ Es läuft bereits ein Job — bitte warten.'; return; }

    const ftp = this.useFTP ? 'true' : 'false';
    this.resetForNewRun();
    this.message = '⟳ Load wird gestartet…';
    this.forcePollUntil = Date.now() + DatabaseComponent.FORCE_WINDOW_MS;
    this.startPolling();

    this.apiService.loadDataBase(ftp)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.pollOnce(),
        error: (err) => {
          if (err?.status === 409) {
            // Server lehnt ab: es läuft schon ein Job → kein neuer Lauf, nur tracken.
            this.message = '⚠ Es läuft bereits ein Job.';
          } else {
            this.message = '⚠ Load-Trigger fehlgeschlagen — siehe Browser-Console.';
            this.forcePollUntil = 0;
            this.stopPolling();
          }
          this.pollOnce();
        },
      });
  }

  /** Truncate aller Tabellen, dann frischer Load. Nur wenn KEIN Job läuft (Single-Job-Anker). */
  clearAndReload(): void {
    if (this.isBusy()) { this.message = '⚠ Es läuft ein Job — Clear erst danach möglich.'; return; }
    if (!confirm(
      'Wirklich alle Daten + Job-History löschen und neu laden?\n\n' +
      'Truncate auf: CLIMATE, MONTH_, STATION, WEATHER + alle BATCH_*-Tables.'
    )) return;

    this.isClearing = true;
    this.clearDisplay();          // Anzeige leeren, aber NICHT 'STARTING' (sonst blockt loadDataBase)
    this.message = '⟳ Truncate läuft…';

    this.apiService.clearDatabase()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.isClearing = false;
          this.message = '✓ Tabellen geleert — starte Re-Load…';
          this.loadDataBase();
        },
        error: (err) => {
          this.isClearing = false;
          this.message = err?.status === 409
            ? '⚠ Clear nicht möglich — es läuft gerade ein Job.'
            : `⚠ Truncate fehlgeschlagen: ${err?.message || 'unbekannt'}`;
          this.pollOnce();
        },
      });
  }

  // ───────────────────────── Anzeige-Helfer ─────────────────────────

  /** Status-Message kombiniert Job-Status + Kontext. */
  private statusMessage(): string {
    const batchStatus = this.dbLoadResponseDto?.status || 'NEVER_RUN';
    if (RUNNING_STATES.has(batchStatus)) {
      return '⟳ Job läuft gerade — Pipeline-Status aktualisiert sich live (alle 5 s).';
    }
    if (batchStatus === 'FAILED') {
      const failedStep = this.failedStepName();
      return failedStep
        ? `⚠ Letzter Load-Versuch ist fehlgeschlagen an Step "${failedStep}". Details unten.`
        : '⚠ Letzter Load-Versuch ist fehlgeschlagen.';
    }
    if (batchStatus === 'COMPLETED') {
      const total = this.totalRecords();
      return total > 0
        ? `✓ Letzter Lauf erfolgreich — ${total.toLocaleString('de-CH')} Records geschrieben.`
        : '✓ Letzter Lauf erfolgreich (no-op, keine neuen Daten).';
    }
    if (batchStatus === 'STOPPED' || batchStatus === 'ABANDONED') {
      return '⏸ Letzter Load wurde abgebrochen.';
    }
    if (batchStatus === 'NEVER_RUN') {
      return 'Database wurde noch nie geladen.';
    }
    return '';
  }

  private failedStepName(): string {
    const steps = this.dbLoadResponseDto?.dbLoadSteps || [];
    const failed = steps.find(s => s.stepStatus === 'FAILED');
    return failed?.stepName || '';
  }

  private totalRecords(): number {
    const steps = this.dbLoadResponseDto?.dbLoadSteps || [];
    return steps.reduce((sum, s) => sum + (parseInt(s.writeCount || '0', 10)), 0);
  }

  /** Mergt Pipeline-Skeleton mit Backend-Steps. Fehlende = 'pending'. */
  mergedSteps(): DbLoadSteps[] {
    const backendSteps: DbLoadSteps[] = this.dbLoadResponseDto?.dbLoadSteps || [];
    const backendByName = new Map<string, DbLoadSteps>();
    for (const s of backendSteps) {
      backendByName.set(s.stepName, s);
    }
    return DatabaseComponent.PIPELINE_SKELETON.map(name => {
      const found = backendByName.get(name);
      if (found) return found;
      return {
        stepName: name,
        startTime: '',
        stepEndTime: '',
        readCount: '',
        writeCount: '',
        stepStatus: 'pending',
      } as DbLoadSteps;
    });
  }

  /** Step-Icon: pending / running / done / failed / stopped. */
  stepIconClass(status: string): string {
    switch (status) {
      case 'COMPLETED': return 'fas fa-check-circle text-success';
      case 'FAILED':    return 'fas fa-times-circle text-danger';
      case 'STARTED':
      case 'STARTING': return 'fas fa-sync fa-spin text-primary';
      case 'STOPPED':
      case 'ABANDONED': return 'fas fa-pause-circle text-warning';
      case 'pending':
      default:          return 'far fa-circle text-muted';
    }
  }

  /** Gesamt-Laufzeit-Label: läuft → "läuft seit 2 m 25 s", fertig → "Dauer 6 m 12 s". */
  elapsedLabel(): string {
    const es = this.dbLoadResponseDto?.elapsedSeconds;
    const status = this.dbLoadResponseDto?.status || '';
    if (this.isRunning()) {
      return `läuft seit ${this.fmtDuration(this.displayElapsed)}`;
    }
    if (es != null && ['COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED'].includes(status)) {
      return `Dauer ${this.fmtDuration(es)}`;
    }
    return '';
  }

  /** Sekunden → "Y s" oder "X m Y s". */
  fmtDuration(secs: number): string {
    const s = Math.max(0, Math.floor(secs));
    if (s < 60) return `${s} s`;
    const m = Math.floor(s / 60);
    return `${m} m ${s % 60} s`;
  }

  /** Step-Dauer ("4m 35s", "0.3s", "" wenn nicht gelaufen). */
  stepDuration(step: DbLoadSteps): string {
    if (!step.startTime || !step.stepEndTime) return '';
    const start = new Date(step.startTime.replace(' ', 'T')).getTime();
    const end = new Date(step.stepEndTime.replace(' ', 'T')).getTime();
    if (isNaN(start) || isNaN(end) || end < start) return '';
    const secs = Math.round((end - start) / 1000);
    if (secs < 60) return `${secs}s`;
    const mins = Math.floor(secs / 60);
    return `${mins}m ${secs % 60}s`;
  }

  /**
   * Read/Write-Anzeige: bei download/unzipFiles File-Counts aus den Verzeichnissen
   * (Spring-Batch trackt 0/0 für non-chunk Tasklets), sonst Standard r/w-Counts.
   */
  stepCounts(step: DbLoadSteps): string {
    const fc = this.dbLoadResponseDto?.fileCounts || {};
    const r = parseInt(step.readCount || '0', 10);
    const w = parseInt(step.writeCount || '0', 10);

    if (step.stepName === 'download' && (r === 0 && w === 0)) {
      const n = fc['ftpData'] || 0;
      return n > 0 ? `${n.toLocaleString('de-CH')} ZIP-Files` : 'no files';
    }
    if (step.stepName === 'unzipFiles' && (r === 0 && w === 0)) {
      const n = fc['inputFiles'] || fc['unzipedFiles'] || 0;
      return n > 0 ? `${n.toLocaleString('de-CH')} entpackte Files` : 'no files';
    }
    if (r === 0 && w === 0) return 'no-op';
    const fmt = (n: number) => n.toLocaleString('de-CH');
    return `${fmt(r)} read · ${fmt(w)} written`;
  }

  // ───────────────────────── FAILED-Step-Details + Skip-Bericht ─────────────────────────

  expandedStep: string | null = null;

  toggleStepDetails(stepName: string): void {
    this.expandedStep = this.expandedStep === stepName ? null : stepName;
  }

  isExpanded(stepName: string): boolean {
    return this.expandedStep === stepName;
  }

  shortExitMessage(step: DbLoadSteps): string {
    if (!step.exitMessage) return '';
    return step.exitMessage.length > 600
      ? step.exitMessage.substring(0, 600) + '\n…'
      : step.exitMessage;
  }

  skippedRecords(): SkippedRecord[] {
    return this.dbLoadResponseDto?.skippedRecords || [];
  }

  skippedCount(): number {
    return this.skippedRecords().length;
  }

  skippedForStep(stepName: string): SkippedRecord[] {
    return this.skippedRecords().filter(r => r.stepName === stepName);
  }

  /** Status-Badge-Farbe. */
  statusBadgeClass(): string {
    const status = this.dbLoadResponseDto?.status || '';
    if (status === 'COMPLETED') return 'bg-success';
    if (status === 'FAILED') return 'bg-danger';
    if (RUNNING_STATES.has(status)) return 'bg-primary';
    if (status === 'STOPPED' || status === 'ABANDONED') return 'bg-warning text-dark';
    return 'bg-secondary';
  }
}
