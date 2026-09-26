/** 与后端 API 对应的类型定义。金额单位：元；利率为小数（0.049 = 4.9%）。 */

export type RepaymentMethod = 'EQUAL_INSTALLMENT' | 'EQUAL_PRINCIPAL';

export const METHOD_LABELS: Record<RepaymentMethod, string> = {
  EQUAL_INSTALLMENT: '等额本息',
  EQUAL_PRINCIPAL: '等额本金',
};

export interface RateSegment {
  effectiveDate: string;
  annualRate: number;
}

export interface LoanContract {
  id: number;
  contractNo: string;
  borrowerName: string;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  scheduleStartDate: string;
  rateScheduleVersion: number;
  rateSegments: RateSegment[];
  createdAt: string;
}

export interface InterestBreakdown {
  segmentStart: string;
  segmentEnd: string;
  effectiveDate: string;
  annualRate: number;
  days: number;
  interest: number;
}

export interface ScheduleRow {
  period: number;
  startDate: string | null;
  dueDate: string | null;
  payment: number;
  principal: number;
  interest: number;
  balance: number;
  interestBreakdown: InterestBreakdown[];
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
  scheduleStartDate: string | null;
  rateScheduleVersion: number | null;
  rateScheduleSnapshot: RateSegment[] | null;
  prepaymentDate: string | null;
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
  scheduleStartDate?: string | null;
  rateSegments?: RateSegment[];
  prepaymentDate?: string | null;
  prepaymentAmount: number;
  fee: number;
}

export interface DeriveCalculationRequest {
  prepaymentAmount: number;
  fee: number;
  prepaymentDate?: string | null;
  useCurrentContractSchedule?: boolean;
}

export interface RecordSummaryView {
  id: number;
  createdAt: string;
  contractNo: string | null;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  scheduleStartDate: string | null;
  rateScheduleVersion: number;
  prepaymentDate: string | null;
  prepaymentAmount: number;
  fee: number;
  shortenPeriods: number;
  reducePeriods: number;
  shortenTotalInterest: number;
  reduceTotalInterest: number;
  interestDiff: number;
}
