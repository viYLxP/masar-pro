import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { openCheckout } from './checkout';

describe('openCheckout', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));

  it('opens the built-in test page inside the app, whatever host PUBLIC_URL names', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    openCheckout(router, { paymentId: 'abc', checkoutUrl: 'http://localhost:8083/pay/abc' });
    expect(navigate).toHaveBeenCalledWith('/pay/abc');
  });

  it('leaves the site for a real gateway page', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl');
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, origin: 'http://localhost', assign });
    openCheckout(router, { paymentId: 'abc', checkoutUrl: 'https://checkout.moyasar.com/i/xyz' });
    expect(assign).toHaveBeenCalledWith('https://checkout.moyasar.com/i/xyz');
    expect(navigate).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
