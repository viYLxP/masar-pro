import { TestBed } from '@angular/core/testing';

import { I18nService } from '../core/i18n/i18n.service';
import { StatusStepperComponent } from './status-stepper.component';

function render(status: string, reached: string[] = []) {
  const fixture = TestBed.createComponent(StatusStepperComponent);
  fixture.componentRef.setInput('status', status);
  fixture.componentRef.setInput('reached', reached);
  fixture.detectChanges();
  const items = [...(fixture.nativeElement as HTMLElement).querySelectorAll('li')];
  return items.map((li) => ({
    text: li.textContent?.trim(),
    done: li.classList.contains('is-done'),
    exit: li.classList.contains('is-exit'),
    current: li.getAttribute('aria-current') === 'step',
  }));
}

describe('StatusStepperComponent', () => {
  beforeEach(() => TestBed.inject(I18nService).setLang('en'));

  it('marks earlier steps done and the current one as the step', () => {
    const steps = render('ACCEPTED');
    expect(steps).toHaveLength(6);
    expect(steps.slice(0, 2).every((s) => s.done)).toBe(true);
    expect(steps[2]).toMatchObject({ text: 'Accepted', current: true, done: false });
    expect(steps.slice(3).some((s) => s.done || s.current)).toBe(false);
  });

  it('shows a completed job with every step done', () => {
    expect(render('COMPLETED').every((s) => s.done)).toBe(true);
  });

  it('ends a rejected request at the step it reached, then the exit', () => {
    const steps = render('REJECTED', ['NEW', 'QUOTED', 'REJECTED']);
    expect(steps.map((s) => s.text)).toEqual(['New', 'Quote sent', 'Rejected']);
    expect(steps[2]).toMatchObject({ exit: true, current: true });
  });
});
