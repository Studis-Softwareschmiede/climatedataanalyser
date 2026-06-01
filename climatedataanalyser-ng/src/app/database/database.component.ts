import {Component, OnDestroy, OnInit} from '@angular/core';
import {ApiService} from '../shared/api.service';
import {HttpEventType} from '@angular/common/http';
import {DbLoadResponseDto, DbLoadSteps} from './model/DbLoadResponseDto';
import {DbStatus} from '../shared/dbStatusEnum';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

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
  // Reihenfolge entspricht der Spring-Batch-Job-Definition (importGermanClimateDataJob).
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
   * Pollt /api/database/ einmal sofort, dann alle 2s solange Status=loading.
   * Cleanup via takeUntil(destroy$) — kein Memory-Leak (Issue #13 partial fix).
   */
  async refreshStatus(): Promise<void> {
    await this.pollOnce();
    while (this.currentDbLoadStatus === DbStatus.loading && !this.destroy$.closed) {
      await new Promise(r => setTimeout(r, 2000));
      await this.pollOnce();
    }
    if (this.currentDbLoadStatus !== DbStatus.loading) {
      this.isLoading = false;
    }
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
              this.isLoading = this.currentDbLoadStatus === DbStatus.loading;
              this.message = this.statusMessage(this.currentDbLoadStatus);
            }
          },
        });
    });
  }

  private statusMessage(status: DbStatus): string {
    switch (status) {
      case DbStatus.empty:
        return 'Datenbank ist leer — klicke Load Database, um den Import zu starten.';
      case DbStatus.failed:
        return 'Letzter Load-Versuch ist fehlgeschlagen. Siehe Pipeline-Status für Details.';
      case DbStatus.loading:
        return 'Database lädt gerade — Pipeline-Status aktualisiert sich automatisch.';
      case DbStatus.loaded:
        return '';
      case DbStatus.stopped:
        return 'Letzter Load wurde gestoppt.';
      default:
        return '';
    }
  }

  loadDataBase(): void {
    const ftp = this.useFTP ? 'true' : 'false';
    this.isLoading = true;
    this.message = 'Triggering Load…';

    this.apiService.loadDataBase(ftp)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        complete: () => {
          this.currentDbLoadStatus = DbStatus.loading;
          this.refreshStatus();
        },
        error: () => {
          this.message = '⚠ Load-Trigger fehlgeschlagen — siehe Browser-Console.';
          this.isLoading = false;
        },
      });
  }

  /**
   * Mergt das Pipeline-Skeleton mit den Backend-gelieferten Step-Daten.
   * Fehlende Steps werden als 'pending' angezeigt.
   */
  mergedSteps(): DbLoadSteps[] {
    const backendSteps: DbLoadSteps[] = this.dbLoadResponseDto?.dbLoadSteps || [];
    const backendByName = new Map<string, DbLoadSteps>();
    for (const s of backendSteps) {
      backendByName.set(s.stepName, s);
    }

    return DatabaseComponent.PIPELINE_SKELETON.map(name => {
      const found = backendByName.get(name);
      if (found) {
        return found;
      }
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
   * Icon-Mapping pro Spring-Batch Step-Status. Font-Awesome 5.
   */
  stepIconClass(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'fas fa-check-circle text-success';
      case 'FAILED':
        return 'fas fa-times-circle text-danger';
      case 'STARTED':
      case 'STARTING':
        return 'fas fa-sync fa-spin text-primary';
      case 'STOPPED':
      case 'ABANDONED':
        return 'fas fa-pause-circle text-warning';
      case 'pending':
      default:
        return 'far fa-circle text-muted';
    }
  }

  /**
   * Status-Badge-Farbe (oben in Card 1 neben "Status:").
   */
  statusBadgeClass(): string {
    const status = this.dbLoadResponseDto?.status || '';
    if (status === 'COMPLETED') return 'bg-success';
    if (status === 'FAILED') return 'bg-danger';
    if (status === 'STARTING' || status === 'STARTED') return 'bg-primary';
    if (status === 'STOPPED' || status === 'ABANDONED') return 'bg-warning text-dark';
    return 'bg-secondary';
  }
}
