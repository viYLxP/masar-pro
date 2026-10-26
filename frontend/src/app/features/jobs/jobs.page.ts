import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { JobCard } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { localPhone, serviceName } from '../../core/masar';
import { IconComponent } from '../../core/ui/icon.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * The technician's own jobs, made for a phone: big cards with call and map buttons.
 * Starting and completing happen on the job's detail page, where the version check protects the update.
 * أعمال الفني نفسه، مصممة للجوال: بطاقات كبيرة فيها أزرار الاتصال والخريطة.
 * البدء والإنهاء يتمّون في صفحة تفاصيل الشغل، حيث فحص النسخة يحمي التحديث.
 */
@Component({
  selector: 'app-jobs',
  imports: [RouterLink, TranslatePipe, IconComponent, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'jobs.title' | t }}</h1>
        <p>{{ 'jobs.subtitle' | t }}</p>
      </div>
    </header>

    @if (jobs.error()) {
      <div class="alert alert--danger" role="alert">
        {{ 'app.loadFailed' | t }}
        <button type="button" class="btn btn--small" (click)="jobs.reload()">
          {{ 'app.retry' | t }}
        </button>
      </div>
    } @else if (jobs.isLoading() && !jobs.hasValue()) {
      <div class="loading" role="status"><span class="spinner"></span>{{ 'app.loading' | t }}</div>
    } @else {
      @for (group of groups(); track group.title) {
        @if (group.jobs.length) {
          <h2 class="section-title">{{ group.title | t }}</h2>
          <ul class="job-cards" data-testid="job-cards">
            @for (job of group.jobs; track job.id) {
              <li class="card job-card">
                <div class="job-card__head">
                  <span class="job-card__time"
                    >{{ fmt.date(job.scheduledStart) }} ·
                    {{ fmt.timeRange(job.scheduledStart, job.scheduledEnd) }}</span
                  >
                  <app-status-badge [status]="job.status" />
                </div>
                <h3>
                  <a [routerLink]="['/app/requests', job.id]" class="row-link">{{ name(job) }}</a>
                </h3>
                <p>
                  <bdi>{{ job.customerName }}</bdi> · <bdi>{{ job.district }}</bdi
                  >,
                  {{ 'city.' + job.city | t }}
                </p>
                @if (job.address) {
                  <p class="muted" dir="auto">{{ job.address }}</p>
                }
                @if (job.details) {
                  <p class="job-card__details" dir="auto">{{ job.details }}</p>
                }
                <div class="job-card__actions">
                  <a class="btn btn--small" [href]="'tel:+' + job.phone">
                    <app-icon name="phone" /> <span dir="ltr">{{ phone(job.phone) }}</span>
                  </a>
                  <a
                    class="btn btn--small"
                    [href]="mapLink(job)"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <app-icon name="pin" /> {{ 'jobs.navigate' | t }}
                  </a>
                  <a class="btn btn--small btn--primary" [routerLink]="['/app/requests', job.id]">{{
                    'jobs.open' | t
                  }}</a>
                </div>
              </li>
            }
          </ul>
        }
      } @empty {
        <p class="empty-state">{{ 'jobs.empty' | t }}</p>
      }
    }
  `,
})
export class JobsPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  protected readonly jobs = rxResource({ stream: () => this.api.get<JobCard[]>('/jobs/mine') });

  // Open work first (the server already sorts by time), then what was finished today.
  // الشغل المفتوح أول (الخادم مرتّبه بالوقت)، ثم اللي انتهى اليوم.
  protected readonly groups = computed(() => {
    const all = this.jobs.value() ?? [];
    if (all.length === 0) return [];
    return [
      { title: 'jobs.upcoming', jobs: all.filter((j) => j.status !== 'COMPLETED') },
      { title: 'jobs.doneToday', jobs: all.filter((j) => j.status === 'COMPLETED') },
    ];
  });

  protected readonly phone = localPhone;

  protected name(job: JobCard): string {
    return serviceName(job.service, this.i18n.lang());
  }

  // We store no coordinates, so the map searches the written address. / ما نخزّن إحداثيات، فالخريطة تبحث بالعنوان المكتوب.
  protected mapLink(job: JobCard): string {
    const city = this.i18n.t(`city.${job.city}`);
    const query = [job.address, job.district, city].filter(Boolean).join(', ');
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;
  }
}
