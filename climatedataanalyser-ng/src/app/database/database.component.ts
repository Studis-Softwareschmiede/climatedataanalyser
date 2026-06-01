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
   * läuft (Spring-Batch-Status STARTING/STARTED). Fix vs. vorher: nicht mehr auf
   * DbStatusEnum-`loading` schauen (das wurde live nie auf 'loading' gesetzt).
   */
  async refreshStatus(): Promise<void> {
    await this.pollOnce();
    while (this.isJobRunning() && !this.destroy$.closed) {
      await new Promise(r => setTimeout(r, 2000));
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
   * Beispiel: 'DB ist geladen (745k Records), letzter Job-Run FAILED an Step 4.'
   */
  private statusMessage(): string {
    const batchStatus = this.dbLoadResponseDto?.status || 'NEVER_RUN';
    if (RUNNING_STATES.has(batchStatus)) {
      return '⟳ Job läuft gerade — Pipeline-Status aktualisiert sich alle 2s.';
    }
    if (batchStatus === 'FAILED') {
      return '⚠ Letzter Load-Versuch ist fehlgeschlagen. Pipeline-Status zeigt, welcher Step.';
    }
    if (batchStatus === 'COMPLETED') {
      return '✓ Letzter Load-Lauf erfolgreich abgeschlossen.';
    }
    if (batchStatus === 'STOPPED' || batchStatus === 'ABANDONED') {
      return '⏸ Letzter Load wurde abgebrochen.';
    }
    if (batchStatus === 'NEVER_RUN') {
      return 'Database wurde noch nie geladen.';
    }
    return '';
  }

  loadDataBase(): void {
    const ftp = this.useFTP ? 'true' : 'false';
    this.isLoading = true;
    this.message = '⟳ Triggering Load…';

    this.apiService.loadDataBase(ftp)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        complete: () => this.refreshStatus(),
        error: () => {
          this.message = '⚠ Load-Trigger fehlgeschlagen — siehe Browser-Console.';
          this.isLoading = false;
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

  /**
   * Step-Icon: pending / running / done / failed / stopped.
   */
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

  /**
   * Step-Dauer in lesbarem Format ("4m 35s", "0.3s", "—" wenn nicht gelaufen).
   */
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
   * Read/Write-Anzeige: "skipped" wenn beide 0, sonst formatiert mit Tausender-Punkt.
   */
  stepCounts(step: DbLoadSteps): string {
    const r = parseInt(step.readCount || '0', 10);
    const w = parseInt(step.writeCount || '0', 10);
    if (r === 0 && w === 0) {
      // 0/0 bei COMPLETED = Step war ein No-Op (z.B. download mit skipFTP, oder unzip bei leerem FTPData)
      return 'no-op';
    }
    const fmt = (n: number) => n.toLocaleString('de-CH');
    return `${fmt(r)} read · ${fmt(w)} written`;
  }

  /**
   * Status-Badge-Farbe oben in Card 1.
   */
  statusBadgeClass(): string {
    const status = this.dbLoadResponseDto?.status || '';
    if (status === 'COMPLETED') return 'bg-success';
    if (status === 'FAILED') return 'bg-danger';
    if (RUNNING_STATES.has(status)) return 'bg-primary';
    if (status === 'STOPPED' || status === 'ABANDONED') return 'bg-warning text-dark';
    return 'bg-secondary';
  }
}
