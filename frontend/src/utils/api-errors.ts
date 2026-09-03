export type FailureNotice = { message: string; detail: string };
export type Notice = string | FailureNotice;

function text(value: unknown): string {
  return typeof value === 'string' ? value.replace(/\p{Cc}/gu, ' ').trim().slice(0, 1200) : '';
}

export function errorDetail(reason: unknown): string {
  const value = reason instanceof Error ? reason.message : reason;
  return typeof value === 'string' ? value.split('\n').map(text).join('\n').slice(0, 4000) : '';
}

export function failureNotice(message: string, reason: unknown): FailureNotice {
  return { message, detail: errorDetail(reason) || message };
}

/** Keep only diagnostic fields, never request bodies, headers, HTML error pages or stack traces. */
export async function apiFetch(url: string, init?: RequestInit): Promise<Response> {
  const method = init?.method ?? 'GET';
  const target = `${method} ${url.split(/[?#]/)[0]}`;
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch (reason) {
    if (init?.signal?.aborted || (reason instanceof Error && reason.name === 'AbortError')) throw reason;
    throw new Error(`${target}\n${errorDetail(reason) || 'Network error'}`);
  }
  if (response.ok) return response;
  const details = [target, `HTTP ${response.status} ${response.statusText}`.trim()];
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/problem+json') || contentType.includes('application/json')) {
    try {
      const problem: unknown = await response.json();
      if (problem && typeof problem === 'object') {
        const fields = problem as Record<string, unknown>;
        for (const value of [fields.title, fields.detail]) {
          const detail = text(value);
          if (detail && !details.includes(detail)) details.push(detail);
        }
        const correlationId = text(fields.correlationId);
        if (correlationId) details.push(`Correlation: ${correlationId}`);
      }
    } catch { /* The HTTP status remains useful when an error body cannot be decoded. */ }
  }
  throw new Error(details.join('\n'));
}
