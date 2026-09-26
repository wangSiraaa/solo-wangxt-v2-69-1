import { Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { METHOD_LABELS, RecordSummaryView } from '../models';
import { LoanApiService } from '../loan-api.service';

/**
 * 历史计算记录列表：展示每次试算的关键指标，可回查完整结果或基于快照/当前合同派生新试算。
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
  /** 基于历史记录发起新计算：false=复现快照，true=当前合同版本。 */
  @Output() deriveRecord = new EventEmitter<{ record: RecordSummaryView; useCurrent: boolean }>();

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
