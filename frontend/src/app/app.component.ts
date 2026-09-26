import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CompareFormComponent } from './compare-form/compare-form.component';
import { PlanResultComponent } from './plan-result/plan-result.component';
import { HistoryComponent } from './history/history.component';
import { LoanApiService } from './loan-api.service';
import { CompareRequest, ComparisonResult, RepaymentMethod } from './models';

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
  /** 当前展示结果计算时采用的利率版本与计划起始日。 */
  rateVersionNo: number | null = null;
  scheduleStartDate: string | null = null;
  loading = false;
  deriving = false;
  error = '';
  /** 每完成一次计算递增，通知历史记录刷新。 */
  historyRefresh = 0;

  onSubmit(req: CompareRequest): void {
    this.loading = true;
    this.error = '';
    this.api.compare(req).subscribe({
      next: (resp) => {
        this.applyResponse(resp.recordId, resp.method, resp.comparison, resp.rateVersionNo, resp.scheduleStartDate);
        this.loading = false;
        this.historyRefresh++;
      },
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
      next: (resp) => {
        this.applyResponse(resp.recordId, resp.method, resp.comparison, resp.rateVersionNo, resp.scheduleStartDate);
        this.loading = false;
      },
      error: () => {
        this.error = '历史记录加载失败';
        this.loading = false;
      },
    });
  }

  /** 以当前展示的记录为基础，按合同当前利率版本派生新计算。 */
  onDerive(): void {
    if (this.recordId == null) {
      return;
    }
    this.deriving = true;
    this.error = '';
    this.api.derive(this.recordId).subscribe({
      next: (resp) => {
        this.applyResponse(resp.recordId, resp.method, resp.comparison, resp.rateVersionNo, resp.scheduleStartDate);
        this.deriving = false;
        this.historyRefresh++;
      },
      error: (err) => {
        this.error = err?.error?.message ?? '派生计算失败';
        this.deriving = false;
      },
    });
  }

  private applyResponse(recordId: number, method: RepaymentMethod, comparison: ComparisonResult,
                        rateVersionNo: number | null, scheduleStartDate: string): void {
    this.recordId = recordId;
    this.method = method;
    this.comparison = comparison;
    this.rateVersionNo = rateVersionNo;
    this.scheduleStartDate = scheduleStartDate;
  }
}
