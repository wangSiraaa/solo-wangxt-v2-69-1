import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CompareRequest,
  LoanContract,
  METHOD_LABELS,
  RateSegment,
  RepaymentMethod,
} from '../models';
import { ContractPayload, LoanApiService } from '../loan-api.service';

/**
 * 提前还款试算表单：选择模拟合同（自动带出合同参数）或手工录入，
 * 提供按生效日排列的利率时间表编辑，并输入提前还款金额与手续费。
 */
@Component({
  selector: 'app-compare-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './compare-form.component.html',
  styleUrl: './compare-form.component.css',
})
export class CompareFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(LoanApiService);

  /** 父组件传入：是否正在计算（禁用提交按钮）。 */
  @Input() loading = false;
  /** 合同利率表保存后请求刷新合同列表。 */
  @Output() contractsChanged = new EventEmitter<void>();
  /** 提交计算请求。 */
  @Output() submitted = new EventEmitter<CompareRequest>();

  contracts: LoanContract[] = [];
  loadError = '';
  saveError = '';
  saveSuccess = '';
  saving = false;
  segmentDraft = this.fb.group({
    date: ['', Validators.required],
    ratePercent: [null as number | null, [Validators.required, Validators.min(0), Validators.max(36)]],
  });
  /** 选中合同时间轴上的临时编辑；保存前不影响已发布版本。 */
  editingSegments: RateSegment[] = [];
  editingStartDate = '';
  prepaymentDate = '2025-01-01';
  touchedTimeline = false;

  readonly methodLabels = METHOD_LABELS;

  readonly form = this.fb.group({
    contractId: this.fb.control<number | null>(null),
    method: this.fb.control<RepaymentMethod>('EQUAL_INSTALLMENT', { nonNullable: true }),
    annualRatePercent: this.fb.control<number | null>(4.9, [Validators.required, Validators.min(0), Validators.max(36)]),
    startDate: this.fb.control('2025-01-01', Validators.required),
    remainingPrincipal: this.fb.control<number | null>(1000000, [Validators.required, Validators.min(0.01)]),
    remainingPeriods: this.fb.control<number | null>(240, [Validators.required, Validators.min(1), Validators.max(600)]),
    prepaymentAmount: this.fb.control<number | null>(200000, [Validators.required, Validators.min(0.01)]),
    fee: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)]),
  });

  get useContract(): boolean {
    return this.form.controls.contractId.value != null;
  }

  get selectedContract(): LoanContract | undefined {
    return this.contracts.find((c) => c.id === this.form.controls.contractId.value);
  }

  get timelineError(): string {
    if (!this.touchedTimeline) {
      return '';
    }
    const segments = this.currentSegments();
    const start = this.currentStartDate();
    if (!start) {
      return '请选择模拟开始日期';
    }
    if (segments.length === 0) {
      return '至少维护一个利率段';
    }
    if (segments.some((s) => !s.effectiveDate || Number.isNaN(s.annualRate))) {
      return '利率段的生效日和年利率必须完整';
    }
    const sorted = [...segments].sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].effectiveDate === sorted[i - 1].effectiveDate) {
        return `同一日期不能多次调息：${sorted[i].effectiveDate}`;
      }
    }
    if (sorted[0].effectiveDate > start) {
      return '首个利率段必须不晚于模拟开始日期，否则存在空档';
    }
    if (!this.prepaymentDate || this.prepaymentDate < start) {
      return '提前还款日不能早于模拟开始日期';
    }
    if (this.prepaymentDate.slice(8, 10) !== start.slice(8, 10)) {
      return '提前还款日必须是某个账期起始日（日号需与起始日一致）';
    }
    return '';
  }

  ngOnInit(): void {
    this.loadContracts();
    // 选择合同后，合同参数由后端取值，禁用手工录入控件（同时免于校验）
    const manual = ['method', 'annualRatePercent', 'remainingPrincipal', 'remainingPeriods'] as const;
    this.form.controls.contractId.valueChanges.subscribe((id) => {
      for (const name of manual) {
        const control = this.form.controls[name];
        id != null ? control.disable() : control.enable();
      }
      this.beginContractEdit();
    });
    this.form.controls.startDate.valueChanges.subscribe(() => {
      if (!this.useContract) {
        this.touchedTimeline = true;
      }
    });
    this.form.controls.annualRatePercent.valueChanges.subscribe(() => {
      if (!this.useContract) {
        this.touchedTimeline = true;
      }
    });
  }

  loadContracts(selectId?: number): void {
    this.api.listContracts().subscribe({
      next: (contracts) => {
        this.contracts = contracts;
        this.loadError = '';
        if (selectId != null) {
          this.form.controls.contractId.setValue(selectId);
        } else {
          this.beginContractEdit();
        }
      },
      error: () => (this.loadError = '模拟合同加载失败，请确认后端已启动'),
    });
  }

  syncManualInput(): void {
    if (!this.useContract) {
      this.syncManualSegment();
      this.touchedTimeline = true;
    }
  }

  addSegment(): void {
    const d = this.segmentDraft.getRawValue();
    if (this.segmentDraft.invalid || !d.date || d.ratePercent == null) {
      this.segmentDraft.markAllAsTouched();
      return;
    }
    this.currentSegments().push({ effectiveDate: d.date, annualRate: d.ratePercent / 100 });
    this.currentSegments().sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
    this.touchedTimeline = true;
    this.saveSuccess = '';
    this.segmentDraft.reset({ date: '', ratePercent: null });
  }

  removeSegment(date: string, rate: number): void {
    const segments = this.currentSegments();
    const index = segments.findIndex((s) => s.effectiveDate === date && s.annualRate === rate);
    if (index >= 0) {
      segments.splice(index, 1);
      this.touchedTimeline = true;
      this.saveSuccess = '';
    }
  }

  saveContractSchedule(): void {
    const contract = this.selectedContract;
    this.touchedTimeline = true;
    if (!contract || this.timelineError || this.saving) {
      return;
    }
    const payload: ContractPayload = {
      contractNo: contract.contractNo,
      borrowerName: contract.borrowerName,
      method: contract.method,
      annualRate: null,
      remainingPrincipal: contract.remainingPrincipal,
      remainingPeriods: contract.remainingPeriods,
      scheduleStartDate: this.editingStartDate,
      rateSegments: this.sortedSegments(this.editingSegments),
    };
    this.saving = true;
    this.saveError = '';
    this.api.updateContract(contract.id, payload).subscribe({
      next: () => {
        this.saving = false;
        this.saveSuccess = `已保存为利率时间表新版本（v${contract.rateScheduleVersion + 1}）；历史计算仍保留其计算时快照。`;
        this.contractsChanged.emit();
        this.loadContracts(contract.id);
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err?.error?.message ?? '利率时间表保存失败';
      },
    });
  }

  submit(): void {
    this.touchedTimeline = true;
    if (this.form.invalid || this.timelineError) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const segments = this.sortedSegments(this.currentSegments());
    const startDate = this.currentStartDate()!;
    const req: CompareRequest = {
      prepaymentAmount: v.prepaymentAmount!,
      fee: v.fee!,
      // 当前模型只支持期初提前还款；事件顺序：先冲本金，再按同日新利率起息。
      prepaymentDate: this.prepaymentDate,
      scheduleStartDate: startDate,
    };
    if (v.contractId != null) {
      req.contractId = v.contractId;
    } else {
      req.method = v.method;
      req.annualRate = segments[0]?.annualRate ?? v.annualRatePercent! / 100;
      req.remainingPrincipal = v.remainingPrincipal;
      req.remainingPeriods = v.remainingPeriods;
      req.rateSegments = segments;
    }
    this.submitted.emit(req);
  }

  setPrepaymentDate(event: Event): void {
    this.prepaymentDate = (event.target as HTMLInputElement).value;
  }

  private beginContractEdit(): void {
    const contract = this.selectedContract;
    if (contract) {
      this.editingStartDate = contract.scheduleStartDate ?? this.form.controls.startDate.value ?? '2025-01-01';
      this.prepaymentDate = this.editingStartDate;
      this.editingSegments = (contract.rateSegments?.length
        ? contract.rateSegments
        : [{ effectiveDate: this.editingStartDate, annualRate: contract.annualRate }])
        .map((s) => ({ ...s }));
    } else {
      this.syncManualSegment();
    }
    this.touchedTimeline = false;
    this.saveSuccess = '';
    this.saveError = '';
  }

  private syncManualSegment(): void {
    const start = this.form.controls.startDate.value ?? '';
    const ratePercent = this.form.controls.annualRatePercent.value;
    this.editingStartDate = start;
    this.prepaymentDate = start;
    if (this.editingSegments.length === 1) {
      this.editingSegments[0].effectiveDate = start;
    }
    if (this.editingSegments.length <= 1 && ratePercent != null) {
      this.editingSegments = [{ effectiveDate: start, annualRate: ratePercent / 100 }];
    }
  }

  private currentSegments(): RateSegment[] {
    return this.useContract ? this.editingSegments : this.editingSegments;
  }

  private currentStartDate(): string {
    return this.useContract ? this.editingStartDate : (this.form.controls.startDate.value ?? '');
  }

  private sortedSegments(segments: RateSegment[]): RateSegment[] {
    return [...segments].sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
  }
}
