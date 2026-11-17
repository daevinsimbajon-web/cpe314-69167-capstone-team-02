import { afterEach, expect, it, vi } from 'vitest';
import { api } from '../api';
afterEach(() => vi.unstubAllGlobals());
it('preserves the server room-removal code separately from its display message',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(Response.json({code:'ROOM_REMOVED',message:'You were removed'},{status:403})));
  await expect(api('/rooms/TLFSKBMH')).rejects.toMatchObject({status:403,code:'ROOM_REMOVED',message:'You were removed'});
});
it('obtains a CSRF token and sends credentials only in cookies', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({token:'csrf',headerName:'X-CSRF-TOKEN'})))
    .mockResolvedValueOnce(new Response(JSON.stringify({authenticated:true})));
  vi.stubGlobal('fetch',fetch);
  await api('/auth/login',{method:'POST',body:new URLSearchParams({username:'player',password:'secret'})});
  expect(fetch).toHaveBeenCalledTimes(2);
  const request = fetch.mock.calls[1][1] as RequestInit;
  expect(request.credentials).toBe('include');
  expect((request.headers as Headers).get('X-CSRF-TOKEN')).toBe('csrf');
  expect(localStorage.length).toBe(0);
});
it('surfaces backend validation errors', async () => {
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({message:'Full room'}),{status:409})));
  await expect(api('/rooms/ABCDEFGH')).rejects.toThrow('Full room');
});
it('reports an unavailable account service when the CSRF endpoint fails without sending registration', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response('', { status: 500 }));
  vi.stubGlobal('fetch', fetch);
  await expect(api('/auth/register', { method: 'POST', body: '{}' }))
    .rejects.toThrow('Account service is unavailable. Please try again shortly.');
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch.mock.calls[0][0]).toBe('/api/auth/csrf');
});
it('keeps the security-session error for a rejected CSRF request', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response('', { status: 403 }));
  vi.stubGlobal('fetch', fetch);
  await expect(api('/auth/register', { method: 'POST', body: '{}' }))
    .rejects.toThrow('Could not establish a secure session. Please retry.');
  expect(fetch).toHaveBeenCalledTimes(1);
});
