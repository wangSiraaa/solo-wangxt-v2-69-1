import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CalculationResponse,
  CompareRequest,
  LoanContract,
  RecordSummaryView,
} from './models';

@Injectable({ providedIn: 'root' })
export class LoanApiService {
  private readonly http = inject(HttpClient);

  listContracts(): Observable<LoanContract[]> {
    return this.http.get<LoanContract[]>('/api/contracts');
  }

  compare(req: CompareRequest): Observable<CalculationResponse> {
    return this.http.post<CalculationResponse>('/api/calculations/compare', req);
  }

  listRecords(): Observable<RecordSummaryView[]> {
    return this.http.get<RecordSummaryView[]>('/api/calculations');
  }

  getRecord(id: number): Observable<CalculationResponse> {
    return this.http.get<CalculationResponse>(`/api/calculations/${id}`);
  }
}
