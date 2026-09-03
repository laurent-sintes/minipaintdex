import assert from 'node:assert/strict';
import { test } from 'node:test';
import { apiFetch, failureNotice } from './api-errors.ts';

test('successful responses and mutation options remain untouched', async t => {
  const response = new Response('{"ok":true}', { status: 202 });
  const options = { method: 'POST', body: 'intent' };
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    assert.equal(url, '/api/v1/command'); assert.equal(init, options); return response;
  });
  assert.equal(await apiFetch('/api/v1/command', options), response);
});

test('HTTP failures preserve ProblemDetail and correlation but omit query, payload and stack', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    title: 'Conflict', detail: 'This pot is already open.', correlationId: 'request-42',
    stack: 'private stack', payload: 'private payload',
  }), { status: 409, headers: { 'content-type': 'application/problem+json' } }));
  await assert.rejects(apiFetch('/api/v1/pots/open?secret=value', { method: 'POST' }), error => {
    const notice = failureNotice('Failed', error);
    assert.equal(notice.message, 'Failed');
    assert.match(notice.detail, /POST \/api\/v1\/pots\/open\nHTTP 409\nConflict\nThis pot is already open./);
    assert.match(notice.detail, /Correlation: request-42/);
    assert.doesNotMatch(notice.detail, /secret|private/); return true;
  });
});

test('HTML and malformed error bodies fall back to HTTP diagnostics', async t => {
  for (const contentType of ['text/html', 'application/problem+json']) {
    t.mock.method(globalThis, 'fetch', async () => new Response('<script>private</script>', {
      status: 503, headers: { 'content-type': contentType },
    }));
    await assert.rejects(apiFetch('/api/v1/dashboard'), { message: 'GET /api/v1/dashboard\nHTTP 503' });
    t.mock.restoreAll();
  }
});

test('network failures retain the route and reason; cancellation stays silent to callers', async t => {
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('Failed to fetch'); });
  await assert.rejects(apiFetch('/api/v1/dashboard'), { message: 'GET /api/v1/dashboard\nFailed to fetch' });
  const aborted = new DOMException('Cancelled', 'AbortError');
  t.mock.method(globalThis, 'fetch', async () => { throw aborted; });
  await assert.rejects(apiFetch('/api/v1/dashboard'), error => error === aborted);
});
