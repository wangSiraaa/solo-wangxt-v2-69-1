import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CompareFormComponent } from './compare-form/compare-form.component';
import { PlanResultComponent } from './plan-result/plan-result.component';
import { HistoryComponent } from './history/history.component';
import { LoanApiService } from './loan-api.service';
import { CompareRequest, ComparisonResult, RecordSummaryView, RepaymentMethod } from './models';

/**
 * 工作台主界面：试算表单 + 两方案对比结果 + 历史记录。
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, CompareFormComponent, PlanResultComponent, HistoryComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  private readonly api = inject(LoanApiService);

  comparison: ComparisonResult | null = null;
  method: RepaymentMethod = 'EQUAL_INSTALLMENT';
  recordId: number | null = null;
  loading = false;
  error = '';
  /** 每完成一次计算递增，通知历史记录刷新。 */
  historyRefresh = 0;

  onSubmit(req: CompareRequest): void {
    this.loading = true;
    this.error = '';
    this.api.compare(req).subscribe({
      next: (resp) => this.applyResponse(resp.recordId, resp),
      error: (err) => {
        this.error = err?.error?.message ?? '计算失败，请检查输入或确认后端已启动';
        this.loading = false;
      },
    });
  }

  onViewRecord(id: number): void {
    this.loading = true;
    this.error = '';
    this.api.getRecord(id).subscribe({
      next: (resp) => this.applyResponse(resp.recordId, resp),
      error: () => {
        this.error = '历史记录加载失败';
        this.loading = false;
      },
    });
  }

  onDerive(event: { record: RecordSummaryView; useCurrent: boolean }): void {
    this.loading = true;
    this.error = '';
    this.api.derive(event.record.id, {
      prepaymentAmount: event.record.prepaymentAmount,
      fee: event.record.fee,
      prepaymentDate: event.record.prepaymentDate,
      useCurrentContractSchedule: event.useCurrent,
    }).subscribe({
      next: (resp) => this.applyResponse(resp.recordId, resp),
      error: (err) => {
        this.error = err?.error?.message ?? '派生计算失败';
        this.loading = false;
      },
    });
  }

  private applyResponse(id: number, resp: { method: RepaymentMethod; comparison: ComparisonResult }): void {
    this.comparison = resp.comparison;
    this.method = resp.method;
    this.recordId = id;
    this.loading = false;
    this.historyRefresh++;
  }
}
