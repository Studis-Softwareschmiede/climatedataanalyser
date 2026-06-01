import {Component, OnDestroy, OnInit} from '@angular/core';
import {ApiService} from '../shared/api.service';
import {HttpEventType} from '@angular/common/http';
import {DbLoadResponseDto, DbLoadSteps} from './model/DbLoadResponseDto';
import {DbStatus} from '../shared/dbStatusEnum';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

// Spring-Batch BatchStatus-Werte, die "Job läuft gerade" bedeuten.
// Polling läuft solange dbLoadResponseDto.status in dieser Menge ist.
const RUNNING_STATES = new Set(['STARTING', 'STARTED', 'UNKNOWN']);

// Polling-Intervalle: schnell während Job läuft, langsam idle.
const POLL_FAST_MS = 500;
const POLL_IDLE_MS = 2000;

@Component({
  selector: 'app-database',
  templateUrl: './database.component.html',
  styleUrls: ['./database.component.css']
})
export class DatabaseComponent implements OnInit, OnDestroy {
  message: string = '';
  dbLoadResponseDto: DbLoadResponseDto;
  currentDbLoadStatus: DbStatus;
  useFTP: boolean = false;
  isLoading: boolean = false;
  isClearing: boolean = false;

  // Force-Polling-Window: nach loadDataBase()-Trigger pollen wir mindestens
  // FORCE_POLL_WINDOW_MS lang, auch wenn der Backend-Status (noch) NEVER_RUN
  // oder FAILED zurückgibt — Race-Condition zwischen Trigger und Job-Init.
  private forcePollUntil: number = 0;
  private static readonly FORCE_POLL_WINDOW_MS = 30000;

  // Pipeline-Skeleton: fixe 5 Steps, auch wenn das Backend noch keine Job-Run-Daten hat.
  private static readonly PIPELINE_SKELETON = [
    'download',
    'unzipFiles',
    'import-temperature-records',
    'import-station-records',
    'import-climate-records',
  ];

  private destroy$ = new Subject<void>();

  constructor(private apiService: ApiService) {
  }

