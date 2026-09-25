/** 与后端 API 对应的类型定义。金额单位：元；利率为小数（0.049 = 4.9%）。 */

export type RepaymentMethod = 'EQUAL_INSTALLMENT' | 'EQUAL_PRINCIPAL';

export const METHOD_LABELS: Record<RepaymentMethod, string> = {
  EQUAL_INSTALLMENT: '等额本息',
  EQUAL_PRINCIPAL: '等额本金',
};

export interface LoanContract {
  id: number;
  contractNo: string;
  borrowerName: string;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  createdAt: string;
}

export interface ScheduleRow {
  period: number;
  payment: number;
  principal: number;
  interest: number;
  balance: number;
}

export interface PlanSummary {
  periods: number;
  firstPayment: number;
  lastPayment: number;
  monthlyPayment: number | null;
  totalPrincipal: number;
  totalInterest: number;
  totalPayment: number;
  fee: number;
  totalCost: number;
}

export interface PlanResult {
  code: string;
  label: string;
  summary: PlanSummary;
  schedule: ScheduleRow[];
}

export interface DiffSummary {
  periodDiff: number;
  monthlyPaymentDiff: number | null;
  firstPaymentDiff: number;
  interestDiff: number;
  totalCostDiff: number;
}

export interface ComparisonResult {
  baseline: PlanSummary;
  shortenTerm: PlanResult;
  reducePayment: PlanResult;
  diff: DiffSummary;
}

export interface CalculationResponse {
  recordId: number;
  method: RepaymentMethod;
  comparison: ComparisonResult;
}

export interface CompareRequest {
  contractId?: number | null;
  method?: RepaymentMethod | null;
  annualRate?: number | null;
  remainingPrincipal?: number | null;
  remainingPeriods?: number | null;
  prepaymentAmount: number;
  fee: number;
}

export interface RecordSummaryView {
  id: number;
  createdAt: string;
  contractNo: string | null;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  prepaymentAmount: number;
  fee: number;
  shortenPeriods: number;
  reducePeriods: number;
  shortenTotalInterest: number;
  reduceTotalInterest: number;
  interestDiff: number;
}
