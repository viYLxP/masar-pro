import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';

/**
 * The customer-facing layout: header with the main links and language switch, then the page, then the footer.
 * تخطيط الموقع العام للعملاء: رأس فيه الروابط وتبديل اللغة، ثم الصفحة، ثم التذييل.
 */
@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main">{{ 'app.skipToContent' | t }}</a>
    <header class="site-header">
      <div class="site-header__inner">
        <a class="brand" routerLink="/" data-testid="brand">
          <span class="logo" aria-hidden="true">M</span>
          <span class="brand__text">
            <strong>{{ 'app.name' | t }}</strong>
            <small>{{ 'app.tagline' | t }}</small>
          </span>
        </a>
        <!-- Outside the collapsible menu, so switching language is one tap on phones too. -->
        <!-- خارج القائمة القابلة للطي، حتى تبديل اللغة يكون بضغطة وحدة في الجوال برضو. -->
        <button
          type="button"
          class="btn btn--small btn--ghost lang-switch site-header__lang"
          (click)="i18n.toggle()"
          [attr.aria-label]="'app.switchToLabel' | t"
          [attr.lang]="i18n.lang() === 'ar' ? 'en' : 'ar'"
          data-testid="lang-switch"
        >
          <app-icon name="globe" /> {{ 'app.switchTo' | t }}
        </button>
        <button
          type="button"
          class="icon-btn site-header__toggle"
          (click)="menuOpen.set(!menuOpen())"
          [attr.aria-label]="(menuOpen() ? 'nav.closeMenu' : 'nav.menu') | t"
          aria-controls="site-nav"
          [attr.aria-expanded]="menuOpen()"
        >
          <app-icon [name]="menuOpen() ? 'x' : 'menu'" />
        </button>
        <nav
          id="site-nav"
          class="site-nav"
          [class.site-nav--open]="menuOpen()"
          [attr.aria-label]="'site.nav' | t"
        >
          <a
            routerLink="/"
            routerLinkActive="is-active"
            [routerLinkActiveOptions]="{ exact: true }"
            ariaCurrentWhenActive="page"
            >{{ 'site.home' | t }}</a
          >
          <a routerLink="/track" routerLinkActive="is-active" ariaCurrentWhenActive="page">{{
            'site.track' | t
          }}</a>
          <!-- Guests sign in or sign up; a customer gets their account; staff get back to their app. -->
          <!-- الزائر يدخل أو يسجّل؛ والعميل يروح لحسابه؛ والموظف يرجع لتطبيقه. -->
          @if (auth.isCustomer()) {
            <a
              routerLink="/account"
              routerLinkActive="is-active"
              ariaCurrentWhenActive="page"
              data-testid="nav-account"
            >
              <app-icon name="user" /> {{ 'site.account' | t }}
            </a>
          } @else if (auth.user()) {
            <a routerLink="/app" class="site-nav__staff">
              <app-icon name="login" [flip]="true" /> {{ 'site.staff' | t }}
            </a>
          } @else {
            <a routerLink="/login" class="site-nav__staff" data-testid="nav-login">
              <app-icon name="login" [flip]="true" /> {{ 'site.signIn' | t }}
            </a>
            <a routerLink="/register" data-testid="nav-register">{{ 'site.register' | t }}</a>
          }
          <a class="btn btn--accent" routerLink="/book" data-testid="nav-book">{{
            'site.book' | t
          }}</a>
        </nav>
      </div>
    </header>

    <main id="main" class="site-main" tabindex="-1">
      <router-outlet />
    </main>

    <footer class="site-footer">
      <div class="site-footer__inner">
        <div>
          <strong>{{ 'app.name' | t }}</strong>
          <p>{{ 'site.cities' | t }}</p>
          <p>{{ 'site.hours' | t }}</p>
        </div>
        <nav [attr.aria-label]="'site.nav' | t" class="site-footer__links">
          <a routerLink="/book">{{ 'site.book' | t }}</a>
          <a routerLink="/track">{{ 'site.track' | t }}</a>
          <a routerLink="/login">{{ 'site.staff' | t }}</a>
        </nav>
      </div>
      <p class="site-footer__legal">{{ 'site.footer' | t: { year: year } }}</p>
    </footer>
  `,
})
export class PublicLayoutComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly auth = inject(AuthService);
  protected readonly menuOpen = signal(false);
  protected readonly year = new Date().getFullYear();

  constructor() {
    // After a reload, find out who the saved token belongs to, so the header shows "My account".
    // بعد إعادة التحميل، نعرف لمين الرمز المحفوظ، حتى الرأس يعرض "حسابي".
    void this.auth.restore();
    // Close the mobile menu after each navigation. / نقفل قائمة الجوال بعد كل تنقل.
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
  }
}
