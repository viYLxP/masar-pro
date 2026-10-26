import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormArray, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { startWith } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { RequestDetail } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { addDays, riyadhToday, serviceName } from '../../core/masar';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';

// Same limits as the server (QuoteRequest and RequestService.MAX_QUOTE_DAYS).
// نفس حدود الخادم (QuoteRequest و RequestService.MAX_QUOTE_DAYS).
const MAX_LINES = 20;
const MAX_QUOTE_DAYS = 60;
const DEFAULT_VALID_DAYS = 7;

/**
 * Creates or edits the draft quote of a NEW request: item lines, validity and notes, with live totals.
 * ينشئ أو يعدّل مسودة عرض السعر لطلب جديد: البنود والصلاحية والملاحظات، مع مجاميع مباشرة.
 *
 * The totals here are a preview only. The server recalculates them (VAT 15%, rounded once) and stores
 * what it computed, so a tampered browser cannot change the price.
 * المجاميع هنا معاينة فقط. الخادم يعيد حسابها (ضريبة 15% مقرّبة مرة وحدة) ويحفظ اللي حسبه،
 * فالمتصفح المعدّل ما يقدر يغيّر السعر.
 */
@Component({
  selector: 'app-quote-editor',
  imports: [ReactiveFormsModule, TranslatePipe, IconComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quote-editor.component.html',
})
export class QuoteEditorComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  readonly request = input.required<RequestDetail>();
  readonly saved = output<RequestDetail>();
  readonly cancelled = output<void>();
  // A save that fails because someone else changed the request asks the page to reload it.
  // الحفظ اللي يفشل لأن أحد ثاني عدّل الطلب يطلب من الصفحة تعيد تحميله.
  readonly conflict = output<void>();

  protected readonly today = riyadhToday();
  protected readonly maxDate = addDays(this.today, MAX_QUOTE_DAYS);
  protected readonly maxLines = MAX_LINES;

  protected readonly form = this.fb.group({
    validUntil: [addDays(this.today, DEFAULT_VALID_DAYS), Validators.required],
    notes: ['', Validators.maxLength(1000)],
    lines: this.fb.array([this.line()]),
  });

  private readonly values = toSignal(this.form.valueChanges.pipe(startWith(null)));

  // Work in halalas (1/100 SAR) so 0.1 + 0.2 style float errors never show up in money.
  // نحسب بالهللة (1/100 ريال) حتى ما تطلع أخطاء الكسور العشرية في المبالغ.
  protected readonly totals = computed(() => {
    this.values();
    const subtotal = this.form.controls.lines.controls.reduce((sum, line) => {
      const qty = Math.max(0, Math.trunc(Number(line.controls.quantity.value) || 0));
      const price = Math.max(0, Math.round((Number(line.controls.unitPrice.value) || 0) * 100));
      return sum + qty * price;
    }, 0);
    // Half-up rounding of 15%, like the server's Money.vat. / تقريب 15% للأعلى عند النص، مثل Money.vat في الخادم.
    const vat = Math.floor((subtotal * 15 + 50) / 100);
    return { subtotal: subtotal / 100, vat: vat / 100, total: (subtotal + vat) / 100 };
  });

  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  ngOnInit(): void {
    const quote = this.request().quote;
    if (quote) {
      // Editing an existing draft: start from its saved lines.
      // تعديل مسودة موجودة: نبدأ من بنودها المحفوظة.
      this.form.controls.lines.clear();
      for (const l of quote.lines) {
        this.form.controls.lines.push(this.line(l.description, l.quantity, l.unitPrice));
      }
      this.form.patchValue({
        // An old draft may carry a date that has passed; move it to a valid default instead.
        // المسودة القديمة ممكن تحمل تاريخ فات؛ ننقله لقيمة افتراضية صالحة.
        validUntil:
          quote.validUntil >= this.today
            ? quote.validUntil
            : addDays(this.today, DEFAULT_VALID_DAYS),
        notes: quote.notes ?? '',
      });
    } else {
      // A new quote starts with one line named after the service at its starting price.
      // العرض الجديد يبدأ ببند واحد باسم الخدمة وسعرها المبدئي.
      this.form.controls.lines.at(0).patchValue({
        description: serviceName(this.request().service, this.i18n.lang()),
      });
    }
  }

  protected get lines(): FormArray {
    return this.form.controls.lines;
  }

  private line(description = '', quantity = 1, unitPrice: number | null = null) {
    return this.fb.group({
      description: [
        description,
        [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)],
      ],
      quantity: [quantity, [Validators.required, Validators.min(1), Validators.max(999)]],
      unitPrice: [
        unitPrice as number | null,
        [Validators.required, Validators.min(0), Validators.max(99999999.99)],
      ],
    });
  }

  protected addLine(): void {
    if (this.lines.length < MAX_LINES) {
      this.form.controls.lines.push(this.line());
      // Focus the new item's description so typing can continue straight away.
      // نركّز على وصف البند الجديد حتى يكمل الكتابة مباشرة.
      const index = this.lines.length - 1;
      queueMicrotask(() => document.getElementById(`q-desc-${index}`)?.focus());
    }
  }

  protected removeLine(index: number): void {
    if (this.lines.length > 1) this.form.controls.lines.removeAt(index);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = {
      validUntil: v.validUntil,
      notes: v.notes.trim() || null,
      version: this.request().version,
      lines: v.lines.map((l) => ({
        description: l.description.trim(),
        quantity: Number(l.quantity),
        unitPrice: Number(l.unitPrice),
      })),
    };
    this.saving.set(true);
    this.apiError.set(null);
    this.api.put<RequestDetail>(`/requests/${this.request().id}/quote`, body).subscribe({
      next: (detail) => {
        this.saving.set(false);
        this.saved.emit(detail);
      },
      error: (err) => {
        this.saving.set(false);
        const error = toApiError(err);
        this.apiError.set(error);
        if (error.code === 'CONCURRENT_UPDATE') this.conflict.emit();
      },
    });
  }
}
