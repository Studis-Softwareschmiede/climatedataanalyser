import {Component, NgZone, OnDestroy, OnInit} from '@angular/core';
import {ApiService} from '../shared/api.service';
import {GpsPoint} from './model/GpsPoint';
import {ClimateAnalyserResponseDto} from './model/ClimateAnalyserResponseDto';
import {HttpEventType} from '@angular/common/http';
import {UntypedFormBuilder, UntypedFormControl, UntypedFormGroup} from '@angular/forms';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import * as L from 'leaflet';
// leaflet-draw bleibt importiert, weil es L.Rectangle.prototype um `.editing`
// erweitert (Persistent-Edit nach dem Zeichnen). Die Toolbar selbst nutzen
// wir NICHT mehr — die war flaky (Click-vs-Drag-Quirks in dieser Bundle-
// Konstellation). Stattdessen eigener Drag-Handler unten.
import 'leaflet-draw';

@Component({
  selector: 'app-analytics',
  templateUrl: './analytics.component.html',
  styleUrls: ['./analytics.component.css']
})
export class AnalyticsComponent implements OnInit, OnDestroy {

  bundeslaender: Array<string> | null = null;
  selectedBundesland: string = '';
  climateAnalyserResponseDto: ClimateAnalyserResponseDto | null = null;
  angForm!: UntypedFormGroup;

  // Map state — public so template can bind [leafletLayer]="drawnItems"
  leafletOptions!: L.MapOptions;
  drawnItems!: L.FeatureGroup;

  // Read-only coordinate display (AC-F9)
  nwDisplay: string | null = null;
  seDisplay: string | null = null;

  // Drag-selection state (eigener Tool-Modus, statt Leaflet.draw)
  isDrawing = false;
  private startLatLng: L.LatLng | null = null;
  private previewLayer: L.Rectangle | null = null;

  private map: L.Map | null = null;
  private fb: UntypedFormBuilder;

  // Lifecycle: unsubscribe all observables on destroy + tear down Leaflet map.
  private destroy$ = new Subject<void>();

  constructor(private apiService: ApiService, fb: UntypedFormBuilder, private zone: NgZone) {
    this.fb = fb;
    this.createForm();
    this.initLeafletOptions();
  }

