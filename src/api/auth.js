import { apiRequest } from './http';

export function login(payload) {
  return apiRequest('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function register(payload) {
  return apiRequest('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function fetchMe() {
  return apiRequest('/api/v1/auth/me');
}
