import {DatabaseComponent} from './database.component';
import {ApiService} from '../shared/api.service';
import {DbLoadResponseDto} from './model/DbLoadResponseDto';
import {of, throwError} from 'rxjs';

/**
 * Pure-Logic-Tests der DB-Load-State-Machine (kein TestBed/Browser nötig).
 * Deckt die Requirement-Invarianten ab: Zustand kommt vom Server, Single-Job-Anker,
 * Reset bei neuem Lauf, robustes Start/Stop des Pollers.
 */
describe('DatabaseComponent (state machine)', () => {
  let api: jasmine.SpyObj<ApiService>;
  let c: DatabaseComponent;

  const responseOf = (body: Partial<DbLoadResponseDto>) =>
    of({type: 4 /* HttpEventType.Response */, body} as any);

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['isDbLoaded', 'loadDataBase', 'clearDatabase']);
    api.isDbLoaded.and.returnValue(responseOf({status: 'NEVER_RUN'}));
    c = new DatabaseComponent(api);
  });

  afterEach(() => c.ngOnDestroy());

  function setStatus(status: string, extra: Partial<DbLoadResponseDto> = {}) {
    c.dbLoadResponseDto = {status, dbLoadSteps: [], ...extra} as DbLoadResponseDto;
  }

  it('isRunning: STARTED/STARTING true, COMPLETED/NEVER_RUN false', () => {
    setStatus('STARTED'); expect(c.isRunning()).toBeTrue();
    setStatus('STARTING'); expect(c.isRunning()).toBeTrue();
    setStatus('COMPLETED'); expect(c.isRunning()).toBeFalse();
    setStatus('NEVER_RUN'); expect(c.isRunning()).toBeFalse();
  });

  it('isLoading: true während Job läuft, false wenn idle', () => {
    setStatus('STARTED'); expect(c.isLoading).toBeTrue();
    setStatus('COMPLETED'); expect(c.isLoading).toBeFalse();
  });

  it('isBusy spiegelt Laufen ODER Clearing', () => {
    setStatus('COMPLETED');
    expect(c.isBusy()).toBeFalse();
    c.isClearing = true;
    expect(c.isBusy()).toBeTrue();
  });

  it('loadDataBase startet KEINEN Job, wenn schon einer läuft (Single-Job-Anker)', () => {
    setStatus('STARTED');
    c.loadDataBase();
    expect(api.loadDataBase).not.toHaveBeenCalled();
  });

  it('loadDataBase: Reset auf 0 + STARTING, ruft Trigger, pollt', () => {
    setStatus('COMPLETED', {elapsedSeconds: 290});
    c.displayElapsed = 290;
    api.loadDataBase.and.returnValue(of('' as any));
    c.useFTP = true;

    c.loadDataBase();

    expect(c.displayElapsed).toBe(0);
    expect(c.dbLoadResponseDto?.status).toBe('STARTING');
    expect(c.dbLoadResponseDto?.dbLoadSteps?.length).toBe(0);
    expect(api.loadDataBase).toHaveBeenCalledWith('true');
  });

  it('loadDataBase: 409 vom Server → Meldung "läuft bereits", kein Crash', () => {
    setStatus('NEVER_RUN');
    api.loadDataBase.and.returnValue(throwError(() => ({status: 409})));
    c.loadDataBase();
    expect(c.message).toContain('läuft bereits');
  });

  it('clearAndReload startet kein Clear, wenn ein Job läuft', () => {
    setStatus('STARTED');
    c.clearAndReload();
    expect(api.clearDatabase).not.toHaveBeenCalled();
  });

  it('clearAndReload: nach erfolgreichem Truncate wird der Re-Load WIRKLICH gestartet', () => {
    setStatus('COMPLETED', {elapsedSeconds: 290});
    spyOn(window, 'confirm').and.returnValue(true);
    api.clearDatabase.and.returnValue(of({} as any));
    api.loadDataBase.and.returnValue(of('' as any));

    c.clearAndReload();

    // Truncate lief + Re-Load wurde getriggert (der frühere Bug: STARTING blockte loadDataBase).
    expect(api.clearDatabase).toHaveBeenCalled();
    expect(api.loadDataBase).toHaveBeenCalled();
  });

  it('clearAndReload: 409 (Job läuft) → Meldung, kein Crash', () => {
    setStatus('NEVER_RUN');
    spyOn(window, 'confirm').and.returnValue(true);
    api.clearDatabase.and.returnValue(throwError(() => ({status: 409})));
    c.clearAndReload();
    expect(c.message).toContain('Clear nicht möglich');
  });

  it('statusMessage: COMPLETED ohne Records UND ohne Quelldateien → FTP-Handlungshinweis', () => {
    setStatus('COMPLETED', {
      dbLoadSteps: [{stepName: 'download', writeCount: '0', readCount: '0', stepStatus: 'COMPLETED'} as any],
      fileCounts: {ftpData: 0, unzipedFiles: 0, inputFiles: 0},
    });
    const msg = (c as any).statusMessage();
    expect(msg).toContain('keine Quelldateien');
    expect(msg).toContain('FTP-Download');
  });

  it('statusMessage: COMPLETED mit geschriebenen Records → Erfolgsmeldung (kein FTP-Hinweis)', () => {
    setStatus('COMPLETED', {
      dbLoadSteps: [{stepName: 'import', writeCount: '1555967', readCount: '1555967', stepStatus: 'COMPLETED'} as any],
      fileCounts: {ftpData: 1172, unzipedFiles: 20339, inputFiles: 0},
    });
    const msg = (c as any).statusMessage();
    expect(msg).toContain('erfolgreich');
    expect(msg).not.toContain('Quelldateien');
  });

  it('fmtDuration formatiert Sekunden', () => {
    expect(c.fmtDuration(5)).toBe('5 s');
    expect(c.fmtDuration(145)).toBe('2 m 25 s');
  });

  it('elapsedLabel: "läuft seit" während Lauf, "Dauer" wenn fertig', () => {
    setStatus('STARTED'); c.displayElapsed = 12;
    expect(c.elapsedLabel()).toBe('läuft seit 12 s');
    setStatus('COMPLETED', {elapsedSeconds: 290});
    expect(c.elapsedLabel()).toBe('Dauer 4 m 50 s');
  });
});
