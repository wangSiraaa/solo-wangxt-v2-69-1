import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ComparisonResult, PlanResult, RepaymentMethod } from '../models';

/**
 * 对比结果展示：差异说明 + 基准参考 + 两种方案的汇总卡片与逐期还款计划表。
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
}
