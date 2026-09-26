import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RateSegment } from '../models';

interface EditorRow {
  effectiveDate: string;
  ratePercent: number | null;
}

interface TimelineBlock {
  left: number;
  width: number;
  color: string;
  label: string;
  title: string;
}

const DAY_MS = 24 * 60 * 60 * 1000;
const BLOCK_COLORS = ['#2563eb', '#0d9488', '#d97706', '#dc2626', '#7c3aed', '#475569'];

function parseDate(d: string): Date {
  return new Date(d + 'T00:00:00Z');
}

function fmtDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** 与 Java LocalDate.plusMonths 一致：月末日期向目标月最后一天钳位。 */
function addMonths(d: Date, n: number): Date {
  const day = d.getUTCDate();
  const r = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + n, 1));
  const daysInMonth = new Date(Date.UTC(r.getUTCFullYear(), r.getUTCMonth() + 1, 0)).getUTCDate();
  r.setUTCDate(Math.min(day, daysInMonth));
  return r;
}

function daysBetween(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / DAY_MS);
}

/** 百分数 → 小数利率（保留 6 位小数，与后端精度一致，避免浮点尾差）。 */
export function percentToRate(percent: number): number {
  return Math.round(percent * 10000) / 1000000;
}

/** 小数利率 → 百分数。 */
export function rateToPercent(rate: number): number {
  return Math.round(rate * 1000000) / 10000;
}

/**
 * 分段利率时间表编辑器：时间轴可视化 + 逐段编辑（生效日 + 年利率%）。
 * 客户端提示与后端一致的校验（同日重复 / 起始空档 / 范围与精度），后端为权威校验。
 */
@Component({
  selector: 'app-rate-schedule-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rate-schedule-editor.component.html',
  styleUrl: './rate-schedule-editor.component.css',
})
export class RateScheduleEditorComponent {
  /** 计划起始日（第 1 期计息起始日），用于空档校验与时间轴原点。 */
  @Input() startDate = '';
  /** 还款计划跨度（月），用于时间轴右端；空则按末段后延 12 个月。 */
  @Input() horizonMonths: number | null = null;
  /** 只读展示模式（隐藏编辑操作）。 */
  @Input() readonly = false;

  @Output() segmentsChange = new EventEmitter<RateSegment[]>();

  rows: EditorRow[] = [];
  private lastEmitted: RateSegment[] | null = null;

  @Input() set segments(value: RateSegment[] | null) {
    const segments = value ?? [];
    if (this.lastEmitted && this.sameSegments(segments, this.lastEmitted)) {
      return; // 自身发出的事件回环，不重建行以免打断输入
    }
    this.rows = segments.map((s) => ({
      effectiveDate: s.effectiveDate,
      ratePercent: rateToPercent(s.annualRate),
    }));
    if (this.rows.length === 0) {
      this.rows = [{ effectiveDate: this.startDate, ratePercent: null }];
    }
  }

  get sortedRows(): EditorRow[] {
    return [...this.rows].sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
  }

  /** 客户端校验（与后端规则一致；后端仍为权威校验）。 */
  get errors(): string[] {
    const errors: string[] = [];
    if (this.rows.length === 0) {
      errors.push('至少需要一个利率段');
      return errors;
    }
    const seen = new Set<string>();
    let firstDate: string | null = null;
    for (const row of this.rows) {
      if (!row.effectiveDate) {
        errors.push('利率段生效日不能为空');
        continue;
      }
      if (seen.has(row.effectiveDate)) {
        errors.push(`同一生效日存在多次利率调整: ${row.effectiveDate}`);
      }
      seen.add(row.effectiveDate);
      if (row.ratePercent == null || isNaN(row.ratePercent)) {
        errors.push(`生效日 ${row.effectiveDate} 的年利率不能为空`);
      } else {
        if (row.ratePercent < 0 || row.ratePercent > 36) {
          errors.push(`生效日 ${row.effectiveDate} 的年利率需在 0 ~ 36% 之间`);
        }
        if (this.decimalPlaces(row.ratePercent) > 4) {
          errors.push(`生效日 ${row.effectiveDate} 的年利率最多支持 4 位百分数小数（即利率 6 位小数）`);
        }
      }
      if (firstDate == null || row.effectiveDate < firstDate) {
        firstDate = row.effectiveDate;
      }
    }
    if (this.startDate && firstDate != null && firstDate > this.startDate) {
      errors.push(`利率时间表存在空档：首个利率段生效日 ${firstDate} 晚于计划起始日 ${this.startDate}`);
    }
    return [...new Set(errors)];
  }

