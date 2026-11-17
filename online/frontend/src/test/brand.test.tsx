import { StrictMode } from 'react';
import { act, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { Room } from '../api';
import { HootlyMascot } from '../components/brand/HootlyMascot';
import { HootlyLogo } from '../components/brand/HootlyLogo';
import { roomReactions, useRoomMascotEvents } from '../components/brand/useRoomMascotEvents';
import { MotionProvider } from '../components/ui/MotionProvider';
import * as brandConfig from '../config/mascotScenes';
import { BrandAssetsProvider } from '../components/brand/BrandAssetsProvider';

const lobby: Room = {
  code: 'ABC234XY', title: 'Quiz night', hostId: 'host', status: 'LOBBY', invitationUrl: '/join/ABC234XY',
  maxPlayers: 8, questionCount: 2, secondsPerQuestion: 20, deadline: null, serverTime: '2026-11-15T00:00:00Z',
  participants: [{ id: 'host', displayName: 'Host', role: 'HOST', connected: true, left: false }],
  leaderboard: [{ id: 'host', displayName: 'Host', points: 0, correctCount: 0, rank: 1 }],
  question: null, submittedIndex: null,
};
const question: Room = { ...lobby, status: 'QUESTION', question: { number: 1, text: 'A question?', choices: ['Yes', 'No'], difficulty: 'easy', type: 'TRUE_FALSE', correctAnswer: null } };
const reveal: Room = { ...question, status: 'REVEAL', question: { ...question.question!, correctAnswer: 'Yes' } };

beforeEach(() => { vi.spyOn(brandConfig, 'hasBrandAsset').mockReturnValue(true); });
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); delete document.documentElement.dataset.reducedMotion; });

it('welcomes only newly joined participants, not initial rosters, reconnects or departures', () => {
  expect(roomReactions(null, lobby)).toEqual({});
  expect(roomReactions(lobby, structuredClone(lobby))).toEqual({});
  const joined = { ...lobby, participants: [...lobby.participants, { id: 'player', displayName: 'Player', role: 'PLAYER', connected: true, left: false }] };
  expect(roomReactions(lobby, joined)).toEqual({ lobby: 'playerJoined' });
  expect(roomReactions(joined, lobby)).toEqual({});
  expect(roomReactions(lobby, { ...lobby, participants: [{ ...lobby.participants[0], connected: false }] })).toEqual({});
});

it('reacts to correctness only after a server answer reveal and treats timeouts gently', () => {
  expect(roomReactions(question, { ...question, submittedIndex: 0 })).toEqual({});
  expect(roomReactions(question, { ...reveal, submittedIndex: 0 })).toEqual({ gameplay: 'correct', leaderboard: 'rankingRevealed' });
  expect(roomReactions(question, { ...reveal, submittedIndex: 1 }).gameplay).toBe('incorrect');
  expect(roomReactions(question, reveal).gameplay).toBe('timeout');
  expect(roomReactions(reveal, structuredClone(reveal))).toEqual({});
});

it('responds to confirmed starts, actual rank changes, and finished games', () => {
  expect(roomReactions(lobby, question).gameplay).toBe('gameStarted');
  expect(roomReactions(question, { ...question, leaderboard: [{ ...question.leaderboard[0], points: 10 }] })).toEqual({});
  expect(roomReactions(question, { ...question, leaderboard: [{ ...question.leaderboard[0], rank: 2 }] }).leaderboard).toBe('rankingChanged');
  const finished = { ...question, status: 'FINISHED' };
  expect(roomReactions(question, finished).results).toBe('finished');
  expect(roomReactions(finished, finished)).toEqual({});
  expect(roomReactions(null, finished)).toEqual({});
});

it('celebrates only actual first place and shows a sad pose for an actual lower rank',()=>{
  const finished={...question,status:'FINISHED',leaderboard:[...question.leaderboard,{id:'player',displayName:'Player',points:0,correctCount:0,rank:2}]};
  expect(roomReactions(question,finished,'host').results).toBe('won');
  expect(roomReactions(question,finished,'player').results).toBe('lost');
  expect(roomReactions(question,finished,'unknown').results).toBe('finished');
  const tied={...finished,leaderboard:finished.leaderboard.map(row=>({...row,rank:1}))};
  expect(roomReactions(question,tied,'player').results).toBe('won');
});

it('deduplicates repeated room polls and clears the previous answer reaction at the next question', () => {
  const { result, rerender } = renderHook(({ room }) => useRoomMascotEvents(room, room.code), { initialProps: { room: question } });
  rerender({ room: reveal });
  const event = result.current.gameplay;
  expect(event?.type).toBe('timeout');
  rerender({ room: structuredClone(reveal) });
  expect(result.current.gameplay).toBe(event);
  rerender({ room: { ...question, question: { ...question.question!, number: 2 } } });
  expect(result.current.gameplay).toBeUndefined();
});

