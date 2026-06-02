import {Component, OnDestroy, OnInit} from '@angular/core';
import {HttpEventType} from '@angular/common/http';
import {ApiService} from '../shared/api.service';
import {AppInfoDto} from './model/AppInfoDto';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

@Component({
  selector: 'app-navigation',
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.css']
})
export class NavigationComponent implements OnInit, OnDestroy {

  appinfo: AppInfoDto | null = null;

  private destroy$ = new Subject<void>();

  constructor(private apiService: ApiService) {
  }

  ngOnInit() {

    this.apiService.appInfo()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (value) => {
          switch (value.type) {
            case HttpEventType.Response:
              this.appinfo = value.body;
          }
        },
        error: () => {
          alert('An error occurred ,while getting AppInfo from Backend!');
        }
      });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