  get valid(): boolean {
    return this.errors.length === 0;
  }

  /** 时间轴色块（按生效日排序后按比例布局）。 */
  get timelineBlocks(): TimelineBlock[] {
    const rows = this.sortedRows.filter((r) => r.effectiveDate && r.ratePercent != null);
    if (rows.length === 0 || !this.startDate) {
      return [];
    }
    const start = parseDate(this.startDate);
    const first = parseDate(rows[0].effectiveDate);
    const axisStart = first < start ? first : start;
    const last = parseDate(rows[rows.length - 1].effectiveDate);
    const axisEnd = this.horizonMonths != null
      ? addMonths(start, this.horizonMonths)
      : addMonths(last > start ? last : start, 12);
    const totalDays = daysBetween(axisStart, axisEnd);
    if (totalDays <= 0) {
      return [];
    }
    const blocks: TimelineBlock[] = [];
    for (let i = 0; i < rows.length; i++) {
      const from = parseDate(rows[i].effectiveDate);
      const rawTo = i + 1 < rows.length ? parseDate(rows[i + 1].effectiveDate) : axisEnd;
      const segFrom = from < axisStart ? axisStart : from;
      const segTo = rawTo > axisEnd ? axisEnd : rawTo;
      if (segTo <= segFrom) {
        continue; // 计划范围之外的段不在时间轴上占位
      }
      const left = (daysBetween(axisStart, segFrom) / totalDays) * 100;
      const width = (daysBetween(segFrom, segTo) / totalDays) * 100;
      blocks.push({
        left,
        width,
        color: BLOCK_COLORS[i % BLOCK_COLORS.length],
        label: `${rows[i].ratePercent}%`,
        title: `自 ${rows[i].effectiveDate} 起年利率 ${rows[i].ratePercent}%`,
      });
    }
    return blocks;
  }

  get axisStartLabel(): string {
    return this.startDate;
  }

  get axisEndLabel(): string {
    if (!this.startDate) {
      return '';
    }
    const start = parseDate(this.startDate);
    return fmtDate(this.horizonMonths != null ? addMonths(start, this.horizonMonths) : addMonths(start, 12));
  }

  addRow(): void {
    const sorted = this.sortedRows;
    const last = sorted[sorted.length - 1];
    const nextDate = last?.effectiveDate
      ? fmtDate(addMonths(parseDate(last.effectiveDate), 6))
      : this.startDate;
    this.rows = [...this.rows, { effectiveDate: nextDate, ratePercent: last?.ratePercent ?? null }];
    this.emit();
  }

  removeRow(index: number): void {
    this.rows = this.rows.filter((_, i) => i !== index);
    this.emit();
  }

  onRowChange(): void {
    this.emit();
  }

  private emit(): void {
    if (!this.valid) {
      return; // 无效时不向外发，父组件依据 errors 阻止提交
    }
    const segments: RateSegment[] = this.sortedRows.map((r) => ({
      effectiveDate: r.effectiveDate,
      annualRate: percentToRate(r.ratePercent!),
    }));
    this.lastEmitted = segments;
    this.segmentsChange.emit(segments);
  }

  private sameSegments(a: RateSegment[], b: RateSegment[]): boolean {
    return a.length === b.length
      && a.every((s, i) => s.effectiveDate === b[i].effectiveDate && s.annualRate === b[i].annualRate);
  }

  private decimalPlaces(value: number): number {
    const text = String(value);
    const dot = text.indexOf('.');
    return dot < 0 ? 0 : text.length - dot - 1;
  }
}
