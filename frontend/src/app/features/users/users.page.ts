import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CITIES, City, ROLES, Role, ServiceRef, ServiceType, User } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { SAUDI_MOBILE, localPhone, serviceName } from '../../core/masar';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

// The single dialog has three modes; each mode shows only the fields the matching API call accepts.
// النافذة الوحدة لها ثلاث حالات؛ كل حالة تعرض فقط الحقول اللي يقبلها طلب الـ API المقابل.
type DialogMode = 'create' | 'edit' | 'password';

/**
 * Admin-only user management: create accounts, change role/active state and reset passwords.
 * إدارة المستخدمين للمدير فقط: إنشاء حسابات، تغيير الدور والحالة، وإعادة تعيين كلمة المرور.
 *
 * Users are never deleted: their names are on jobs and in request history, so we deactivate instead.
 * المستخدمون لا يُحذفون أبدًا: أسماؤهم على الأعمال وفي سجل الطلبات، فنعطّل الحساب بدل الحذف.
 *
 * Technicians also need a work city and at least one skill, otherwise they could never be booked
 * (the server refuses a technician without them).
 * الفني يحتاج كمان مدينة عمل ومهارة وحدة على الأقل، وإلا ما يمكن حجزه أبدًا
 * (والخادم يرفض الفني بدونها).
 */
@Component({
  selector: 'app-users',
  imports: [ReactiveFormsModule, TranslatePipe, IconComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './users.page.html',
})
export class UsersPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly users = rxResource({ stream: () => this.api.get<User[]>('/users') });
  protected readonly services = rxResource({
    stream: () => this.api.get<ServiceType[]>('/services'),
  });
  protected readonly roles = ROLES;
  protected readonly cities = CITIES;
  protected readonly phone = localPhone;
  // Skills picked in the dialog, kept as a signal because checkboxes are not a single form control.
  // المهارات المختارة في النافذة، محفوظة كـ signal لأن مربعات الاختيار مو حقل نموذج واحد.
  protected readonly skillIds = signal<Set<number>>(new Set());
  protected readonly skillsTouched = signal(false);

  protected readonly mode = signal<DialogMode>('create');
  protected readonly target = signal<User | null>(null);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  // Editing yourself: role and active are locked in the UI, mirroring the server's CANNOT_MODIFY_SELF rule.
  // تعديل نفسك: الدور والحالة مقفلة في الواجهة، مطابقة لقاعدة CANNOT_MODIFY_SELF في الخادم.
  protected readonly editingSelf = computed(() => this.isSelf(this.target()));

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(50),
        Validators.pattern(/^[A-Za-z0-9._-]+$/),
      ],
    ],
    fullName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    // Same bounds as the server: BCrypt only uses the first 72 bytes.
    // نفس حدود الخادم: BCrypt يستخدم أول 72 بايت فقط.
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    role: ['DISPATCHER' as Role, Validators.required],
    phone: ['', [Validators.maxLength(20), Validators.pattern(SAUDI_MOBILE)]],
    city: [null as City | null],
    active: [true],
  });

  protected readonly role = signal<Role>('DISPATCHER');
  protected readonly isTechnician = computed(() => this.role() === 'TECHNICIAN');
  protected readonly skillsMissing = computed(
    () => this.isTechnician() && this.skillIds().size === 0,
  );

  constructor() {
    // The technician-only fields (city, skills) appear and become required only for that role.
    // حقول الفني (المدينة والمهارات) تظهر وتصير مطلوبة لهذا الدور بس.
    this.form.controls.role.valueChanges.subscribe((role) => {
      this.role.set(role);
      this.syncCityRule(role);
    });
  }

  private syncCityRule(role: Role): void {
    const city = this.form.controls.city;
    city.setValidators(role === 'TECHNICIAN' ? Validators.required : null);
    city.updateValueAndValidity({ emitEvent: false });
  }

  protected toggleSkill(id: number, on: boolean): void {
    this.skillsTouched.set(true);
    this.skillIds.update((set) => {
      const next = new Set(set);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  protected skillName(service: ServiceRef): string {
    return serviceName(service, this.i18n.lang());
  }

  protected isSelf(user: User | null): boolean {
    const me = this.auth.user();
    return !!user && !!me && user.username.toLowerCase() === me.username.toLowerCase();
  }

  protected open(mode: DialogMode, user: User | null): void {
    this.mode.set(mode);
    this.target.set(user);
    this.apiError.set(null);
    this.form.reset({
      username: user?.username ?? '',
      fullName: user?.fullName ?? '',
      password: '',
      role: user?.role ?? 'DISPATCHER',
      phone: localPhone(user?.phone),
      city: user?.city ?? null,
      active: user?.active ?? true,
    });
    this.role.set(user?.role ?? 'DISPATCHER');
    this.syncCityRule(this.role());
    this.skillIds.set(new Set(user?.skills.map((s) => s.id) ?? []));
    this.skillsTouched.set(false);
    // Disabled controls are skipped by validation, so each mode enables only the fields it submits.
    // الحقول المعطّلة تُتجاهل في التحقق، فكل حالة تفعّل فقط الحقول اللي ترسلها.
    const c = this.form.controls;
    this.toggle(c.username, mode === 'create');
    this.toggle(c.password, mode !== 'edit');
    this.toggle(c.fullName, mode !== 'password');
    this.toggle(c.role, mode !== 'password' && !this.isSelf(user));
    this.toggle(c.active, mode === 'edit' && !this.isSelf(user));
    this.toggle(c.phone, mode !== 'password');
    this.toggle(c.city, mode !== 'password');
    this.dialog().nativeElement.showModal();
  }

  private toggle(control: { enable(): void; disable(): void }, on: boolean): void {
    if (on) control.enable();
    else control.disable();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  protected submit(): void {
    if (this.form.invalid || (this.mode() !== 'password' && this.skillsMissing())) {
      this.form.markAllAsTouched();
      this.skillsTouched.set(true);
      return;
    }
    // getRawValue includes disabled controls, so locked role/active keep the user's current values.
    // getRawValue يشمل الحقول المعطّلة، فالدور والحالة المقفلة تحتفظ بقيم المستخدم الحالية.
    const v = this.form.getRawValue();
    const user = this.target();
    const mode = this.mode();
    // Office staff have no city or skills; sending empty values keeps the server's data clean.
    // موظفو المكتب ما لهم مدينة ولا مهارات؛ إرسال قيم فاضية يخلي بيانات الخادم نظيفة.
    const technician = v.role === 'TECHNICIAN';
    const profile = {
      phone: v.phone.trim() || null,
      city: technician ? v.city : null,
      skillIds: technician ? [...this.skillIds()] : [],
    };
    const request: Observable<unknown> =
      mode === 'create'
        ? this.api.post<User>('/users', {
            username: v.username.trim(),
            fullName: v.fullName.trim(),
            password: v.password,
            role: v.role,
            ...profile,
          })
        : mode === 'edit'
          ? this.api.put<User>(`/users/${user!.id}`, {
              fullName: v.fullName.trim(),
              role: v.role,
              active: v.active,
              ...profile,
            })
          : this.api.put<void>(`/users/${user!.id}/password`, { password: v.password });
    this.saving.set(true);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(
          this.i18n.t(mode === 'password' ? 'users.passwordReset' : 'common.saved'),
        );
        this.close();
        if (mode !== 'password') this.users.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }
}
