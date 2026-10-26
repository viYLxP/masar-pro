// TypeScript mirrors of the backend DTOs. Keeping them in one file makes API changes easy to spot.
// نسخ TypeScript من الـ DTOs في الخادم. وجودها في ملف واحد يسهّل ملاحظة أي تغيير في الـ API.
// Money values arrive as JSON numbers (BigDecimal on the server, 2 decimals, SAR, VAT included in "total").
// المبالغ تصل كأرقام JSON (BigDecimal في الخادم بمنزلتين، بالريال، و"total" شامل الضريبة).
// Instants are UTC ISO strings; plain dates ("2026-10-26") are already Riyadh calendar days.
// الأوقات نصوص ISO بتوقيت UTC؛ والتواريخ المجردة ("2026-10-26") هي أيام تقويم الرياض أصلًا.

export type Role = 'ADMIN' | 'DISPATCHER' | 'TECHNICIAN';
export type City = 'RIYADH' | 'JEDDAH' | 'DAMMAM' | 'KHOBAR' | 'MAKKAH' | 'MADINAH';
export type PropertyType = 'APARTMENT' | 'VILLA' | 'OFFICE' | 'RETAIL';
export type TimeSlot = 'MORNING' | 'AFTERNOON' | 'EVENING';
export type RequestStatus =
  | 'NEW'
  | 'QUOTED'
  | 'ACCEPTED'
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED';

export const CITIES: City[] = ['RIYADH', 'JEDDAH', 'DAMMAM', 'KHOBAR', 'MAKKAH', 'MADINAH'];
export const PROPERTY_TYPES: PropertyType[] = ['APARTMENT', 'VILLA', 'OFFICE', 'RETAIL'];
export const TIME_SLOTS: TimeSlot[] = ['MORNING', 'AFTERNOON', 'EVENING'];
export const ROLES: Role[] = ['ADMIN', 'DISPATCHER', 'TECHNICIAN'];
// The happy path, in order; REJECTED and CANCELLED are exits from it.
// المسار الطبيعي بالترتيب؛ وREJECTED وCANCELLED مخارج منه.
export const MAIN_FLOW: RequestStatus[] = [
  'NEW',
  'QUOTED',
  'ACCEPTED',
  'SCHEDULED',
  'IN_PROGRESS',
  'COMPLETED',
];
export const ALL_STATUSES: RequestStatus[] = [...MAIN_FLOW, 'REJECTED', 'CANCELLED'];

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Short reference to a service, with both names so the UI can switch language without a reload. */
/** مرجع مختصر للخدمة، بالاسمين حتى الواجهة تبدّل اللغة بدون إعادة تحميل. */
export interface ServiceRef {
  id: number;
  code: string;
  nameAr: string;
  nameEn: string;
}

export interface ServiceType extends ServiceRef {
  descriptionAr: string | null;
  descriptionEn: string | null;
  basePrice: number;
  durationMinutes: number;
  sortOrder: number;
  active: boolean;
  version: number;
}

export interface User {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  phone: string | null;
  city: City | null;
  skills: ServiceRef[];
  active: boolean;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface UserRef {
  id: number;
  fullName: string;
  phone: string | null;
}

export interface BookingCreated {
  reference: string;
  status: RequestStatus;
}

export interface QuoteLine {
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Quote {
  validUntil: string;
  expired: boolean;
  subtotal: number;
  vat: number;
  total: number;
  notes: string | null;
  // null while the quote is still a draft that the customer cannot see.
  // null ما دام العرض مسودة ما يشوفها العميل.
  sentAt: string | null;
  respondedAt: string | null;
  lines: QuoteLine[];
}

export interface RequestEvent {
  fromStatus: RequestStatus | null;
  toStatus: RequestStatus;
  actor: string;
  note: string | null;
  createdAt: string;
}

export interface RequestSummary {
  id: number;
  reference: string;
  customerName: string;
  phone: string;
  city: City;
  district: string;
  service: ServiceRef;
  preferredDate: string;
  preferredSlot: TimeSlot;
  status: RequestStatus;
  technician: UserRef | null;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  quoteTotal: number | null;
  createdAt: string;
}

export interface RequestDetail {
  id: number;
  reference: string;
  status: RequestStatus;
  customerName: string;
  phone: string;
  email: string | null;
  city: City;
  district: string;
  address: string | null;
  propertyType: PropertyType;
  service: ServiceRef;
  durationMinutes: number;
  preferredDate: string;
  preferredSlot: TimeSlot;
  details: string | null;
  technician: UserRef | null;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  startedAt: string | null;
  completedAt: string | null;
  completionNote: string | null;
  rejectReason: string | null;
  cancelReason: string | null;
  quote: Quote | null;
  history: RequestEvent[];
  createdAt: string;
  version: number;
}

/** What the customer sees when tracking: no staff usernames or internal notes. */
/** اللي يشوفه العميل في التتبع: بدون أسماء دخول الموظفين ولا ملاحظات داخلية. */
export interface PublicView {
  reference: string;
  status: RequestStatus;
  customerName: string;
  service: ServiceRef;
  city: City;
  district: string;
  preferredDate: string;
  preferredSlot: TimeSlot;
  quote: Quote | null;
  technicianName: string | null;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  completedAt: string | null;
  rejectReason: string | null;
  cancelReason: string | null;
  history: { status: RequestStatus; at: string }[];
  createdAt: string;
}

export interface JobSlot {
  id: number;
  reference: string;
  status: RequestStatus;
  service: ServiceRef;
  customerName: string;
  district: string;
  start: string;
  end: string;
}

export interface TechnicianDay {
  id: number;
  fullName: string;
  city: City;
  skills: ServiceRef[];
  jobs: JobSlot[];
}

export interface DayBoard {
  date: string;
  technicians: TechnicianDay[];
}

export interface JobCard {
  id: number;
  reference: string;
  status: RequestStatus;
  service: ServiceRef;
  customerName: string;
  phone: string;
  city: City;
  district: string;
  address: string | null;
  details: string | null;
  scheduledStart: string;
  scheduledEnd: string;
  completedAt: string | null;
  version: number;
}

export interface DashboardRow {
  id: number;
  reference: string;
  customerName: string;
  service: ServiceRef;
  district: string;
  status: RequestStatus;
  createdAt: string;
  validUntil: string | null;
  total: number | null;
}

export interface Dashboard {
  statusCounts: Record<RequestStatus, number>;
  revenueThisMonth: number;
  completedThisMonth: number;
  // null when no quote was answered in the last 90 days. / null لو ما انرد على أي عرض آخر 90 يوم.
  acceptanceRate: number | null;
  today: JobSlot[];
  waitingForQuote: DashboardRow[];
  expiringQuotes: DashboardRow[];
  topServices: { service: ServiceRef; count: number }[];
}
