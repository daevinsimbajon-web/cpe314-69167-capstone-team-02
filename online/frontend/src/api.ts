import type { AvatarConfig } from './components/avatar/avatarConfig';
export class ApiError extends Error {
  constructor(public status: number, message: string, public code?:string) { super(message); }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers = new Headers(options.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) {
    let csrf: Response;
    try { csrf = await fetch('/api/auth/csrf', { credentials: 'include', cache: 'no-store' }); }
    catch { throw new ApiError(0, 'Connection lost. Check your network and try again.'); }
    if (!csrf.ok) {
      if (csrf.status >= 500) throw new ApiError(csrf.status, 'Account service is unavailable. Please try again shortly.');
      throw new ApiError(csrf.status, 'Could not establish a secure session. Please retry.');
    }
    const token = await csrf.json() as { token: string; headerName: string };
    headers.set(token.headerName, token.token);
  }
  if (typeof options.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  let response: Response;
  try { response = await fetch(`/api${path}`, { ...options, headers, credentials: 'include', cache: 'no-store' }); }
  catch { throw new ApiError(0, 'Connection lost. Check your network and try again.'); }
  if (response.status === 204) return undefined as T;
  const data = await response.json().catch(() => ({})) as { message?: string; code?:string; errors?: {field: string; message: string}[] };
  if (!response.ok) throw new ApiError(response.status,
    data.errors?.map(e => `${e.field}: ${e.message}`).join('. ') || data.message || 'Something went wrong. Please retry.',data.code);
  return data as T;
}

export interface Profile {
  id: string; displayName: string; username: string; email: string; createdAt: string;
  emailVerified?:boolean; verificationRequired?:boolean;
  avatar?:AvatarConfig;
  statistics: { gamesPlayed: number; totalWins: number; totalPoints: number; averageAccuracy?: number | null };
}
export interface Category { id: number; name: string; questionCount: number }
export interface RoomSummary { code: string; title: string; status: string; createdAt: string; host: boolean }
export interface Participant { username?:string; id: string; displayName: string; role: string; connected: boolean; left: boolean;avatar?:AvatarConfig }
export interface Ranking { username?:string; id: string; displayName: string; points: number; correctCount: number; rank: number;avatar?:AvatarConfig;forfeited?:boolean }
export interface Question { number: number; text: string; choices: string[]; difficulty: string; type: string; correctAnswer: string | null }
export interface AnswerAttempt { submissionId:string; outcome:'RETRY'|'CORRECT'|'INCORRECT'; retriesRemaining:number; acceptedAt:string;decisionExpiresAt?:string|null;questionTimerPaused?:boolean|null;pointsAvailable?:number;awardedPoints?:number|null }
export interface Room {
  independentFlow?:boolean;revealAnswers?:boolean;startsAt?:string|null;feedbackUntil?:string|null;playerCompleted?:boolean;personalScore?:number;playerProgress?:{id:string;questionNumber:number;completed:boolean;points:number;retriesRemaining:number;answeredQuestions?:number;correctAnswers?:number}[];
  scoringVersion?:string;questionSource?:'OPENTDB'|'LOCAL';quizId?:string|null;quizVersion?:number|null;openTdbCategoryId?:number|null;difficulty?:string;questionType?:string;pointsAvailable?:number;awardedPoints?:number|null;
  code: string; title: string; status: string; hostId: string; invitationUrl: string;
  maxPlayers: number; questionCount: number; secondsPerQuestion: number;
  deadline: string | null; serverTime: string; participants: Participant[]; leaderboard: Ranking[];
  question: Question | null; submittedIndex: number | null;
  retryAllowance?:number; retriesRemaining?:number; questionFinalized?:boolean; lastAttempt?:AnswerAttempt|null;
}
export interface History { roomCode: string; title: string; startedAt: string; finishedAt: string; points: number|null; rank: number|null;forfeited?:boolean;hosted?:boolean }
export interface Analytics { category: string; answered: number; correct: number }
