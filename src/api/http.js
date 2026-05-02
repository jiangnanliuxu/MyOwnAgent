import { apiSession } from './session';

export class ApiError extends Error {
  constructor(message, { status, body } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

export async function apiRequest(path, options = {}) {
  const session = apiSession();
  if (!session.baseUrl) {
    throw new ApiError('Backend API base URL is not configured.');
  }

  const headers = new Headers(options.headers || {});
  if (session.accessToken) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${session.baseUrl}${path}`, {
    ...options,
    headers
  });
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await response.json() : await response.text();
  if (!response.ok || body?.code && body.code !== '0') {
    throw new ApiError(body?.message || response.statusText, { status: response.status, body });
  }
  return body.data;
}
