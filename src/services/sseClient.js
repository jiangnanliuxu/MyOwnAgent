import { apiSession } from '../api/session';

export function createThreadEventSource(threadId, lastEventId = 0) {
  const session = apiSession();
  if (!session.baseUrl) return null;
  const url = new URL(`/api/v1/threads/${threadId}/stream`, session.baseUrl);
  url.searchParams.set('last_event_id', String(lastEventId));
  return new EventSource(url.toString());
}
