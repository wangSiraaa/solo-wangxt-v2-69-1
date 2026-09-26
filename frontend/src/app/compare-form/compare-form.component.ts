import { Component, EventEmitter, Input, OnInit, Output, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CompareRequest,
  LoanContract,
  METHOD_LABELS,
  RateScheduleView,
  RateSegment,
  RepaymentMethod,
} from '../models';
import { LoanApiService } from '../loan-api.service';
import { RateScheduleEditorComponent } from '../rate-schedule-editor/rate-schedule-editor.component';

/**
 * 提前还款试算表单：选择模拟合同（自动带出合同参数与利率时间表）或手工录入，
 * 输入提前还款金额与手续费后提交对比计算。
 * 合同模式下可编辑利率时间表并保存为新版本；手工模式下直接编辑本次试算用时间表。
 */
@Component({
  selector: 'app-compare-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RateScheduleEditorComponent],
  templateUrl: './compare-form.component.html',
  styleUrl: './compare-form.component.css',
})
export class CompareFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(LoanApiService);

  /** 父组件传入：是否正在计算（禁用提交按钮）。 */
  @Input() loading = false;
  /** 提交计算请求。 */
  @Output() submitted = new EventEmitter<CompareRequest>();

  @ViewChild('manualEditor') manualEditor?: RateScheduleEditorComponent;
  @ViewChild('contractEditor') contractEditor?: RateScheduleEditorComponent;

  contracts: LoanContract[] = [];
  loadError = '';
  readonly methodLabels = METHOD_LABELS;

  /** 手工录入模式下的利率时间表（本次试算用）。 */
  manualSegments: RateSegment[] = [];
  /** 合同当前利率时间表（含版本信息）。 */
  contractSchedule: RateScheduleView | null = null;
  /** 合同时间表编辑后的暂存段。 */
  contractSegments: RateSegment[] = [];
  editingSchedule = false;
  savingSchedule = false;
  scheduleMessage = '';
  scheduleError = '';

  readonly form = this.fb.group({
    contractId: this.fb.control<number | null>(null),
    method: this.fb.control<RepaymentMethod>('EQUAL_INSTALLMENT', { nonNullable: true }),
    scheduleStartDate: this.fb.control<string>(this.today(), { nonNullable: true }),
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

  get scheduleInvalid(): boolean {
    const editor = this.useContract ? this.contractEditor : this.manualEditor;
    return this.useContract ? false : (editor != null && !editor.valid);
  }

  ngOnInit(): void {
    this.manualSegments = [{ effectiveDate: this.form.controls.scheduleStartDate.value, annualRate: 0.049 }];
    this.api.listContracts().subscribe({
      next: (contracts) => (this.contracts = contracts),
      error: () => (this.loadError = '模拟合同加载失败，请确认后端已启动'),
    });
    // 选择合同后，合同参数由后端取值，禁用手工录入控件（同时免于校验）
    const manual = ['method', 'scheduleStartDate', 'remainingPrincipal', 'remainingPeriods'] as const;
    this.form.controls.contractId.valueChanges.subscribe((id) => {
      for (const name of manual) {
        const control = this.form.controls[name];
        id != null ? control.disable() : control.enable();
      }
      this.editingSchedule = false;
      this.scheduleMessage = '';
      this.scheduleError = '';
      this.contractSchedule = null;
      if (id != null) {
        this.loadContractSchedule(id);
      }
    });
  }

  loadContractSchedule(contractId: number): void {
    this.api.getRateSchedule(contractId).subscribe({
      next: (view) => {
        this.contractSchedule = view;
        this.contractSegments = view.segments;
      },
      error: () => (this.scheduleError = '利率时间表加载失败'),
    });
  }

  startEditSchedule(): void {
    this.editingSchedule = true;
    this.scheduleMessage = '';
    this.scheduleError = '';
  }

  cancelEditSchedule(): void {
    this.editingSchedule = false;
    if (this.contractSchedule) {
      this.contractSegments = this.contractSchedule.segments;
    }
  }

  saveSchedule(): void {
    const contract = this.selectedContract;
    if (!contract || this.contractEditor == null || !this.contractEditor.valid) {
      return;
    }
    this.savingSchedule = true;
    this.scheduleMessage = '';
    this.scheduleError = '';
    this.api.saveRateSchedule(contract.id, this.contractSegments).subscribe({
      next: (view) => {
        this.contractSchedule = view;
        this.contractSegments = view.segments;
        this.editingSchedule = false;
        this.savingSchedule = false;
        this.scheduleMessage = `已保存为新版本 v${view.versionNo}；后续试算将采用该版本，历史记录不受影响。`;
      },
      error: (err) => {
        this.scheduleError = err?.error?.message ?? '利率时间表保存失败';
        this.savingSchedule = false;
      },
    });
  }

  submit(): void {
    if (this.form.invalid || this.scheduleInvalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const req: CompareRequest = {
      prepaymentAmount: v.prepaymentAmount!,
      fee: v.fee!,
    };
    if (v.contractId != null) {
      req.contractId = v.contractId;
    } else {
      req.method = v.method;
      req.scheduleStartDate = v.scheduleStartDate;
      req.rateSchedule = this.manualSegments;
      req.remainingPrincipal = v.remainingPrincipal;
      req.remainingPeriods = v.remainingPeriods;
    }
    this.submitted.emit(req);
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
