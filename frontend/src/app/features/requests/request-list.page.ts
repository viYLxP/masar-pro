import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

import { ApiService } from '../../core/api/api.service';
import {
  ALL_STATUSES,
  CITIES,
  Page,
  RequestSummary,
  ServiceRef,
  ServiceType,
  User,
} from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { localPhone, serviceName } from '../../core/masar';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * All requests with search and filters. The filters live in the URL (?status=NEW…), so links from the
 * dashboard open the right list and the browser's back button restores it.
 * كل الطلبات مع البحث والفلاتر. الفلاتر محفوظة في الرابط (‎?status=NEW…)، فروابط لوحة التحكم
 * تفتح القائمة الصحيحة وزر الرجوع في المتصفح يرجّعها.
 */
@Component({
  selector: 'app-request-list',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './request-list.page.html',
})
export class RequestListPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  // Query parameters arrive as inputs (withComponentInputBinding). / باراميترات الرابط توصل كـ inputs.
  readonly q = input<string>();
  readonly status = input<string>();
  readonly city = input<string>();
  readonly service = input<string>();
  readonly technician = input<string>();
  readonly page = input<string>();

  // linkedSignal: follows the URL, but the user can change it locally until the URL changes again.
  // linkedSignal: يتبع الرابط، والمستخدم يقدر يغيّره محليًا لين يتغير الرابط مرة ثانية.
  protected readonly searchText = linkedSignal(() => this.q() ?? '');

  protected readonly statuses = ALL_STATUSES;
  protected readonly cities = CITIES;
  protected readonly services = rxResource({
    stream: () => this.api.get<ServiceType[]>('/services'),
  });
  protected readonly technicians = rxResource({
    stream: () => this.api.get<User[]>('/technicians'),
  });

  protected readonly requests = rxResource({
    params: () => ({
      q: this.q() ?? '',
      status: this.status() ?? '',
      city: this.city() ?? '',
      serviceTypeId: this.service() ?? '',
      technicianId: this.technician() ?? '',
      page: Number(this.page() ?? 0) || 0,
    }),
    stream: ({ params }) =>
      this.api.get<Page<RequestSummary>>('/requests', { ...params, size: 20 }),
  });

  private readonly typing = new Subject<string>();
  protected readonly filtersOpen = signal(false);

  constructor() {
    // Wait for a pause in typing before searching, so we do not call the server on every key.
    // ننتظر توقف الكتابة قبل البحث، حتى ما نكلم الخادم مع كل حرف.
    this.typing
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe((q) => this.setFilter('q', q));
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    this.typing.next(value.trim());
  }

  /** Writes one filter to the URL and goes back to the first page. / يكتب فلتر واحد في الرابط ويرجع للصفحة الأولى. */
  protected setFilter(key: string, value: string): void {
    void this.router.navigate([], {
      queryParams: { [key]: value || null, page: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { page: page || null },
      queryParamsHandling: 'merge',
    });
  }

  protected clear(): void {
    this.searchText.set('');
    void this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  protected hasFilters(): boolean {
    return !!(this.q() || this.status() || this.city() || this.service() || this.technician());
  }

  protected name(service: ServiceRef): string {
    return serviceName(service, this.i18n.lang());
  }

  protected readonly phone = localPhone;
}
