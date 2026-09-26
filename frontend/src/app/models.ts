/** 与后端 API 对应的类型定义。金额单位：元；利率为小数（0.049 = 4.9%）；日期为 yyyy-MM-dd。 */

export type RepaymentMethod = 'EQUAL_INSTALLMENT' | 'EQUAL_PRINCIPAL';

export const METHOD_LABELS: Record<RepaymentMethod, string> = {
  EQUAL_INSTALLMENT: '等额本息',
  EQUAL_PRINCIPAL: '等额本金',
};

/** 利率时间表中的一个利率段：自 effectiveDate（含）起生效。 */
export interface RateSegment {
  effectiveDate: string;
  annualRate: number;
}

/** 利率时间表视图；versionId/versionNo 为 null 表示非版本化快照。 */
export interface RateScheduleView {
  versionId: number | null;
  versionNo: number | null;
  createdAt: string | null;
  segments: RateSegment[];
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
  createdAt: string;
}

/** 某一期内按实际占用天数拆分出的一段计息来源。 */
export interface RatePart {
  from: string;
  to: string;
  days: number;
  annualRate: number;
}

export interface ScheduleRow {
  period: number;
  periodStart?: string;
  periodEnd?: string;
  payment: number;
  principal: number;
  interest: number;
  balance: number;
  /** 当期利率来源；历史旧记录可能为空。 */
  rateParts?: RatePart[] | null;
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
  scheduleStartDate: string;
  /** 计算时采用的利率时间表版本；手工录入为 null。 */
  rateVersionNo: number | null;
  /** 计算时实际采用的利率时间表快照。 */
  rateSchedule: RateSegment[];
  comparison: ComparisonResult;
}

export interface CompareRequest {
  contractId?: number | null;
  method?: RepaymentMethod | null;
  annualRate?: number | null;
  scheduleStartDate?: string | null;
  rateSchedule?: RateSegment[] | null;
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
  scheduleStartDate: string;
  rateVersionNo: number | null;
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
