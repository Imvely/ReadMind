import type {
  LoginRequest,
  LoginResponse,
  MeResponse,
  SignupRequest,
  SignupResponse,
} from '@readmind/shared';
import { apiRequest } from '@/lib/api';

export function signup(req: SignupRequest): Promise<SignupResponse> {
  return apiRequest('/auth/signup', { method: 'POST', body: req, noAuthRetry: true });
}

export function login(req: LoginRequest): Promise<LoginResponse> {
  return apiRequest('/auth/login', { method: 'POST', body: req, noAuthRetry: true });
}

export function getMe(): Promise<MeResponse> {
  return apiRequest('/auth/me');
}
