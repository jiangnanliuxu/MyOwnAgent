import { apiRequest } from './http';

export function uploadThreadRagFile(threadId, file, { scope = 'thread', sourcePath = '' } = {}) {
  const form = new FormData();
  form.append('file', file);
  form.append('scope', scope);
  if (sourcePath) {
    form.append('source_path', sourcePath);
  }
  return apiRequest(`/api/v1/threads/${threadId}/rag/uploads`, {
    method: 'POST',
    body: form
  });
}
