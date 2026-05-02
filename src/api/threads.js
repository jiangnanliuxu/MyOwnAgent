import { apiRequest } from './http';

export function sendThreadMessage(threadId, payload, idempotencyKey) {
  return apiRequest(`/api/v1/threads/${threadId}/messages`, {
    method: 'POST',
    headers: idempotencyKey ? { 'X-Idempotency-Key': idempotencyKey } : {},
    body: JSON.stringify(payload)
  });
}

export function threadStreamUrl(threadId, lastEventId = 0) {
  return `/api/v1/threads/${threadId}/stream?last_event_id=${lastEventId}`;
}
