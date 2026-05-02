import { apiSession } from '../api/session';

export function createThreadEventSource(threadId, lastEventId = 0) {
  const session = apiSession();
  if (!session.baseUrl) return null;
  const url = new URL(`/api/v1/threads/${threadId}/stream`, session.baseUrl);
  url.searchParams.set('last_event_id', String(lastEventId));
  return new EventSource(url.toString());
}

export async function fetchThreadEvents(threadId, lastEventId = 0, { replayOnly = true } = {}) {
  const session = apiSession();
  if (!session.baseUrl) return [];
  const url = new URL(`/api/v1/threads/${threadId}/stream`, session.baseUrl);
  url.searchParams.set('last_event_id', String(lastEventId));
  if (replayOnly) url.searchParams.set('replay_only', 'true');
  const headers = new Headers();
  if (session.accessToken) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }
  const response = await fetch(url.toString(), { headers });
  if (!response.ok) {
    throw new Error(`SSE replay failed: ${response.status}`);
  }
  return parseEventStream(await response.text());
}

export function parseEventStream(text) {
  return String(text || '')
    .split(/\n\n+/)
    .map((block) => parseEventBlock(block))
    .filter(Boolean);
}

function parseEventBlock(block) {
  const event = { id: '', type: 'message', data: '' };
  String(block || '').split(/\n/).forEach((line) => {
    if (!line || line.startsWith(':')) return;
    const separatorIndex = line.indexOf(':');
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line;
    const value = separatorIndex >= 0 ? line.slice(separatorIndex + 1).replace(/^ /, '') : '';
    if (field === 'id') event.id = value;
    if (field === 'event') event.type = value;
    if (field === 'data') event.data = event.data ? `${event.data}\n${value}` : value;
  });
  if (!event.data && event.type === 'message') return null;
  try {
    event.payload = event.data ? JSON.parse(event.data) : null;
  } catch {
    event.payload = event.data;
  }
  return event;
}
