import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ComparisonResult, PlanResult, RepaymentMethod, ScheduleRow } from '../models';

/**
 * 对比结果展示：差异说明 + 基准参考 + 两种方案的汇总卡片与逐期还款计划表。
 * 逐期表包含计息区间与利率来源（跨调息日的期展示分段明细）。
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

  /** 月供是否在计划中途被重算（利率调整导致），决定汇总卡片展示「月供」还是「首期月供」。 */
  paymentVaries(plan: PlanResult): boolean {
    if (!this.isInstallment) {
      return true;
    }
    const first = plan.schedule[0]?.payment;
    return plan.schedule.slice(0, -1).some((row) => row.payment !== first);
  }

  /** 当期利率来源：单段显示利率，跨调息日显示「利率×天数」分段明细。 */
  rateSource(row: ScheduleRow): string {
    const parts = row.rateParts;
    if (!parts || parts.length === 0) {
      return '—';
    }
    if (parts.length === 1) {
      return `${this.percent(parts[0].annualRate)}%`;
    }
    return parts.map((p) => `${this.percent(p.annualRate)}%×${p.days}天`).join(' ＋ ');
  }

  rateSourceTitle(row: ScheduleRow): string {
    const parts = row.rateParts;
    if (!parts || parts.length === 0) {
      return '';
    }
    return parts
      .map((p) => `${p.from} ~ ${p.to}（${p.days} 天）按年利率 ${this.percent(p.annualRate)}%`)
      .join('；');
  }

  isSplit(row: ScheduleRow): boolean {
    return (row.rateParts?.length ?? 0) > 1;
  }

  private percent(rate: number): string {
    return (Math.round(rate * 1000000) / 10000).toString();
  }
}
