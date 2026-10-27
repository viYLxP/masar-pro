import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { startWith } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../core/api/api-error';
import { ApiService } from '../core/api/api.service';
import { AuthService } from '../core/auth/auth.service';
import {
  BookingCreated,
  CITIES,
  City,
  PROPERTY_TYPES,
  PropertyType,
  ServiceType,
  TIME_SLOTS,
  TimeSlot,
} from '../core/api/models';
import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import {
  SAUDI_MOBILE,
  SLOT_END_MIN,
  addDays,
  localPhone,
  riyadhMinutesNow,
  riyadhToday,
  serviceName,
} from '../core/masar';
import { FieldErrorComponent } from '../core/ui/field-error.component';

// Same limit as BookingService.MAX_DAYS_AHEAD on the server. / نفس حد BookingService.MAX_DAYS_AHEAD في الخادم.
const MAX_DAYS_AHEAD = 60;

function saudiMobile(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  return value === '' || SAUDI_MOBILE.test(value) ? null : { rule: 'Phone' };
}

/**
 * The booking form, shared by the public "Book a service" page and the office "New request" page.
 * نموذج الحجز، مشترك بين صفحة "احجز خدمة" العامة وصفحة "طلب جديد" في المكتب.
 *
 * The modes differ only in the endpoint (guest, signed-in customer or staff) and in a few labels; the rules
 * are the same, because the server applies the same checks to all of them. A signed-in customer books with
 * the account's mobile number, so that field is filled and locked.
 * الأوضاع تختلف بس في الرابط (زائر أو عميل مسجّل أو موظف) وبعض العناوين؛ والقواعد نفسها لأن الخادم يطبق
 * نفس الفحوص على الكل. والعميل المسجّل يحجز برقم جوال حسابه، فالحقل يتعبى ويتقفل.
 */
