import {Component, OnDestroy, OnInit} from '@angular/core';
import {ApiService} from '../shared/api.service';
import {GpsPoint} from './model/GpsPoint';
import {ClimateAnalyserResponseDto} from './model/ClimateAnalyserResponseDto';
import {HttpEventType} from '@angular/common/http';
import {FormBuilder, FormControl, FormGroup} from '@angular/forms';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import * as L from 'leaflet';
import 'leaflet-draw';

@Component({
  selector: 'app-analytics',
  templateUrl: './analytics.component.html',
  styleUrls: ['./analytics.component.css']
})
export class AnalyticsComponent implements OnInit, OnDestroy {

  bundeslaender: Array<string>;
  selectedBundesland: string;
  climateAnalyserResponseDto: ClimateAnalyserResponseDto;
  angForm: FormGroup;

  // Map state — public so template can bind [leafletLayer]="drawnItems"
  leafletOptions: L.MapOptions;
  leafletDrawOptions: object;
  drawnItems: L.FeatureGroup;

  // Read-only coordinate display (AC-F9)
  nwDisplay: string = null;
  seDisplay: string = null;

  private map: L.Map;
  private fb: FormBuilder;

  // Lifecycle: unsubscribe all observables on destroy + tear down Leaflet map.
  // Pattern aus database.component.ts übernommen.
  private destroy$ = new Subject<void>();

  constructor(private apiService: ApiService, fb: FormBuilder) {
    this.fb = fb;
    this.createForm();
    this.initLeafletOptions();
  }

  createForm() {
    this.angForm = this.fb.group({
      gps1lat: new FormControl(''),
      gps1long: new FormControl(''),
      gps2lat: new FormControl(''),
      gps2long: new FormControl(''),
      yearO: new FormControl('1989'),
      yearC: new FormControl('2018')
    });
  }

