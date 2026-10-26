import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';

/**
 * Application-wide providers.
 * المزوّدات على مستوى التطبيق كامل.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Zoneless: change detection is driven by signals and template events instead of zone.js patches.
    // بدون zone.js: التحديث يعتمد على الـ signals وأحداث القوالب بدل ترقيع zone.js.
    provideZonelessChangeDetection(),
    // withComponentInputBinding: route params such as :id arrive directly as component inputs.
    // withComponentInputBinding: باراميترات الرابط مثل :id توصل مباشرة كـ input للمكوّن.
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
