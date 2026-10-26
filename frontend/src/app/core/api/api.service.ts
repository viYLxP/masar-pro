import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

// All endpoints live under /api. In development Angular's proxy forwards them to Spring Boot (proxy.conf.json);
// in Docker nginx does the same, so the browser always talks to one origin and no CORS is needed.
// كل الروابط تحت /api. أثناء التطوير، بروكسي Angular يحوّلها لـ Spring Boot (proxy.conf.json)،
// وفي Docker يسوي nginx نفس الشي، فالمتصفح دائمًا يكلم مصدر واحد وما نحتاج CORS.
const BASE = '/api';

// An array repeats the parameter (?sort=a&sort=b), which is how Spring reads several sort orders.
// المصفوفة تكرر الباراميتر (?sort=a&sort=b)، وهكذا يقرأ Spring أكثر من ترتيب.
export type QueryValue = string | number | boolean | string[] | null | undefined;

/**
 * Thin typed wrapper around HttpClient.
 * غلاف بسيط مع أنواع واضحة فوق HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  get<T>(path: string, query?: Record<string, QueryValue>): Observable<T> {
    return this.http.get<T>(BASE + path, { params: toParams(query) });
  }

  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(BASE + path, body);
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(BASE + path, body);
  }

  delete(path: string): Observable<void> {
    return this.http.delete<void>(BASE + path);
  }
}

// Drops empty values so "?q=&status=" never reaches the server as empty filters.
// يشيل القيم الفاضية حتى ما توصل "?q=&status=" للخادم كفلاتر فاضية.
function toParams(query?: Record<string, QueryValue>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (Array.isArray(value)) {
      for (const item of value) params = params.append(key, item);
    } else if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