  initLeafletOptions() {
    // AC-F2 + AC-F3
    this.leafletOptions = {
      layers: [
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 18,
          attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        })
      ],
      zoom: 6,
      center: L.latLng(51.16, 10.45)
    };

    this.drawnItems = L.featureGroup();

    // AC-F4 + AC-F6: rectangle only, danger-red style.
    // Edit-Toolbar (Save/Cancel) bewusst DEAKTIVIERT — wir aktivieren das
    // Editing direkt am Layer (persistent edit), so dass Ecken & Mitte
    // sofort nach dem Zeichnen ziehbar sind, ohne den Umweg über Save.
    // Gelöscht wird über den eigenen "Rechteck löschen"-Button im Template.
    this.leafletDrawOptions = {
      position: 'topleft',
      draw: {
        rectangle: {
          shapeOptions: {
            color: '#dc3545',
            weight: 2,
            fillColor: '#dc3545',
            fillOpacity: 0.3
          }
        },
        polygon: false,
        polyline: false,
        circle: false,
        marker: false,
        circlemarker: false
      },
      edit: false
    };
  }

  onMapReady(map: L.Map) {
    this.map = map;

    // AC-F5 + AC-F7 + AC-F8: draw:created → replace old rectangle + persistent edit
    this.map.on('draw:created', (event: any) => {
      this.drawnItems.clearLayers();
      const layer = event.layer as L.Rectangle;
      this.drawnItems.addLayer(layer);
      // Beim Zeichnen via Toolbar: bewusst auch das Dropdown leeren — der User
      // hat ja eine neue Region per Hand definiert, das alte Bundesland-Tag
      // gehört nicht mehr dazu (Convenience-Init ist überholt).
      this.selectedBundesland = '';
      this.updateCoordsFromLayer(layer);
      // Sofort persistent editierbar machen — Eck-Marker & Mitten-Drag verfügbar
      // ohne den Umweg über eine separate Edit-Toolbar + Save.
      this.enablePersistentEdit(layer);
    });
  }

  /**
   * Aktiviert Leaflet.draw's per-layer editing direkt am Rectangle:
   * - Eckpunkte & Mitten-Marker werden sofort angezeigt
   * - User kann Ecken / ganze Box ziehen
   * - Bei jedem Drag-Ende werden die Form-Werte synchronisiert
   * Ersatz für die abgeschaltete Edit-Toolbar (kein "Save"-Klick nötig).
   */
  private enablePersistentEdit(layer: L.Rectangle) {
    const editing = (layer as any).editing;
    if (editing && typeof editing.enable === 'function') {
      editing.enable();
    }
    // Leaflet.draw firet auf dem Layer 'edit' sobald ein Drag abgeschlossen ist.
    // 'editdrag' / 'editmove' während des Drags — wir nehmen das zum Live-Update.
    layer.on('edit editdrag editmove', () => this.updateCoordsFromLayer(layer));
  }

  /**
   * Vom Lösch-Button im Template aufgerufen. Cleart Layer + Form + Display.
   */
  clearRectangle() {
    this.drawnItems.clearLayers();
    this.clearSelection();
    this.selectedBundesland = '';
  }

  private clearSelection() {
    this.nwDisplay = null;
    this.seDisplay = null;
    this.angForm.patchValue({gps1lat: '', gps1long: '', gps2lat: '', gps2long: ''});
  }

  /**
   * Liest den (einzigen) Rectangle-Layer aus drawnItems, oder null.
   * Wird vom Submit-Pfad benutzt, um die aktuellen Bounds direkt vom Layer
   * zu ziehen — robuster als sich auf die Form zu verlassen (das edit-Event
   * von Leaflet.draw firet je nach Version unterschiedlich zuverlässig).
   */
  private getCurrentRectangle(): L.Rectangle | null {
    let found: L.Rectangle | null = null;
    this.drawnItems.eachLayer((l: any) => {
      if (!found && l instanceof L.Rectangle) {
        found = l;
      }
    });
    return found;
  }

  private updateCoordsFromLayer(layer: L.Rectangle) {
    // Defensive: only act on actual Rectangle layers (instanceof guard against
    // Leaflet.draw's edit-markers, which would otherwise produce a degenerate
    // nw == se "box" when accidentally captured).
    if (!(layer instanceof L.Rectangle)) {
      return;
    }
    const bounds = layer.getBounds();
    const nw = bounds.getNorthWest();
    const se = bounds.getSouthEast();

    // Defensive: warn (and skip form update) when the rectangle has zero area —
    // typically a single-click on the map without dragging.
    if (nw.lat === se.lat && nw.lng === se.lng) {
      console.warn('analytics: degenerate rectangle (NW == SE) — ignored. Bitte ziehen statt klicken.');
      return;
    }

    // AC-F8: fill form controls programmatically
    this.angForm.patchValue({
      gps1lat: nw.lat,
      gps1long: nw.lng,
      gps2lat: se.lat,
      gps2long: se.lng
    });

    // AC-F9: 6 decimal places
    this.nwDisplay = `NW: ${nw.lat.toFixed(6)}, ${nw.lng.toFixed(6)}`;
    this.seDisplay = `SE: ${se.lat.toFixed(6)}, ${se.lng.toFixed(6)}`;
  }

  // AC-F12: submit enabled only when a valid (non-degenerate) box OR a Bundesland is set.
  get hasSelection(): boolean {
    const v = this.angForm.value;
    const hasBox =
      v.gps1lat !== '' && v.gps1long !== '' && v.gps2lat !== '' && v.gps2long !== '' &&
      (parseFloat(v.gps1lat) !== parseFloat(v.gps2lat) || parseFloat(v.gps1long) !== parseFloat(v.gps2long));
    const hasBundesland = !!(this.selectedBundesland && this.selectedBundesland !== '');
    return hasBox || hasBundesland;
  }

  // AC-F13: submit. Prioritisierung:
  // 1) Wenn ein Rechteck auf der Karte existiert → live bounds vom Layer lesen
  //    (nicht aus dem Form, falls das edit-Event nicht zuverlässig firet) → GPS senden, Bundesland leer.
  // 2) Wenn KEIN Rechteck, aber Bundesland gewählt → nur Bundesland senden.
  onClickSubmit() {
    const v = this.angForm.value;

    // Live-Bounds vom aktuellen Rectangle-Layer ziehen — das ist die Wahrheit,
    // unabhängig von Form-Werten oder edit-Event-Quirks.
    const rect = this.getCurrentRectangle();
    let gps1: GpsPoint;
    let gps2: GpsPoint;
    let bundeslandParam: string;

    if (rect) {
      const bounds = rect.getBounds();
      const nw = bounds.getNorthWest();
      const se = bounds.getSouthEast();
      gps1 = new GpsPoint(nw.lng, nw.lat);
      gps2 = new GpsPoint(se.lng, se.lat);
      bundeslandParam = '';
      // Form-Werte sync zum aktuellen Stand (vor Submit)
      this.angForm.patchValue({gps1lat: nw.lat, gps1long: nw.lng, gps2lat: se.lat, gps2long: se.lng});
    } else if (this.selectedBundesland) {
      gps1 = new GpsPoint(0, 0);
      gps2 = new GpsPoint(0, 0);
      bundeslandParam = this.selectedBundesland;
    } else {
      alert('Bitte ein Rechteck zeichnen oder ein Bundesland wählen.');
      return;
    }

    this.apiService.getAnalyticsByRequest(
      bundeslandParam, gps1, gps2, v.yearO, v.yearC
    )
      .pipe(takeUntil(this.destroy$))
      .subscribe(
        value => {
          if (value.type === HttpEventType.Response) {
            this.climateAnalyserResponseDto = value.body;
          }
        },
        () => {
          alert('An error occurred while getting analytics');
        }
      );
  }

  ngOnInit() {
    this.initAnalytics();
  }

  ngOnDestroy() {
    // 1) signal all takeUntil-piped subscriptions to unsubscribe
    this.destroy$.next();
    this.destroy$.complete();
    // 2) tear down Leaflet — without this, the map keeps tile-event listeners
    //    on window/document and accumulates per route-navigation.
    if (this.map) {
      this.map.off();
      this.map.remove();
      this.map = null;
    }
  }

  initAnalytics() {
    this.apiService.initAnalytics()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (value) => {
          this.bundeslaender = value;
        },
        error: () => {
          alert('An error occurred while init Analytics, trying to get all Bundeslaender from Backend !');
        }
      });
  }

  // AC-F11: Bundesland → fetch bbox → draw rectangle on map
  onBundeslaenderDropDownListSelected(selectedBundesland: string) {
    if (!selectedBundesland) {
      return;
    }
    this.apiService.getBoundingBoxByBundesland(selectedBundesland)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (bbox) => {
          this.drawnItems.clearLayers();
          const nw: [number, number] = [bbox.nw.latitude, bbox.nw.longitude];
          const se: [number, number] = [bbox.se.latitude, bbox.se.longitude];
          const rect = L.rectangle([nw, se], {
            color: '#dc3545',
            weight: 2,
            fillColor: '#dc3545',
            fillOpacity: 0.3
          });
          this.drawnItems.addLayer(rect);
          this.updateCoordsFromLayer(rect);
          // Auch beim Bundesland-Vorzeichnen: persistent edit aktivieren,
          // damit der User die vorgezeichnete Box direkt feintunen kann.
          this.enablePersistentEdit(rect);
          if (this.map) {
            this.map.fitBounds(rect.getBounds());
          }
        },
        error: () => {
          alert('Could not load bounding box for: ' + selectedBundesland);
        }
      });
  }
}