@Component({
  selector: 'app-booking-form',
  imports: [ReactiveFormsModule, TranslatePipe, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './booking-form.component.html',
})
export class BookingFormComponent {
  private readonly api = inject(ApiService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  private readonly auth = inject(AuthService);

  readonly mode = input<'public' | 'account' | 'office'>('public');
  // Pre-selected service, e.g. from "Book AC maintenance" on the home page (?service=3).
  // خدمة مختارة مسبقًا، مثل زر "احجز صيانة المكيفات" في الصفحة الرئيسية (?service=3).
  readonly serviceId = input<number | null>(null);
  readonly created = output<BookingCreated>();

  // Only active services can be booked, so both modes read the public (active-only) list.
  // الخدمات النشطة بس هي اللي تنحجز، فالوضعين يقرون القائمة العامة (النشطة فقط).
  protected readonly services = rxResource({
    stream: () => this.api.get<ServiceType[]>('/public/services'),
  });
  protected readonly cities = CITIES;
  protected readonly propertyTypes = PROPERTY_TYPES;
  protected readonly slots = TIME_SLOTS;
  protected readonly today = riyadhToday();
  protected readonly maxDate = addDays(this.today, MAX_DAYS_AHEAD);

  protected readonly form = inject(NonNullableFormBuilder).group({
    serviceTypeId: [null as number | null, Validators.required],
    propertyType: ['APARTMENT' as PropertyType, Validators.required],
    city: ['RIYADH' as City, Validators.required],
    district: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    address: ['', Validators.maxLength(300)],
    preferredDate: [this.today, [Validators.required, (c: AbstractControl) => this.dateRule(c)]],
    preferredSlot: [null as TimeSlot | null, Validators.required],
    details: ['', Validators.maxLength(2000)],
    customerName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    phone: ['', [Validators.required, saudiMobile, Validators.maxLength(20)]],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    // Honeypot: hidden from people, but bots fill every field. The server then pretends to accept.
    // مصيدة: مخفية عن الناس، لكن البوتات تعبّي كل الحقول. والخادم وقتها يتظاهر بالقبول.
    website: [''],
  });

  private readonly values = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );

  protected readonly selected = computed(() => {
    const id = Number(this.values().serviceTypeId);
    return (this.services.value() ?? []).find((s) => s.id === id) ?? null;
  });

  // Slots that already ended today cannot be picked (the server refuses them too).
  // الفترات اللي خلصت اليوم ما تنختار (والخادم يرفضها برضو).
  protected readonly passedSlots = computed(() => {
    if (this.values().preferredDate !== this.today) return new Set<TimeSlot>();
    const now = riyadhMinutesNow();
    return new Set(this.slots.filter((slot) => now >= SLOT_END_MIN[slot]));
  });

  protected readonly submitting = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  constructor() {
    // Account mode: the customer's own name, mobile and email. / وضع الحساب: اسم العميل وجواله وإيميله.
    effect(() => {
      const user = this.auth.user();
      if (this.mode() !== 'account' || !user) return;
      const c = this.form.controls;
      if (!c.customerName.dirty) c.customerName.setValue(user.fullName);
      if (!c.email.dirty) c.email.setValue(user.email ?? '');
      c.phone.setValue(localPhone(user.phone));
      c.phone.disable();
    });
    // Apply the pre-selected service once the list has loaded (only if it is really bookable).
    // نطبّق الخدمة المختارة مسبقًا بعد ما تحمل القائمة (بس لو فعلًا متاحة للحجز).
    effect(() => {
      const wanted = this.serviceId();
      const list = this.services.value();
      if (wanted && list?.some((s) => s.id === wanted) && !this.form.controls.serviceTypeId.dirty) {
        this.form.controls.serviceTypeId.setValue(wanted);
      }
    });
    // A slot that has just become "passed" (date changed to today) is cleared, so it cannot be submitted.
    // الفترة اللي صارت "منتهية" (تغير التاريخ لليوم) تنمسح، حتى ما تنرسل.
    effect(() => {
      const slot = this.form.controls.preferredSlot.value;
      if (slot && this.passedSlots().has(slot)) {
        this.form.controls.preferredSlot.setValue(null);
      }
    });
    // Editing a field hides its old server error. / تعديل الحقل يخفي خطأ الخادم القديم عليه.
    this.form.valueChanges.subscribe(() => {
      if (this.apiError()?.code === 'VALIDATION_FAILED') this.apiError.set(null);
    });
  }

  protected serviceName(service: ServiceType): string {
    return serviceName(service, this.i18n.lang());
  }

  private dateRule(control: AbstractControl): ValidationErrors | null {
    const value = String(control.value ?? '');
    if (!value) return null;
    // ISO dates compare correctly as strings. / تواريخ ISO تنقارن صح كنصوص.
    return value < this.today || value > this.maxDate ? { rule: 'DateRange' } : null;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Move focus to the first invalid field so keyboard and screen-reader users land on the problem.
      // ننقل التركيز لأول حقل غلط حتى مستخدم لوحة المفاتيح وقارئ الشاشة يوصل للمشكلة.
      queueMicrotask(() =>
        document
          .querySelector<HTMLElement>('app-booking-form .ng-invalid[formControlName]')
          ?.focus(),
      );
      return;
    }
    const v = this.form.getRawValue();
    const body = {
      serviceTypeId: Number(v.serviceTypeId),
      propertyType: v.propertyType,
      city: v.city,
      district: v.district.trim(),
      address: v.address.trim() || null,
      preferredDate: v.preferredDate,
      preferredSlot: v.preferredSlot,
      details: v.details.trim() || null,
      customerName: v.customerName.trim(),
      phone: v.phone.trim(),
      email: v.email.trim() || null,
      website: v.website,
    };
    const paths = { public: '/public/requests', account: '/account/requests', office: '/requests' };
    const path = paths[this.mode()];
    this.submitting.set(true);
    this.apiError.set(null);
    this.api.post<BookingCreated>(path, body).subscribe({
      next: (result) => {
        this.submitting.set(false);
        this.created.emit(result);
      },
      error: (err) => {
        this.submitting.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }
}
