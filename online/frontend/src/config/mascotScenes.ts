export type MascotSceneName = 'landing' | 'login' | 'registration' | 'dashboard' | 'create' | 'join' | 'lobby' | 'gameplay' | 'leaderboard' | 'results' | 'profile' | 'history' | 'settings' | 'error' | 'empty' | 'loading' | 'analytics' | 'bank';
export type MascotMood = 'idle' | 'welcoming' | 'curious' | 'focused' | 'happy' | 'sad' | 'surprised' | 'encouraging' | 'celebrating' | 'loading' | 'waving' | 'applauding';
export type MascotEventName = 'success' | 'selection' | 'playerJoined' | 'gameStarted' | 'correct' | 'incorrect' | 'timeout' | 'rankingChanged' | 'rankingRevealed' | 'finished' | 'won' | 'lost';
export interface MascotEvent { id: string; type: MascotEventName }

// The user approved the layered purple character and its contextual gestures.
export const mascotScenes: Record<MascotSceneName, { mood: MascotMood; x: number; y: number; rotate: number; duration: number }> = {
  landing: { mood: 'welcoming', x: 100, y: -24, rotate: 9, duration: .95 },
  login: { mood: 'welcoming', x: 0, y: 0, rotate: -3, duration: .35 },
  registration: { mood: 'curious', x: 0, y: 8, rotate: 5, duration: .5 },
  dashboard: { mood: 'idle', x: 0, y: 6, rotate: -2, duration: .5 },
  create: { mood: 'curious', x: 0, y: 0, rotate: 4, duration: .4 },
  join: { mood: 'curious', x: 30, y: 0, rotate: -10, duration: .6 },
  lobby: { mood: 'idle', x: 0, y: 4, rotate: 0, duration: .4 },
  gameplay: { mood: 'focused', x: 0, y: 0, rotate: 0, duration: 0 },
  leaderboard: { mood: 'focused', x: -8, y: 0, rotate: 0, duration: .35 },
  results: { mood: 'idle', x: 0, y: 12, rotate: 0, duration: .5 },
  profile: { mood: 'idle', x: 0, y: 0, rotate: -3, duration: .5 },
  history: { mood: 'curious', x: 0, y: 0, rotate: 6, duration: .6 },
  settings: { mood: 'idle', x: 0, y: 0, rotate: 0, duration: 0 },
  error: { mood: 'encouraging', x: 0, y: 0, rotate: -7, duration: .5 },
  empty: { mood: 'curious', x: 0, y: 0, rotate: 5, duration: .5 },
  loading: { mood: 'loading', x: 0, y: 0, rotate: 0, duration: 0 },
  analytics: { mood: 'focused', x: 0, y: 0, rotate: 3, duration: .4 },
  bank: { mood: 'curious', x: 0, y: 0, rotate: -4, duration: .4 },
};

export const reactionMoods: Record<MascotEventName, MascotMood> = {
  success: 'happy', selection: 'curious', playerJoined: 'waving', gameStarted: 'focused',
  correct: 'happy', incorrect: 'sad', timeout: 'surprised', rankingChanged: 'applauding',
  rankingRevealed: 'applauding', finished: 'idle', won: 'celebrating', lost: 'sad',
};

export function reactionDuration(type:MascotEventName,scene:MascotSceneName){
  if(type==='won'||(type==='success'&&['registration','create'].includes(scene)))return 1600;
  if(type==='lost'||type==='incorrect')return 1400;
  if(type==='selection')return 500;
  return 1150;
}

export const BRAND_ASSETS = {
  logo: '/brand/hootly-logo.png', icon: '/brand/hootly-owl-icon.png',
  favicon: '/favicon.ico', appleTouchIcon: '/apple-touch-icon.png',
} as const;

// Public files are URL resources, not module imports. The provider refreshes this
// startup hint from the live public-file manifest when artwork is added later.
const initialBrandPaths: string[] = import.meta.env.HOOTLY_BRAND_FILES ?? [];
export const hasBrandAsset = (path: string) => initialBrandPaths.includes(path);