  ngOnInit() {
    this.refreshStatus();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Pollt /api/database/ einmal sofort. Dann nur weiter pollen solange ein Job AKTUELL
   * läuft (Spring-Batch-Status STARTING/STARTED). Schnelles Polling (500ms) während
   * Job läuft — fängt auch sub-Sek-Runs ab.
   */
  async refreshStatus(): Promise<void> {
    await this.pollOnce();
    // Schleife: pollen solange Job läuft ODER force-poll-window nicht abgelaufen
    // (fängt die Race-Condition direkt nach Trigger ab: Job ist noch nicht in
    // BATCH_JOB_EXECUTION, status liefert NEVER_RUN/FAILED, ohne forcePollUntil
    // würde der Loop sofort enden und alle weiteren Updates verpassen).
    while ((this.isJobRunning() || Date.now() < this.forcePollUntil) && !this.destroy$.closed) {
      await new Promise(r => setTimeout(r, POLL_FAST_MS));
      await this.pollOnce();
    }
    this.isLoading = false;
  }

  private isJobRunning(): boolean {
    const status = this.dbLoadResponseDto?.status || '';
    return RUNNING_STATES.has(status);
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
            if (value.type === HttpEventType.Response) {
              this.currentDbLoadStatus = DbStatus[value.body.isDbLoaded];
              this.dbLoadResponseDto = value.body;
              this.isLoading = this.isJobRunning();
              this.message = this.statusMessage();
            }
          },
        });
    });
  }

  /**
   * Status-Message kombiniert DbStatusEnum + aktuellen Job-Status.
   */
  private statusMessage(): string {
    const batchStatus = this.dbLoadResponseDto?.status || 'NEVER_RUN';
    if (RUNNING_STATES.has(batchStatus)) {
      return '⟳ Job läuft gerade — Pipeline-Status aktualisiert sich live.';
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

  loadDataBase(): void {
    const ftp = this.useFTP ? 'true' : 'false';
    this.isLoading = true;
    this.message = '⟳ Triggering Load…';
    // Force-Polling für 30s — Race-Condition: Backend braucht 100-500ms bis Job
    // im BATCH_JOB_EXECUTION sichtbar ist. Ohne Window stoppt Polling sofort.
    this.forcePollUntil = Date.now() + DatabaseComponent.FORCE_POLL_WINDOW_MS;

    // Sofort optimistische STARTING-Anzeige.
    if (this.dbLoadResponseDto) {
      this.dbLoadResponseDto = {
        ...this.dbLoadResponseDto,
        status: 'STARTING',
        dbLoadSteps: []  // Skeleton zeigt alle pending bis erstes Poll-Resultat
      };
    }

    // FIRE-AND-FORGET: Backend blockiert mit SimpleJobLauncher (sync mode) die ganzen
    // 5-8 Min bis Job durch ist. Wir warten NICHT auf complete, sondern starten Polling
    // sofort parallel — sonst keine Live-Updates während der Job läuft.
    this.apiService.loadDataBase(ftp)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        complete: () => {
          // Job ist hier beendet. Ein letzter Poll holt den Final-Status falls
          // der Loop schon gestoppt hat (Race-Condition unwahrscheinlich aber safe).
          this.pollOnce();
        },
        error: () => {
          this.message = '⚠ Load-Trigger fehlgeschlagen — siehe Browser-Console.';
          this.isLoading = false;
        },
      });

    // Polling SOFORT starten — parallel zum (blockierenden) Trigger-Request.
    this.refreshStatus();
  }

  /**
   * Truncated alle DB-Tabellen + BATCH_*-Tables, dann startet einen neuen Load
   * (mit aktuellem useFTP-Wert).
   */
  clearAndReload(): void {
    if (!confirm(
      'Wirklich alle Daten + Job-History löschen und neu laden?\n\n' +
      'Truncate auf: CLIMATE, MONTH_, STATION, WEATHER + alle BATCH_*-Tables.'
    )) return;

    this.isClearing = true;
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
          this.message = `⚠ Truncate fehlgeschlagen: ${err?.message || 'unbekannt'}`;
        },
      });
  }

  /**
   * Mergt Pipeline-Skeleton mit Backend-Steps. Fehlende = 'pending'.
   */
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

  /** Step-Dauer ("4m 35s", "0.3s", "—" wenn nicht gelaufen). */
  stepDuration(step: DbLoadSteps): string {
    if (!step.startTime || !step.stepEndTime) return '';
    const start = new Date(step.startTime.replace(' ', 'T')).getTime();
    const end = new Date(step.stepEndTime.replace(' ', 'T')).getTime();
    if (isNaN(start) || isNaN(end) || end < start) return '';
    const secs = Math.round((end - start) / 1000);
    if (secs < 60) return `${secs}s`;
    const mins = Math.floor(secs / 60);
    const remSecs = secs % 60;
    return `${mins}m ${remSecs}s`;
  }

  /**
   * Read/Write-Anzeige: bei download/unzipFiles zeigt File-Counts aus den Verzeichnissen
   * (Spring-Batch trackt 0/0 für non-chunk Tasklets), sonst Standard r/w-Counts.
   */
  stepCounts(step: DbLoadSteps): string {
    const fc = this.dbLoadResponseDto?.fileCounts || {};
    const r = parseInt(step.readCount || '0', 10);
    const w = parseInt(step.writeCount || '0', 10);

    // Spezial-Fall: download zeigt FTP-File-Count (aus /download/FTPData/)
    if (step.stepName === 'download' && (r === 0 && w === 0)) {
      const n = fc['ftpData'] || 0;
      return n > 0 ? `${n.toLocaleString('de-CH')} ZIP-Files` : 'no files';
    }
    // Spezial-Fall: unzipFiles zeigt unzipped/inputFiles count
    if (step.stepName === 'unzipFiles' && (r === 0 && w === 0)) {
      const n = fc['inputFiles'] || fc['unzipedFiles'] || 0;
      return n > 0 ? `${n.toLocaleString('de-CH')} entpackte Files` : 'no files';
    }

    if (r === 0 && w === 0) return 'no-op';
    const fmt = (n: number) => n.toLocaleString('de-CH');
    return `${fmt(r)} read · ${fmt(w)} written`;
  }

  /**
   * Klick auf einen FAILED-Step zeigt seine EXIT_MESSAGE (Stack-Trace) im UI an.
   * Toggle: zweiter Klick schließt.
   */
  expandedStep: string | null = null;

  toggleStepDetails(stepName: string): void {
    this.expandedStep = this.expandedStep === stepName ? null : stepName;
  }

  isExpanded(stepName: string): boolean {
    return this.expandedStep === stepName;
  }

  /**
   * Liefert die ersten Zeilen der EXIT_MESSAGE — Spring-Batch packt da den ganzen
   * Stack-Trace rein, das ist im UI zu viel. Nehmen die ersten N Zeichen.
   */
  shortExitMessage(step: DbLoadSteps): string {
    if (!step.exitMessage) return '';
    return step.exitMessage.length > 600
      ? step.exitMessage.substring(0, 600) + '\n…'
      : step.exitMessage;
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
