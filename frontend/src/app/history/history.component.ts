import { Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { METHOD_LABELS, RecordSummaryView } from '../models';
import { LoanApiService } from '../loan-api.service';

/**
 * 历史计算记录列表：展示每次试算的关键指标，可回查完整结果。
 */
@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './history.component.html',
  styleUrl: './history.component.css',
})
export class HistoryComponent implements OnInit, OnChanges {
  private readonly api = inject(LoanApiService);

  /** 每次计算完成后由父组件递增，触发刷新。 */
  @Input() refreshToken = 0;
  /** 点击「查看」时发出记录 ID。 */
  @Output() viewRecord = new EventEmitter<number>();

  records: RecordSummaryView[] = [];
  readonly methodLabels = METHOD_LABELS;

  ngOnInit(): void {
    this.reload();
  }

  ngOnChanges(): void {
    this.reload();
  }

  private reload(): void {
    this.api.listRecords().subscribe({
      next: (records) => (this.records = records),
      error: () => (this.records = []),
    });
  }
}
