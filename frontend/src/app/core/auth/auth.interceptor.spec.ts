import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.setItem('masar.token', 'staff-token');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    sessionStorage.clear();
  });

  it('sends the staff token to staff endpoints', () => {
    http.get('/api/requests').subscribe();
    expect(backend.expectOne('/api/requests').request.headers.get('Authorization')).toBe(
      'Bearer staff-token',
    );
  });

  it('never sends it to the public site endpoints', () => {
    http.post('/api/public/requests/track', {}).subscribe();
    const req = backend.expectOne('/api/public/requests/track');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.headers.get('Accept-Language')).toBeTruthy();
  });
});
