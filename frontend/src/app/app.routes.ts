import { Routes } from '@angular/router';

import {
  adminGuard,
  customerGuard,
  guestGuard,
  homeRedirect,
  officeGuard,
  staffGuard,
  technicianGuard,
} from './core/auth/guards';
import { ShellComponent } from './layout/shell.component';
import { PublicLayoutComponent } from './layout/public-layout.component';

/**
 * Two apps in one: the public site at "/" (guests, plus "/account" for signed-in customers) and the staff app
 * under "/app". Feature pages are lazy-loaded, so a customer booking a service never downloads the staff screens.
 * تطبيقين في واحد: الموقع العام على "/" (للزوار، و"/account" للعملاء المسجّلين) وتطبيق الموظفين تحت "/app".
 * الصفحات تُحمّل عند الحاجة، فالعميل اللي يحجز خدمة ما يحمّل شاشات الموظفين أبدًا.
 */
export const routes: Routes = [
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./features/public/home.page').then((m) => m.HomePage),
      },
      {
        path: 'book',
        loadComponent: () => import('./features/public/book.page').then((m) => m.BookPage),
      },
      {
        path: 'track',
        loadComponent: () => import('./features/public/track.page').then((m) => m.TrackPage),
      },
      {
        // Open to guests and customers: whoever has the link of a pending payment can pay it.
        // مفتوحة للزوار والعملاء: اللي معه رابط دفعة معلّقة يقدر يدفعها.
        path: 'pay/:id',
        loadComponent: () => import('./features/pay/pay.page').then((m) => m.PayPage),
      },
      {
        path: 'account',
        canActivate: [customerGuard],
        loadComponent: () => import('./features/account/account.page').then((m) => m.AccountPage),
      },
      {
        path: 'account/profile',
        canActivate: [customerGuard],
        loadComponent: () => import('./features/account/profile.page').then((m) => m.ProfilePage),
      },
      {
        path: 'account/requests/:reference',
        canActivate: [customerGuard],
        loadComponent: () =>
          import('./features/account/account-request.page').then((m) => m.AccountRequestPage),
      },
    ],
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'app',
    component: ShellComponent,
    canActivate: [staffGuard],
    children: [
      { path: '', pathMatch: 'full', canActivate: [homeRedirect], children: [] },
      {
        path: 'dashboard',
        canActivate: [officeGuard],
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'requests',
        canActivate: [officeGuard],
        loadComponent: () =>
          import('./features/requests/request-list.page').then((m) => m.RequestListPage),
      },
      {
        path: 'requests/new',
        canActivate: [officeGuard],
        loadComponent: () =>
          import('./features/requests/request-new.page').then((m) => m.RequestNewPage),
      },
      {
        // Technicians open their own jobs here too; the server answers NOT_ASSIGNED for anyone else's.
        // الفنيون يفتحون أعمالهم من هنا برضو؛ والخادم يرد NOT_ASSIGNED لأي شغل مو لهم.
        path: 'requests/:id',
        loadComponent: () =>
          import('./features/requests/request-detail.page').then((m) => m.RequestDetailPage),
      },
      {
        path: 'schedule',
        canActivate: [officeGuard],
        loadComponent: () =>
          import('./features/schedule/schedule.page').then((m) => m.SchedulePage),
      },
      {
        path: 'jobs',
        canActivate: [technicianGuard],
        loadComponent: () => import('./features/jobs/jobs.page').then((m) => m.JobsPage),
      },
      {
        path: 'services',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/services/services.page').then((m) => m.ServicesPage),
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
      {
        path: '**',
        loadComponent: () => import('./features/not-found.page').then((m) => m.NotFoundPage),
      },
    ],
  },
  {
    path: '**',
    component: PublicLayoutComponent,
    children: [
      {
        path: '',
        loadComponent: () => import('./features/not-found.page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
