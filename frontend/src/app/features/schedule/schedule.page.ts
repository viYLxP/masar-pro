import { ChangeDetectionStrategy, Component, inject, input, linkedSignal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { CITIES, DayBoard, ServiceType } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { addDays, riyadhToday, serviceName } from '../../core/masar';
import { IconComponent } from '../../core/ui/icon.component';
import { DayTimelineComponent } from '../../shared/day-timeline.component';

/**
 * The day board for dispatchers: every technician's jobs for one day, filterable by city and service.
 * لوحة اليوم للمنسّقين: أعمال كل فني في يوم واحد، مع فلترة بالمدينة والخدمة.
 */
@Component({
  selector: 'app-schedule',
  imports: [TranslatePipe, IconComponent, DayTimelineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'schedule.title' | t }}</h1>
        <p>{{ 'schedule.subtitle' | t }}</p>
      </div>
    </header>

    <div class="toolbar">
      <div class="day-nav">
        <button
          type="button"
          class="icon-btn"
          (click)="go(-1)"
          [attr.aria-label]="'schedule.previous' | t"
        >
          <app-icon name="chevron" class="icon--back" [flip]="true" />
        </button>
        <label>
          <span class="visually-hidden">{{ 'schedule.date' | t }}</span>
          <input
            type="date"
            [value]="day()"
            (change)="setDay($any($event.target).value)"
            data-testid="schedule-date"
          />
        </label>
        <button
          type="button"
          class="icon-btn"
          (click)="go(1)"
          [attr.aria-label]="'schedule.next' | t"
        >
          <app-icon name="chevron" [flip]="true" />
        </button>
        <button type="button" class="btn btn--small" (click)="setDay(today)">
          {{ 'common.today' | t }}
        </button>
      </div>
      <label>
        <span class="visually-hidden">{{ 'common.city' | t }}</span>
        <select (change)="setFilter('city', $any($event.target).value)">
          <option value="" [selected]="!city()">{{ 'common.anyCity' | t }}</option>
          @for (c of cities; track c) {
            <option [value]="c" [selected]="city() === c">{{ 'city.' + c | t }}</option>
          }
        </select>
      </label>
      <label>
        <span class="visually-hidden">{{ 'common.service' | t }}</span>
        <select (change)="setFilter('service', $any($event.target).value)">
          <option value="" [selected]="!service()">{{ 'common.anyService' | t }}</option>
          @for (s of services.value() ?? []; track s.id) {
            <option [value]="s.id" [selected]="service() === '' + s.id">{{ name(s) }}</option>
          }
        </select>
      </label>
    </div>

    <h2 class="day-title">{{ fmt.longDay(day()) }}</h2>

    @if (board.error()) {
      <div class="alert alert--danger" role="alert">
        {{ 'app.loadFailed' | t }}
        <button type="button" class="btn btn--small" (click)="board.reload()">
          {{ 'app.retry' | t }}
        </button>
      </div>
    } @else if (board.isLoading() && !board.hasValue()) {
      <div class="loading" role="status"><span class="spinner"></span>{{ 'app.loading' | t }}</div>
    } @else if (board.value(); as b) {
      @if (b.technicians.length) {
        <div class="card" [attr.aria-busy]="board.isLoading()">
          <app-day-timeline [technicians]="b.technicians" />
        </div>
      } @else {
        <p class="empty-state">{{ 'schedule.noTechnicians' | t }}</p>
      }
    }
  `,
})
export class SchedulePage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  readonly date = input<string>();
  readonly city = input<string>();
  readonly service = input<string>();

  protected readonly today = riyadhToday();
  protected readonly cities = CITIES;
  // An invalid ?date= falls back to today instead of failing the API call.
  // ‎?date= غير الصالح يرجع لليوم بدل ما يفشل طلب الـ API.
  protected readonly day = linkedSignal(() => {
    const d = this.date();
    return d && /^\d{4}-\d{2}-\d{2}$/.test(d) ? d : this.today;
  });

  protected readonly services = rxResource({
    stream: () => this.api.get<ServiceType[]>('/services'),
  });
  protected readonly board = rxResource({
    params: () => ({
      date: this.day(),
      city: this.city() ?? '',
      serviceTypeId: this.service() ?? '',
    }),
    stream: ({ params }) => this.api.get<DayBoard>('/schedule', params),
  });

  protected go(days: number): void {
    this.setDay(addDays(this.day(), days));
  }

  protected setDay(value: string): void {
    if (value) this.setFilter('date', value === this.today ? '' : value);
  }

  protected setFilter(key: string, value: string): void {
    void this.router.navigate([], {
      queryParams: { [key]: value || null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected name(service: ServiceType): string {
    return serviceName(service, this.i18n.lang());
  }
}
