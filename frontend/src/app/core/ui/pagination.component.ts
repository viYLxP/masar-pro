import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { IconComponent } from './icon.component';
import { TranslatePipe } from '../i18n/translate.pipe';

/**
 * Previous / next pager for server-side pagination. Page numbers are zero-based like the API.
 * أزرار السابق والتالي للتقسيم من جهة الخادم. أرقام الصفحات تبدأ من صفر مثل الـ API.
 */
@Component({
  selector: 'app-pagination',
  imports: [TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav
        class="pager"
        [attr.aria-label]="'common.pageOf' | t: { page: page() + 1, pages: totalPages() }"
      >
        <button
          type="button"
          class="btn btn--small"
          [disabled]="page() === 0"
          (click)="pageChange.emit(page() - 1)"
        >
          <app-icon name="chevron" class="icon--back" [flip]="true" /> {{ 'common.previous' | t }}
        </button>
        <span>{{ 'common.pageOf' | t: { page: page() + 1, pages: totalPages() } }}</span>
        <button
          type="button"
          class="btn btn--small"
          [disabled]="page() + 1 >= totalPages()"
          (click)="pageChange.emit(page() + 1)"
        >
          {{ 'common.next' | t }} <app-icon name="chevron" [flip]="true" />
        </button>
      </nav>
    }
  `,
})
export class PaginationComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();
}
