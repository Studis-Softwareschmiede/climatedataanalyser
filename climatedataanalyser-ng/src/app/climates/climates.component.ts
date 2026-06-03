import {Component, OnDestroy, OnInit} from '@angular/core';
import {UntypedFormBuilder, UntypedFormControl, UntypedFormGroup} from '@angular/forms';
import {ApiService} from '../shared/api.service';
import {ClimateResponseDto} from './model/ClimateResponseDto';
import {HttpEventType} from '@angular/common/http';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

@Component({
  selector: 'app-climates', templateUrl: './climates.component.html', styleUrls: ['./climates.component.css']
})
export class ClimatesComponent implements OnInit, OnDestroy {

  bundeslaender: Array<string> | null = null;
  selectedBundesland: string = '';
  angForm!: UntypedFormGroup;
  climateResponseDto: ClimateResponseDto | null = null;
  private startYear: string = '';
  private distanceYear: string = '';
  private fb: UntypedFormBuilder;
  private zero: string = '0';

  private destroy$ = new Subject<void>();

  constructor(private apiService: ApiService, fb: UntypedFormBuilder) {
    this.fb = fb;
    this.createForm();
  }

  ngOnInit() {
    this.initClimates();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  reset() {
    this.bundeslaender = null;
    setTimeout(() => this.initClimates(), 1000);
    alert('Timeout');
  }

  onClickSubmit() {
    this.reset();

    this.apiService.getClimateRecords(
      ''
      , this.angForm.value.valueOf().gps1lat
      , this.angForm.value.valueOf().gps1long
      , this.angForm.value.valueOf().gps2lat
      , this.angForm.value.valueOf().gps2long
      , this.angForm.value.valueOf().startYear
      , this.angForm.value.valueOf().distanceYear)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (value) => {
          switch (value.type) {
            case HttpEventType.Response:
              this.climateResponseDto = value.body;
          }
        },
        error: () => {
          alert('An error occurred ,while getting climateRecords from Backend');
        }
      })
    ;
  }

  initClimates() {

    this.apiService.initAnalytics()
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {
        this.bundeslaender = value;
        console.log('Bundesland versucht zu laden !');
        console.log(this.bundeslaender);
      }, error => {
        alert('An error occurred while init Analytics, trying to get all Bundeslaender from Backend !');
      });

  }

  onBundeslaenderDropDownListSelected(selectedBundesland: string) {

    this.startYear = this.angForm.value.valueOf().startYear;
    this.distanceYear = this.angForm.value.valueOf().distanceYear;

    this.apiService.getClimateRecords(
      selectedBundesland
      , this.zero
      , this.zero
      , this.zero
      , this.zero
      , this.startYear
      , this.distanceYear)
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {

        switch (value.type) {
          case HttpEventType.Response:
            this.climateResponseDto = value.body;
        }


      }, error => {
        alert('An error occurred ,while getting analytics by Bundesland : ' + selectedBundesland);
      });
    selectedBundesland = 'The value ' + selectedBundesland + ' was selected !';

  }

  private createForm() {

    // Weisweil   79367 : 48.181837104192695, 7.6906623449884695
    // Stühlingen 79780 : 47.73683613454628, 8.360463439669749
    this.angForm = this.fb.group({
      gps1lat: new UntypedFormControl('48.181837104192695'),
      gps1long: new UntypedFormControl('7.6906623449884695'),
      gps2lat: new UntypedFormControl('47.73683613454628'),
      gps2long: new UntypedFormControl('8.360463439669749'),
      startYear: new UntypedFormControl('1900'),
      distanceYear: new UntypedFormControl('30')
    });
  }

}