it('plays each event once in StrictMode, returns to idle, and accepts a new event of the same kind', () => {
  vi.useFakeTimers();
  const complete = vi.fn();
  const view = (id: string) => <StrictMode><HootlyMascot scene="lobby" event={{ id, type: 'playerJoined' }} onReactionComplete={complete}/></StrictMode>;
  const { container, rerender } = render(view('join:1'));
  expect(container.firstChild).toHaveAttribute('data-event', 'playerJoined');
  act(() => { vi.advanceTimersByTime(1200); });
  expect(complete).toHaveBeenCalledTimes(1);
  expect(container.firstChild).toHaveAttribute('data-mood', 'idle');
  rerender(view('join:1'));
  act(() => { vi.advanceTimersByTime(1200); });
  expect(complete).toHaveBeenCalledTimes(1);
  rerender(view('join:2'));
  act(() => { vi.advanceTimersByTime(1200); });
  expect(complete).toHaveBeenCalledTimes(2);
});

it('cleans up completion callbacks on unmount and completes immediately with reduced motion', () => {
  vi.useFakeTimers();
  const complete = vi.fn();
  const { unmount } = render(<HootlyMascot scene="create" event={{ id: 'create:1', type: 'success' }} onReactionComplete={complete}/>);
  unmount();
  act(() => { vi.advanceTimersByTime(1000); });
  expect(complete).not.toHaveBeenCalled();
  document.documentElement.dataset.reducedMotion = 'true';
  const { container } = render(<MotionProvider><HootlyMascot scene="create" event={{ id: 'create:2', type: 'success' }} onReactionComplete={complete}/></MotionProvider>);
  expect(container.firstChild).toHaveAttribute('data-quiet', 'true');
  act(() => { vi.advanceTimersByTime(1); });
  expect(complete).toHaveBeenCalledTimes(1);
});

it('settles a confirmed win into a happy resting face without replaying the jump',()=>{
  vi.useFakeTimers();
  const {container}=render(<HootlyMascot scene="results" mood="happy" event={{id:'result:1',type:'won'}}/>);
  expect(container.firstChild).toHaveAttribute('data-mood','celebrating');
  const body=container.querySelector('[data-part="character-body-motion"]');
  act(()=>{vi.advanceTimersByTime(1700);});
  expect(container.firstChild).toHaveAttribute('data-mood','happy');
  expect(container.firstChild).not.toHaveAttribute('data-event');
  expect(container.querySelector('[data-part="character-body-motion"]')).toBe(body);
  expect(body).toHaveAttribute('data-movement','still');
});

it('keeps the approved character visible even when optional logo PNGs are absent', () => {
  vi.spyOn(brandConfig, 'hasBrandAsset').mockReturnValue(false);
  const { container } = render(<><HootlyLogo/><HootlyMascot scene="landing"/></>);
  expect(screen.getByText('Hootly')).toBeInTheDocument();
  expect(container.querySelector('.hootly-character svg')).toBeInTheDocument();
  expect(container.querySelector('img')).toBeNull();
  expect(container.querySelector('.hootly-mascot-placeholder,.hootly-monogram')).toBeNull();
});

it('uses the exact approved paths when artwork is present and falls back safely on load errors', () => {
  vi.spyOn(brandConfig, 'hasBrandAsset').mockReturnValue(true);
  const { container } = render(<><HootlyLogo/><HootlyLogo iconOnly/></>);
  const images = container.querySelectorAll('img');
  expect(images[0]).toHaveAttribute('src', '/brand/hootly-logo.png');
  expect(images[1]).toHaveAttribute('src', '/brand/hootly-owl-icon.png');
  fireEvent.error(images[0]); fireEvent.error(images[1]);
  expect(container.querySelector('img')).toBeNull();
  expect(screen.getAllByText('Hootly')).toHaveLength(2);
});

it('keeps privacy treatment independent of password visibility controls and motion', () => {
  const { container, rerender } = render(<HootlyMascot scene="login" privateInput/>);
  expect(container.firstChild).toHaveAttribute('data-private', 'true');
  expect(container.querySelector('.hootly-character')).toHaveAttribute('data-covered','true');
  expect(container.querySelector('[data-part="left-wing"]')).toBeInTheDocument();
  rerender(<HootlyMascot scene="login" privateInput={false}/>);
  expect(container.querySelector('.hootly-character')).not.toHaveAttribute('data-covered');
});

it('renders newly available official artwork even when it was absent at application startup', async () => {
  vi.spyOn(brandConfig, 'hasBrandAsset').mockReturnValue(false);
  const manifest: string[] = [];
  const request = vi.fn(async () => Response.json([...manifest]));
  vi.stubGlobal('fetch', request);
  const { container } = render(<BrandAssetsProvider><HootlyLogo iconOnly/></BrandAssetsProvider>);
  expect(container.querySelector('img')).toBeNull();
  await waitFor(() => expect(request).toHaveBeenCalledWith('/brand/assets.json', expect.anything()));
  manifest.push('/brand/hootly-owl-icon.png');
  fireEvent(window, new Event('focus'));
  await waitFor(() => expect(container.querySelector('img')).toHaveAttribute('src', '/brand/hootly-owl-icon.png'));
  expect(container.querySelector('img')).toHaveAttribute('alt','Hootly');
});

it('does not treat a successful HTML response as a valid asset manifest', async () => {
  vi.spyOn(brandConfig, 'hasBrandAsset').mockReturnValue(false);
  const request = vi.fn(async () => new Response('<html>SPA fallback</html>', {status:200}));
  vi.stubGlobal('fetch', request);
  const { container } = render(<BrandAssetsProvider><HootlyLogo/></BrandAssetsProvider>);
  await waitFor(() => expect(request).toHaveBeenCalled());
  expect(container.querySelector('img')).toBeNull();
});
