import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { startWith } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { DayBoard, RequestDetail, User } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import {
  SLOT_END_MIN,
  addDays,
  riyadhMinutesNow,
  riyadhMinutesOf,
  riyadhToday,
  serviceName,
  startTimes,
} from '../../core/masar';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { DayTimelineComponent, Proposal } from '../../shared/day-timeline.component';

const SLOT_START: Record<string, string> = {
  MORNING: '08:00',
  AFTERNOON: '12:00',
  EVENING: '16:00',
};

function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
}

/**
 * Books (or re-books) a technician: pick who, which day and the start time, while seeing that day's
 * board for the qualified technicians. The server makes the final decision (skills, city, hours, overlaps).
 * يحجز (أو يعيد حجز) فني: تختار مين وأي يوم ووقت البداية، وتشوف لوحة ذاك اليوم للفنيين المؤهلين.
 * والقرار النهائي للخادم (المهارات، المدينة، الساعات، التعارض).
 */
@Component({
  selector: 'app-schedule-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, FieldErrorComponent, DayTimelineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './schedule-dialog.component.html',
})
export class ScheduleDialogComponent {
  private readonly api = inject(ApiService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly request = input.required<RequestDetail>();
  readonly booked = output<RequestDetail>();
  readonly conflict = output<void>();

  protected readonly today = riyadhToday();
  protected readonly form = inject(NonNullableFormBuilder).group({
    technicianId: [null as number | null, Validators.required],
    date: [this.today, Validators.required],
    startTime: ['08:00', Validators.required],
  });
  private readonly values = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );

  protected readonly isOpen = signal(false);

  // Only technicians who can actually take this job (active, same city, has the skill).
  // بس الفنيين اللي فعلًا يقدرون ياخذون الشغل (نشط، نفس المدينة، عنده المهارة).
  protected readonly technicians = rxResource({
    params: () => (this.isOpen() ? this.request() : undefined),
    stream: ({ params }) =>
      this.api.get<User[]>('/technicians', { city: params.city, serviceTypeId: params.service.id }),
  });

  protected readonly board = rxResource({
    params: () => {
      const date = this.values().date;
      return this.isOpen() && date ? { date, request: this.request() } : undefined;
    },
    stream: ({ params }) =>
      this.api.get<DayBoard>('/schedule', {
        date: params.date,
        city: params.request.city,
        serviceTypeId: params.request.service.id,
      }),
  });

  // Start times that let the visit finish by 20:00; today, only those still ahead of now.
  // أوقات البداية اللي تخلي الزيارة تخلص قبل 8 الليل؛ واليوم، بس اللي لسا ما جت.
  protected readonly times = computed(() => {
    const all = startTimes(this.request().durationMinutes);
    if (this.values().date !== this.today) return all;
    const now = riyadhMinutesNow();
    return all.filter((t) => toMinutes(t) > now);
  });

  protected readonly endTime = computed(() => {
    const start = this.values().startTime;
    if (!start) return '';
    const end = toMinutes(start) + this.request().durationMinutes;
    return `${String(Math.floor(end / 60)).padStart(2, '0')}:${String(end % 60).padStart(2, '0')}`;
  });

  protected readonly proposal = computed<Proposal | null>(() => {
    const v = this.values();
    if (!v.technicianId || !v.startTime) return null;
    const startMin = toMinutes(v.startTime);
    return {
      technicianId: Number(v.technicianId),
      startMin,
      endMin: startMin + this.request().durationMinutes,
    };
  });

  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  protected readonly serviceLabel = computed(() =>
    serviceName(this.request().service, this.i18n.lang()),
  );

  open(): void {
    const r = this.request();
    let date: string;
    let start: string;
    if (r.scheduledStart) {
      // Rescheduling: start from the current booking. / إعادة جدولة: نبدأ من الحجز الحالي.
      date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Riyadh' }).format(
        new Date(r.scheduledStart),
      );
      const m = riyadhMinutesOf(r.scheduledStart);
      start = `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    } else {
      // First booking: the customer's preferred day and slot, unless that time has already passed.
      // أول حجز: اليوم والفترة اللي يفضلها العميل، إلا لو فات وقتها.
      const preferredPassed =
        r.preferredDate < this.today ||
        (r.preferredDate === this.today && riyadhMinutesNow() >= SLOT_END_MIN[r.preferredSlot]);
      date = preferredPassed ? addDays(this.today, 1) : r.preferredDate;
      start = SLOT_START[r.preferredSlot];
    }
    this.apiError.set(null);
    this.form.reset({ technicianId: r.technician?.id ?? null, date, startTime: start });
    this.isOpen.set(true);
    this.dialog().nativeElement.showModal();
    // If the default time is not offered (too late today / too long a visit), pick the first one that is.
    // لو الوقت الافتراضي مو ضمن الخيارات (متأخر اليوم أو الزيارة طويلة)، نختار أول وقت متاح.
    queueMicrotask(() => this.fixStartTime());
  }

  protected fixStartTime(): void {
    const times = this.times();
    if (!times.includes(this.form.controls.startTime.value)) {
      this.form.controls.startTime.setValue(times[0] ?? '');
    }
  }

  protected close(): void {
    this.isOpen.set(false);
    this.dialog().nativeElement.close();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.apiError.set(null);
    this.api
      .post<RequestDetail>(`/requests/${this.request().id}/schedule`, {
        technicianId: Number(v.technicianId),
        date: v.date,
        startTime: v.startTime,
        version: this.request().version,
      })
      .subscribe({
        next: (detail) => {
          this.saving.set(false);
          this.close();
          this.booked.emit(detail);
        },
        error: (err) => {
          this.saving.set(false);
          const error = toApiError(err);
          this.apiError.set(error);
          // A clash found by the server: refresh the board so the dispatcher sees the new job.
          // تعارض لقاه الخادم: نحدّث اللوحة حتى يشوف المنسّق الشغل الجديد.
          if (error.code === 'TECHNICIAN_BUSY') this.board.reload();
          if (error.code === 'CONCURRENT_UPDATE') {
            this.close();
            this.conflict.emit();
          }
        },
      });
  }
}
