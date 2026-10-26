import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { JobSlot, TechnicianDay } from '../core/api/models';
import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import {
  DAY_END_MIN,
  DAY_START_MIN,
  riyadhMinutesOf,
  serviceName,
  statusTone,
} from '../core/masar';

const SPAN = DAY_END_MIN - DAY_START_MIN;

// Hour marks every 2 hours for the ruler (8, 10, … 20). / علامات كل ساعتين للمسطرة (8، 10، … 20).
const HOURS = Array.from({ length: SPAN / 120 + 1 }, (_, i) => DAY_START_MIN / 60 + i * 2);

/** A proposed booking drawn on top of the board, so the dispatcher sees it next to existing jobs. */
/** حجز مقترح يُرسم فوق اللوحة، حتى يشوفه المنسّق جنب الأعمال الموجودة. */
export interface Proposal {
  technicianId: number;
  startMin: number;
  endMin: number;
}

/**
 * One day of technicians as horizontal lines from 08:00 to 20:00 with their jobs as blocks.
 * Logical CSS (inset-inline-start) makes the timeline run right-to-left in Arabic automatically.
 * يوم الفنيين كخطوط أفقية من 8 الصبح لين 8 الليل وأعمالهم كمربعات.
 * الـ CSS المنطقي (inset-inline-start) يخلي الخط الزمني يمشي من اليمين لليسار في العربي تلقائيًا.
 *
 * Every block also has a text label (time, customer, status) so nothing depends on colour or position alone.
 * كل مربع له نص (الوقت، العميل، الحالة) حتى ما يعتمد شي على اللون أو المكان بس.
 */
@Component({
  selector: 'app-day-timeline',
  imports: [RouterLink, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="timeline-board" [attr.aria-label]="'schedule.timeline' | t" role="group">
      <div class="timeline-board__ruler" aria-hidden="true">
        @for (h of hours; track h) {
          <span [style.inset-inline-start.%]="pos(h * 60)">{{ fmt.clock(pad(h) + ':00') }}</span>
        }
      </div>
      @for (row of rows(); track row.tech.id) {
        <div
          class="timeline-row"
          [class.timeline-row--on]="row.proposal"
          data-testid="timeline-row"
        >
          <div class="timeline-row__name">
            <strong
              ><bdi>{{ row.tech.fullName }}</bdi></strong
            >
            <small>{{
              row.tech.jobs.length
                ? ('schedule.jobs' | t: { count: row.tech.jobs.length })
                : ('schedule.free' | t)
            }}</small>
          </div>
          <ul class="timeline-row__track">
            @for (job of row.jobs; track job.slot.id) {
              <li
                [class]="'timeline-job timeline-job--' + job.tone"
                [style.inset-inline-start.%]="job.left"
                [style.width.%]="job.width"
                [title]="job.title"
              >
                @if (linkJobs()) {
                  <a [routerLink]="['/app/requests', job.slot.id]">{{ job.label }}</a>
                } @else {
                  <span>{{ job.label }}</span>
                }
              </li>
            }
            @if (row.proposal; as p) {
              <li
                class="timeline-job timeline-job--proposal"
                [style.inset-inline-start.%]="p.left"
                [style.width.%]="p.width"
                data-testid="timeline-proposal"
              >
                <span>{{ p.label }}</span>
              </li>
            }
          </ul>
        </div>
      }
    </div>
  `,
})
export class DayTimelineComponent {
  protected readonly fmt = inject(FormatService);
  private readonly i18n = inject(I18nService);

  readonly technicians = input.required<TechnicianDay[]>();
  readonly proposal = input<Proposal | null>(null);
  readonly linkJobs = input(true);

  protected readonly hours = HOURS;

  protected readonly rows = computed(() => {
    const proposal = this.proposal();
    return this.technicians().map((tech) => ({
      tech,
      jobs: tech.jobs.map((slot) => this.block(slot)),
      proposal:
        proposal && proposal.technicianId === tech.id
          ? {
              left: this.pos(proposal.startMin),
              width: this.pos(proposal.endMin) - this.pos(proposal.startMin),
              label: `${this.fmt.clock(this.hhmm(proposal.startMin))} – ${this.fmt.clock(this.hhmm(proposal.endMin))}`,
            }
          : null,
    }));
  });

  private block(slot: JobSlot) {
    const start = riyadhMinutesOf(slot.start);
    const end = riyadhMinutesOf(slot.end);
    const time = this.fmt.timeRange(slot.start, slot.end);
    const status = this.i18n.t(`status.${slot.status}`);
    const service = serviceName(slot.service, this.i18n.lang());
    return {
      slot,
      tone: statusTone(slot.status),
      left: this.pos(start),
      width: Math.max(2, this.pos(end) - this.pos(start)),
      label: `${time} · ${slot.customerName}`,
      title: `${slot.reference} · ${service} · ${slot.customerName} · ${time} · ${status}`,
    };
  }

  // Minutes since midnight → percentage along the 08:00–20:00 line, clamped to the visible day.
  // الدقائق من منتصف الليل ← نسبة على خط 8–20، ومحصورة داخل اليوم الظاهر.
  protected pos(minutes: number): number {
    return (Math.min(Math.max(minutes - DAY_START_MIN, 0), SPAN) / SPAN) * 100;
  }

  protected pad(h: number): string {
    return String(h).padStart(2, '0');
  }

  private hhmm(minutes: number): string {
    return `${this.pad(Math.floor(minutes / 60))}:${this.pad(minutes % 60)}`;
  }
}
