import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { I18nService } from './core/i18n/i18n.service';
import { ConfirmDialogComponent } from './core/ui/confirm-dialog.component';
import { PromptDialogComponent } from './core/ui/prompt-dialog.component';
import { ToastHostComponent } from './core/ui/toast-host.component';

/**
 * Root component: the router outlet plus global overlays (toasts, confirm and prompt dialogs).
 * المكوّن الجذري: مكان عرض الصفحات مع العناصر العامة (الإشعارات ونافذتا التأكيد والإدخال).
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHostComponent, ConfirmDialogComponent, PromptDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <app-toast-host />
    <app-confirm-dialog />
    <app-prompt-dialog />
  `,
})
export class App {
  // Injecting the service here creates it at startup, so <html lang dir> is set before the first page renders.
  // حقن الخدمة هنا ينشئها عند التشغيل، فيتحدد <html lang dir> قبل ما تظهر أول صفحة.
  protected readonly i18n = inject(I18nService);
}
