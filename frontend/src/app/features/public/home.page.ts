import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ServiceType } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { serviceIcon } from '../../core/masar';
import { IconComponent } from '../../core/ui/icon.component';

/**
 * Public home page: what we do, the live service catalogue (from the database), and how booking works.
 * الصفحة الرئيسية العامة: وش نسوي، وقائمة الخدمات الحية (من قاعدة البيانات)، وطريقة الحجز.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <div class="hero__inner">
        <h1>{{ 'site.heroTitle' | t }}</h1>
        <p>{{ 'site.heroText' | t }}</p>
        <div class="hero__actions">
          <a class="btn btn--accent btn--large" routerLink="/book" data-testid="hero-book">{{
            'site.heroCta' | t
          }}</a>
          <a class="btn btn--outline-light btn--large" routerLink="/track">{{
            'site.heroTrack' | t
          }}</a>
        </div>
      </div>
    </section>

    <section class="section" aria-labelledby="services-title">
      <div class="section__inner">
        <h2 id="services-title">{{ 'site.servicesTitle' | t }}</h2>
        <p class="section__lead">{{ 'site.servicesText' | t }}</p>
        @if (services.error()) {
          <div class="alert alert--danger" role="alert">
            {{ 'app.loadFailed' | t }}
            <button type="button" class="btn btn--small" (click)="services.reload()">
              {{ 'app.retry' | t }}
            </button>
          </div>
        } @else if (services.isLoading() && !services.hasValue()) {
          <div class="loading" role="status">
            <span class="spinner"></span>{{ 'app.loading' | t }}
          </div>
        } @else {
          <ul class="service-grid" data-testid="service-grid">
            @for (s of services.value() ?? []; track s.id) {
              <li class="service-card">
                <span class="service-card__icon"><app-icon [name]="icon(s.code)" /></span>
                <h3>{{ i18n.lang() === 'ar' ? s.nameAr : s.nameEn }}</h3>
                <p>{{ i18n.lang() === 'ar' ? s.descriptionAr : s.descriptionEn }}</p>
                <p class="service-card__meta">
                  <strong>{{ 'site.from' | t: { price: fmt.money(s.basePrice) } }}</strong>
                  <span>{{
                    'site.duration' | t: { duration: fmt.duration(s.durationMinutes) }
                  }}</span>
                </p>
                <a
                  class="btn btn--primary btn--block"
                  routerLink="/book"
                  [queryParams]="{ service: s.id }"
                  >{{
                    'site.bookThis' | t: { name: i18n.lang() === 'ar' ? s.nameAr : s.nameEn }
                  }}</a
                >
              </li>
            } @empty {
              <li class="empty-state">{{ 'site.noServices' | t }}</li>
            }
          </ul>
        }
      </div>
    </section>

    <section class="section section--alt" aria-labelledby="how-title">
      <div class="section__inner">
        <h2 id="how-title">{{ 'site.howTitle' | t }}</h2>
        <ol class="steps">
          <li>
            <span class="steps__num" aria-hidden="true">1</span>
            <h3>{{ 'site.step1Title' | t }}</h3>
            <p>{{ 'site.step1Text' | t }}</p>
          </li>
          <li>
            <span class="steps__num" aria-hidden="true">2</span>
            <h3>{{ 'site.step2Title' | t }}</h3>
            <p>{{ 'site.step2Text' | t }}</p>
          </li>
          <li>
            <span class="steps__num" aria-hidden="true">3</span>
            <h3>{{ 'site.step3Title' | t }}</h3>
            <p>{{ 'site.step3Text' | t }}</p>
          </li>
        </ol>
      </div>
    </section>

    <section class="section" aria-labelledby="why-title">
      <div class="section__inner">
        <h2 id="why-title">{{ 'site.whyTitle' | t }}</h2>
        <ul class="why">
          <li><app-icon name="receipt" /> {{ 'site.why1' | t }}</li>
          <li><app-icon name="shield" /> {{ 'site.why2' | t }}</li>
          <li><app-icon name="clock" /> {{ 'site.why3' | t }}</li>
        </ul>
      </div>
    </section>
  `,
})
export class HomePage {
  private readonly api = inject(ApiService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  protected readonly services = rxResource({
    stream: () => this.api.get<ServiceType[]>('/public/services'),
  });
  protected readonly icon = serviceIcon;
}
