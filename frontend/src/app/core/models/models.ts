// Types mirroring the Spring Boot DTOs. Keeping them in one place means a
// backend change shows up as a compile error here instead of a runtime bug.

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN';

export interface User {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  specialty: string | null;
}

export interface AuthResponse {
  token: string;
  expiresInSeconds: number;
  user: User;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  role: Exclude<Role, 'ADMIN'>;
  specialty?: string | null;
}

export type AppointmentStatus = 'BOOKED' | 'CANCELLED' | 'COMPLETED';

export interface Appointment {
  id: number;
  patientId: number;
  patientName: string;
  doctorId: number;
  doctorName: string;
  specialty: string;
  /** Local date-time, e.g. "2027-01-15T10:30:00" (no timezone). */
  dateTime: string;
  reason: string;
  status: AppointmentStatus;
}

export interface CreateAppointmentPayload {
  doctorId: number;
  dateTime: string;
  reason: string;
}

/** Error body returned by GlobalExceptionHandler on the backend. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}

// ---- recommendations ----

export interface SpecialtyScore {
  specialty: string;
  /** 0..1 */
  confidence: number;
}

export interface DoctorMatch {
  id: number;
  fullName: string;
  specialty: string;
  /** Model confidence for this doctor's specialty, 0..1 */
  matchScore: number;
  reason: string;
  upcomingBookings: number;
}

export interface RecommendResponse {
  emergency: boolean;
  emergencyMessage: string | null;
  lowConfidence: boolean;
  note: string | null;
  specialties: SpecialtyScore[];
  doctors: DoctorMatch[];
  disclaimer: string;
}

// ---- chat ----

export interface ChatStatus {
  /** "claude" = real AI, "demo" = scripted replies (no AI key on the server) */
  provider: 'claude' | 'demo';
  model: string;
}

export interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
}

/** Events the server streams (see ChatController.java). */
export type ChatStreamEvent =
  | { type: 'delta'; text: string }
  | { type: 'emergency'; message: string }
  | { type: 'notice'; message: string }
  | { type: 'error'; message: string }
  | { type: 'done' };

// ---- risk check ----

export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH';

export interface RiskRequest {
  age: number;
  glucose: number | null;
  bmi: number | null;
  pregnancies: number | null;
}

export interface RiskFactor {
  name: string;
  label: string;
  unit: string;
  value: number | null;
  /** log-odds contribution; the sign is already interpreted in `direction` */
  impact: number;
  direction: 'RAISES' | 'LOWERS' | 'NEUTRAL';
}

export interface RiskFlag {
  severity: 'warning' | 'urgent';
  message: string;
}

export interface RiskResponse {
  id: number;
  createdAt: string;
  score: number;
  level: RiskLevel;
  factors: RiskFactor[];
  missing: string[];
  outOfRange: string[];
  flags: RiskFlag[];
  reliabilityNote: string | null;
  model: { version: string; trainedOn: string; cvAuc: number; cvAucWithoutGlucose: number };
  disclaimer: string;
}

export interface RiskHistoryItem {
  id: number;
  createdAt: string;
  score: number;
  level: RiskLevel;
  age: number;
  glucose: number | null;
  bmi: number | null;
  pregnancies: number | null;
}

// ---- paging, summary, edit ----

/** Matches the backend's PageResponse envelope. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface AppointmentSummary {
  total: number;
  booked: number;
  completed: number;
  cancelled: number;
  next: Appointment | null;
}

/** PATCH body: send only what changed. */
export interface UpdateAppointmentPayload {
  dateTime?: string;
  reason?: string;
}
