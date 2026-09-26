import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CalculationResponse,
  CompareRequest,
  DeriveCalculationRequest,
  LoanContract,
  RateSegment,
  RecordSummaryView,
  RepaymentMethod,
} from './models';

export interface ContractPayload {
  contractNo: string;
  borrowerName: string;
  method: RepaymentMethod;
  annualRate: number | null;
  remainingPrincipal: number;
  remainingPeriods: number;
  scheduleStartDate: string;
  rateSegments: RateSegment[];
}

@Injectable({ providedIn: 'root' })
export class LoanApiService {
  private readonly http = inject(HttpClient);

  listContracts(): Observable<LoanContract[]> {
    return this.http.get<LoanContract[]>('/api/contracts');
  }

  updateContract(id: number, payload: ContractPayload): Observable<LoanContract> {
    return this.http.put<LoanContract>(`/api/contracts/${id}`, payload);
  }

  compare(req: CompareRequest): Observable<CalculationResponse> {
    return this.http.post<CalculationResponse>('/api/calculations/compare', req);
  }

  derive(id: number, req: DeriveCalculationRequest): Observable<CalculationResponse> {
    return this.http.post<CalculationResponse>(`/api/calculations/${id}/derive`, req);
  }

  listRecords(): Observable<RecordSummaryView[]> {
    return this.http.get<RecordSummaryView[]>('/api/calculations');
  }

  getRecord(id: number): Observable<CalculationResponse> {
    return this.http.get<CalculationResponse>(`/api/calculations/${id}`);
  }
}
