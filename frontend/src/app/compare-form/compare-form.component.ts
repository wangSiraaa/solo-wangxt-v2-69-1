import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CompareRequest, LoanContract, METHOD_LABELS, RepaymentMethod } from '../models';
import { LoanApiService } from '../loan-api.service';

/**
 * 提前还款试算表单：选择模拟合同（自动带出合同参数）或手工录入，
 * 输入提前还款金额与手续费后提交对比计算。
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
  /** 提交计算请求。 */
  @Output() submitted = new EventEmitter<CompareRequest>();

  contracts: LoanContract[] = [];
  loadError = '';
  readonly methodLabels = METHOD_LABELS;

  readonly form = this.fb.group({
    contractId: this.fb.control<number | null>(null),
    method: this.fb.control<RepaymentMethod>('EQUAL_INSTALLMENT', { nonNullable: true }),
    annualRatePercent: this.fb.control<number | null>(4.9, [Validators.required, Validators.min(0), Validators.max(36)]),
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

  ngOnInit(): void {
    this.api.listContracts().subscribe({
      next: (contracts) => (this.contracts = contracts),
      error: () => (this.loadError = '模拟合同加载失败，请确认后端已启动'),
    });
    // 选择合同后，合同参数由后端取值，禁用手工录入控件（同时免于校验）
    const manual = ['method', 'annualRatePercent', 'remainingPrincipal', 'remainingPeriods'] as const;
    this.form.controls.contractId.valueChanges.subscribe((id) => {
      for (const name of manual) {
        const control = this.form.controls[name];
        id != null ? control.disable() : control.enable();
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
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
      // 界面按百分数录入（4.9），API 使用小数（0.049）
      req.annualRate = v.annualRatePercent! / 100;
      req.remainingPrincipal = v.remainingPrincipal;
      req.remainingPeriods = v.remainingPeriods;
    }
    this.submitted.emit(req);
  }
}
