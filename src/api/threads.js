import { apiRequest } from './http';

export function createFolderThread(folderId, payload) {
  return apiRequest(`/api/v1/folders/${folderId}/threads`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function sendThreadMessage(threadId, payload, idempotencyKey, options = {}) {
  return apiRequest(`/api/v1/threads/${threadId}/messages`, {
    method: 'POST',
    headers: idempotencyKey ? { 'X-Idempotency-Key': idempotencyKey } : {},
    body: JSON.stringify(payload),
    signal: options.signal
  });
}

export function threadStreamUrl(threadId, lastEventId = 0) {
  return `/api/v1/threads/${threadId}/stream?last_event_id=${lastEventId}`;
}
