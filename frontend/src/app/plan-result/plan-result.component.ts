import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ComparisonResult, InterestBreakdown, PlanResult, RepaymentMethod, ScheduleRow } from '../models';

/**
 * 对比结果展示：差异说明 + 基准参考 + 两种方案的汇总卡片、利率版本与逐期利率来源。
 */
@Component({
  selector: 'app-plan-result',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './plan-result.component.html',
  styleUrl: './plan-result.component.css',
})
export class PlanResultComponent {
  @Input({ required: true }) result!: ComparisonResult;
  @Input({ required: true }) method!: RepaymentMethod;

  expandedRows = new Set<string>();

  /** 等额本息有固定月供；等额本金月供逐月递减。 */
  get isInstallment(): boolean {
    return this.method === 'EQUAL_INSTALLMENT';
  }

  get plans(): PlanResult[] {
    return [this.result.shortenTerm, this.result.reducePayment];
  }

  trackPlan(_: number, plan: PlanResult): string {
    return plan.code;
  }

  toggle(plan: PlanResult, row: ScheduleRow): void {
    const key = `${plan.code}-${row.period}`;
    if (this.expandedRows.has(key)) {
      this.expandedRows.delete(key);
    } else {
      this.expandedRows.add(key);
    }
  }

  isOpen(plan: PlanResult, row: ScheduleRow): boolean {
    return this.expandedRows.has(`${plan.code}-${row.period}`);
  }

  sourceText(row: ScheduleRow): string {
    return (row.interestBreakdown ?? [])
      .map((s) => `${s.effectiveDate} 起 ${(s.annualRate * 100).toFixed(6)}%/${s.days}天`)
      .join('；');
  }

  breakdownTotal(items: InterestBreakdown[]): number {
    return items.reduce((sum, item) => sum + item.interest, 0);
  }
}