  createForm() {
    this.angForm = this.fb.group({
      gps1lat: new UntypedFormControl(''),
      gps1long: new UntypedFormControl(''),
      gps2lat: new UntypedFormControl(''),
      gps2long: new UntypedFormControl(''),
      yearO: new UntypedFormControl('1989'),
      yearC: new UntypedFormControl('2018')
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
  }

  onMapReady(map: L.Map) {
    this.map = map;
    // Bewusst KEINE draw:created / draw:edited Handler — wir nutzen die
    // Leaflet.draw-Toolbar nicht mehr. Alles läuft über startDrawing() unten.
  }

  /**
   * Vom Button "Bereich markieren" im Template aufgerufen.
   * Aktiviert eigenen Drag-Selection-Modus:
   *  - Map-Panning + Doppelklick-Zoom temporär aus (sonst pannt die Map)
   *  - Cursor wird Crosshair
   *  - mousedown auf Map → Start-Punkt erfassen
   *  - mousemove → Vorschau-Rechteck wachsen lassen
   *  - mouseup → finales Rechteck in drawnItems, Form-Update, Mode beenden
   */
  startDrawing() {
    if (!this.map || this.isDrawing) {
      return;
    }
    this.isDrawing = true;

    // Map-Interaktionen pausieren — sonst pannt die Karte während wir ziehen
    this.map.dragging.disable();
    this.map.doubleClickZoom.disable();
    this.map.boxZoom.disable();
    this.map.getContainer().style.cursor = 'crosshair';

    this.map.on('mousedown', this.onDrawMouseDown);
  }

  private onDrawMouseDown = (e: L.LeafletMouseEvent) => {
    if (!this.map) {
      return;
    }
    this.startLatLng = e.latlng;
    // Vorhandenes Rechteck verwerfen — neue Selektion ersetzt alte.
    this.drawnItems.clearLayers();
    this.selectedBundesland = '';
    this.clearSelection();

    // Vorschau-Rechteck (degeneriert beim Start, wächst mit dem Drag)
    this.previewLayer = L.rectangle(
      L.latLngBounds(e.latlng, e.latlng),
      {
        color: '#dc3545',
        weight: 2,
        fillColor: '#dc3545',
        fillOpacity: 0.3,
        dashArray: '5, 5'
      }
    );
    this.previewLayer.addTo(this.map);

    this.map.on('mousemove', this.onDrawMouseMove);
    this.map.on('mouseup', this.onDrawMouseUp);
  };

  private onDrawMouseMove = (e: L.LeafletMouseEvent) => {
    if (!this.startLatLng || !this.previewLayer) {
      return;
    }
    this.previewLayer.setBounds(L.latLngBounds(this.startLatLng, e.latlng));
  };

  private onDrawMouseUp = (e: L.LeafletMouseEvent) => {
    if (!this.startLatLng || !this.previewLayer || !this.map) {
      this.stopDrawing();
      return;
    }

    const bounds = L.latLngBounds(this.startLatLng, e.latlng);

    // Preview entfernen — gleich kommt der finale, durchgezogen gerenderte Layer.
    this.map.removeLayer(this.previewLayer);
    this.previewLayer = null;
    this.startLatLng = null;

    // Tool-Mode beenden (Map-Interaktionen wieder an, Handler ab)
    this.stopDrawing();

    // Degenerate-Check: bei einem Klick ohne Drag haben wir nw == se.
    const nw = bounds.getNorthWest();
    const se = bounds.getSouthEast();
    if (nw.lat === se.lat && nw.lng === se.lng) {
      console.warn('analytics: Klick ohne Drag — kein Rechteck erstellt.');
      return;
    }

    // Finales Rechteck (durchgezogene Linie) anlegen und alle State syncen.
    // Wir kapseln das in NgZone.run, damit Angular die Change-Detection
    // anstößt (Leaflet-Events laufen außerhalb der Zone).
    this.zone.run(() => {
      const finalRect = L.rectangle(bounds, {
        color: '#dc3545',
        weight: 2,
        fillColor: '#dc3545',
        fillOpacity: 0.3
      });
      this.drawnItems.addLayer(finalRect);
      this.updateCoordsFromLayer(finalRect);
      this.enablePersistentEdit(finalRect);
    });
  };

  private stopDrawing() {
    this.isDrawing = false;
    if (!this.map) {
      return;
    }
    this.map.dragging.enable();
    this.map.doubleClickZoom.enable();
    this.map.boxZoom.enable();
    this.map.getContainer().style.cursor = '';
    this.map.off('mousedown', this.onDrawMouseDown);
    this.map.off('mousemove', this.onDrawMouseMove);
    this.map.off('mouseup', this.onDrawMouseUp);
  }

  /**
   * Aktiviert Leaflet.draw's per-layer editing direkt am Rectangle:
   * - Eckpunkte & Mitten-Marker werden sofort angezeigt
   * - User kann Ecken / ganze Box ziehen
   * - Bei jedem Drag-Ende werden die Form-Werte synchronisiert
   */
  private enablePersistentEdit(layer: any) {
    try {
      const editing = layer && layer.editing;
      if (editing && typeof editing.enable === 'function') {
        editing.enable();
      }
      if (layer && typeof layer.on === 'function') {
        layer.on('edit editdrag editmove', () => {
          this.zone.run(() => this.updateCoordsFromLayer(layer));
        });
      }
    } catch (e) {
      console.warn('analytics: enablePersistentEdit failed (non-fatal)', e);
    }
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
   * zu ziehen — robuster als sich auf die Form zu verlassen.
   */
  private getCurrentRectangle(): any {
    let found: any = null;
    this.drawnItems.eachLayer((l: any) => {
      if (!found && l && typeof l.getBounds === 'function') {
        found = l;
      }
    });
    return found;
  }

  private updateCoordsFromLayer(layer: any) {
    if (!layer || typeof layer.getBounds !== 'function') {
      return;
    }
    const bounds = layer.getBounds();
    if (!bounds || typeof bounds.getNorthWest !== 'function') {
      return;
    }
    const nw = bounds.getNorthWest();
    const se = bounds.getSouthEast();

    if (nw.lat === se.lat && nw.lng === se.lng) {
      console.warn('analytics: degenerate rectangle (NW == SE) — ignored.');
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

  onClickSubmit() {
    const v = this.angForm.value;
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
    this.destroy$.next();
    this.destroy$.complete();
    // Falls noch im Draw-Mode: aufräumen
    if (this.isDrawing) {
      this.stopDrawing();
    }
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
          this.zone.run(() => {
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
            this.enablePersistentEdit(rect);
            if (this.map) {
              this.map.fitBounds(rect.getBounds());
            }
          });
        },
        error: () => {
          alert('Could not load bounding box for: ' + selectedBundesland);
        }
      });
  }
}
