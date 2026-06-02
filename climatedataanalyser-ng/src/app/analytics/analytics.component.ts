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

    // AC-F4 + AC-F6: rectangle only, danger-red style
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
      edit: {
        featureGroup: this.drawnItems
      }
    };
  }

  onMapReady(map: L.Map) {
    this.map = map;

    // AC-F5 + AC-F7 + AC-F8: draw:created → replace old rectangle
    this.map.on('draw:created', (event: any) => {
      this.drawnItems.clearLayers();
      const layer = event.layer;
      this.drawnItems.addLayer(layer);
      // Beim Zeichnen via Toolbar: bewusst auch das Dropdown leeren — der User
      // hat ja eine neue Region per Hand definiert, das alte Bundesland-Tag
      // gehört nicht mehr dazu (Convenience-Init ist überholt).
      this.selectedBundesland = '';
      this.updateCoordsFromLayer(layer);
    });

    // AC-F14: edit support — nur das (eine) Rectangle aktualisieren,
    // niemals via eachLayer auch Edit-Marker (das ist die degenerate-Box-Falle).
    this.map.on('draw:edited', (event: any) => {
      let edited: L.Rectangle | null = null;
      event.layers.eachLayer((layer: any) => {
        if (layer instanceof L.Rectangle && !edited) {
          edited = layer;
        }
      });
      if (edited) {
        this.updateCoordsFromLayer(edited);
      }
    });

    this.map.on('draw:deleted', () => {
      this.clearSelection();
    });
  }

  private clearSelection() {
    this.nwDisplay = null;
    this.seDisplay = null;
    this.angForm.patchValue({gps1lat: '', gps1long: '', gps2lat: '', gps2long: ''});
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
  // 1) Wenn ein Rechteck auf der Karte existiert → GPS senden (Bundesland leer).
  //    Das Rechteck ist die Wahrheit, auch wenn vorher per Bundesland-Dropdown vorgezeichnet.
  // 2) Wenn KEIN Rechteck, aber Bundesland gewählt → nur Bundesland senden (Server-side fallback).
  // Damit kollidieren Dropdown + Map nicht mehr (vorher: bundesland='' UND GPS=0,0 → ErrorMsg).
  onClickSubmit() {
    const v = this.angForm.value;
    const hasBoxNumeric =
      v.gps1lat !== '' && v.gps1long !== '' && v.gps2lat !== '' && v.gps2long !== '';

    const bundeslandParam = hasBoxNumeric ? '' : (this.selectedBundesland || '');
    const gps1 = hasBoxNumeric
      ? new GpsPoint(parseFloat(v.gps1long), parseFloat(v.gps1lat))
      : new GpsPoint(0, 0);
    const gps2 = hasBoxNumeric
      ? new GpsPoint(parseFloat(v.gps2long), parseFloat(v.gps2lat))
      : new GpsPoint(0, 0);

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
