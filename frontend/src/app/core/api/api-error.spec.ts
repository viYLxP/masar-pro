import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { I18nService } from '../i18n/i18n.service';
import { errorMessage, toApiError } from './api-error';

describe('api-error', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
    i18n.setLang('en');
  });

  it('reads code, fields and details from a ProblemDetail body', () => {
    const err = toApiError(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'VALIDATION_FAILED', fields: { phone: 'Pattern' } },
      }),
    );
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_FAILED');
    expect(err.fields).toEqual({ phone: 'Pattern' });
  });

  it('maps status 0 to NETWORK and non-HTTP errors to UNKNOWN', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK');
    expect(toApiError(new Error('boom')).code).toBe('UNKNOWN');
    // A proxy HTML error page has no JSON code.
    // صفحة خطأ HTML من البروكسي ما فيها code بصيغة JSON.
    expect(toApiError(new HttpErrorResponse({ status: 502, error: '<html>' })).code).toBe(
      'UNKNOWN',
    );
  });

  it('translates the request status inside the message', () => {
    const err = toApiError(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'INVALID_STATUS', status: 'COMPLETED' },
      }),
    );
    expect(errorMessage(i18n, err)).toContain('Completed');
    i18n.setLang('ar');
    expect(errorMessage(i18n, err)).toContain('مكتمل');
  });

  it('shows a busy technician clash in Riyadh time', () => {
    // 11:00 UTC is 14:00 in Riyadh (UTC+3, no daylight saving).
    // الساعة 11:00 UTC تساوي 14:00 في الرياض (UTC+3 بدون توقيت صيفي).
    const err = toApiError(
      new HttpErrorResponse({
        status: 409,
        error: {
          code: 'TECHNICIAN_BUSY',
          reference: 'MS-AAAA2222',
          start: '2026-11-01T11:00:00Z',
          end: '2026-11-01T12:30:00Z',
        },
      }),
    );
    expect(errorMessage(i18n, err)).toBe(
      'The technician already has MS-AAAA2222 from 2:00 PM to 3:30 PM.',
    );
  });

  it('turns the rate-limit wait into whole minutes', () => {
    const err = toApiError(
      new HttpErrorResponse({
        status: 429,
        error: { code: 'RATE_LIMITED', retryAfterSeconds: 61 },
      }),
    );
    expect(errorMessage(i18n, err)).toContain('wait 2 minutes');
  });

  it('falls back to a generic message for an unknown code', () => {
    const err = toApiError(new HttpErrorResponse({ status: 418, error: { code: 'TEAPOT' } }));
    expect(errorMessage(i18n, err)).toBe(i18n.t('errors.UNKNOWN'));
  });
});
