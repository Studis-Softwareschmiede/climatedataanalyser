import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-wolfgang',
  templateUrl: './wolfgang.component.html',
  styleUrls: ['./wolfgang.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WolfgangComponent implements OnInit {

  constructor() { }

  ngOnInit() {
  }

}
