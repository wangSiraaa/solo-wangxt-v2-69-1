import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CalculationResponse,
  CompareRequest,
  LoanContract,
  RateScheduleView,
  RateSegment,
  RecordSummaryView,
} from './models';

@Injectable({ providedIn: 'root' })
export class LoanApiService {
  private readonly http = inject(HttpClient);

  listContracts(): Observable<LoanContract[]> {
    return this.http.get<LoanContract[]>('/api/contracts');
  }

  /** 合同当前利率时间表（最新版本）。 */
  getRateSchedule(contractId: number): Observable<RateScheduleView> {
    return this.http.get<RateScheduleView>(`/api/contracts/${contractId}/rate-schedule`);
  }

  /** 合同利率时间表全部版本（新到旧）。 */
  listRateScheduleVersions(contractId: number): Observable<RateScheduleView[]> {
    return this.http.get<RateScheduleView[]>(`/api/contracts/${contractId}/rate-schedule/versions`);
  }

  /** 整体替换利率时间表（保存为新版本）。 */
  saveRateSchedule(contractId: number, segments: RateSegment[]): Observable<RateScheduleView> {
    return this.http.put<RateScheduleView>(`/api/contracts/${contractId}/rate-schedule`, { segments });
  }

  compare(req: CompareRequest): Observable<CalculationResponse> {
    return this.http.post<CalculationResponse>('/api/calculations/compare', req);
  }

  /** 由历史记录派生新计算（采用合同当前利率版本）。 */
  derive(recordId: number): Observable<CalculationResponse> {
    return this.http.post<CalculationResponse>(`/api/calculations/${recordId}/derive`, {});
  }

  listRecords(): Observable<RecordSummaryView[]> {
    return this.http.get<RecordSummaryView[]>('/api/calculations');
  }

  getRecord(id: number): Observable<CalculationResponse> {
    return this.http.get<CalculationResponse>(`/api/calculations/${id}`);
  }
}
