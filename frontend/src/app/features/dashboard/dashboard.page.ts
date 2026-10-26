import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ALL_STATUSES, Dashboard, ServiceRef } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { serviceName } from '../../core/masar';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * Office dashboard: month results, today's jobs and the two queues that need action
 * (requests waiting for a quote, quotes about to expire). One API call loads it all.
 * لوحة تحكم المكتب: نتائج الشهر وأعمال اليوم والقائمتين اللي تحتاج إجراء
 * (طلبات تنتظر عرض سعر، وعروض قربت تنتهي). طلب API واحد يحمّلها كلها.
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, TranslatePipe, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.page.html',
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  protected readonly data = rxResource({ stream: () => this.api.get<Dashboard>('/dashboard') });

  protected readonly openCount = computed(() => {
    const c = this.data.value()?.statusCounts;
    return c ? c.NEW + c.QUOTED + c.ACCEPTED + c.SCHEDULED + c.IN_PROGRESS : 0;
  });

  // Bars are scaled to the biggest status so the chart stays readable whatever the totals are.
  // الأعمدة تتناسب مع أكبر حالة حتى يبقى الرسم مقروء مهما كانت الأعداد.
  protected readonly statusBars = computed(() => {
    const counts = this.data.value()?.statusCounts;
    if (!counts) return [];
    const max = Math.max(1, ...ALL_STATUSES.map((s) => counts[s] ?? 0));
    return ALL_STATUSES.map((status) => ({
      status,
      count: counts[status] ?? 0,
      width: ((counts[status] ?? 0) / max) * 100,
    }));
  });

  protected readonly topMax = computed(() =>
    Math.max(1, ...(this.data.value()?.topServices ?? []).map((t) => t.count)),
  );

  protected name(service: ServiceRef): string {
    return serviceName(service, this.i18n.lang());
  }
}
