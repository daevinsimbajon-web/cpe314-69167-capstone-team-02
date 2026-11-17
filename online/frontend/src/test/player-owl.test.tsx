import { afterEach,expect,it,vi } from 'vitest';
import { StrictMode } from 'react';
import { act,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PlayerOwl } from '../components/avatar/PlayerOwl';
import { AvatarEditor } from '../components/avatar/AvatarEditor';
import { avatarFor,readAvatar,surpriseAvatar } from '../components/avatar/avatarConfig';
import { rankingReactions } from '../components/game/useLeaderboardReactions';
import { Leaderboard } from '../components/game/Leaderboard';
import { AuthProvider,ToastProvider } from '../context';
import { mergeExport,parseStats } from '../analytics/import';
import { awards,periodFor } from '../analytics/model';
import { MotionProvider } from '../components/ui/MotionProvider';
import { birdSpecies } from '../components/avatar/avatarConfig';

afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();delete document.documentElement.dataset.reducedMotion;});
const config={version:1 as const,body:3,eyes:2,iris:1,wings:2,marking:1,accessory:4,expression:0};
it('generates bounded deterministic avatars with broad variety and validates untrusted configs',()=>{
  const variants=new Set(Array.from({length:1000},(_,i)=>JSON.stringify(avatarFor(`player-${i}`))));expect(variants.size).toBeGreaterThan(900);
  expect(avatarFor('same')).toEqual(avatarFor('same'));expect(readAvatar({...config,body:9})).toBeUndefined();expect(readAvatar({...config,accessory:null})).toBeUndefined();expect(readAvatar({...config,version:2})).toBeUndefined();expect(surpriseAvatar(config)).not.toEqual(config);
});
it('keeps old saved avatars unchanged and validates all eight optional species',()=>{
  expect(readAvatar(config)).toEqual(config);
  for(let species=0;species<8;species++)expect(readAvatar({...config,species})).toEqual({...config,species});
  for(const species of [-1,8,1.5,'1',null])expect(readAvatar({...config,species})).toBeUndefined();
  for(let i=0;i<30;i++){const next=surpriseAvatar(config);expect(next.species).toBeGreaterThanOrEqual(0);expect(next.species).toBeLessThan(8);}
});
it('renders genuinely different silhouettes with one palette and updates species through memoization',()=>{
  const {container,rerender}=render(<PlayerOwl identity="same" avatar={config}/>),paths=new Set<string>();
  for(let species=0;species<8;species++){
    rerender(<PlayerOwl identity="same" avatar={{...config,species}} name="Same player"/>);
    expect(container.querySelector('.player-owl')).toHaveAttribute('data-species',birdSpecies[species]);
    expect(container.querySelector('[data-part="player-silhouette"]')).toHaveAttribute('fill','#377f8c');
    paths.add(container.querySelector('[data-part="player-silhouette"]')!.getAttribute('d')!);
  }
  expect(paths.size).toBe(8);
});
it('keeps colors/accessories/identity across facial states and finite independent wing/body reactions',async()=>{
  vi.useFakeTimers();const {container,rerender}=render(<StrictMode><PlayerOwl identity="a" avatar={config} name="A" reaction={{id:'won',type:'first'}}/></StrictMode>);
  const signature=container.querySelector('.player-owl')!.getAttribute('data-avatar');expect(container.querySelector('[data-reaction="first"]')).toBeInTheDocument();expect(container.querySelector('[data-part="player-left-wing"]')).toBeInTheDocument();expect(container.querySelector('[data-part="player-head"]')).toBeInTheDocument();
  await act(()=>vi.advanceTimersByTimeAsync(1600));expect(container.querySelector('.player-owl')).not.toHaveAttribute('data-reaction');
  rerender(<StrictMode><PlayerOwl identity="a" avatar={config} mood="encouraged" reaction={{id:'won',type:'first'}}/></StrictMode>);
  expect(container.querySelector('.player-owl')).toHaveAttribute('data-avatar',signature);expect(container.querySelector('.player-owl')).toHaveAttribute('data-expression','encouraged');expect(container.querySelector('.player-owl')).not.toHaveAttribute('data-reaction');
});
it('respects device reduced motion for a winner reaction',()=>{document.documentElement.dataset.reducedMotion='true';const {container}=render(<MotionProvider><PlayerOwl identity="a" avatar={config} reaction={{id:'quiet',type:'first'}} mood="happy"/></MotionProvider>);expect(container.querySelector('.player-owl')).toHaveAttribute('data-quiet','true');expect(container.querySelector('.player-owl')).not.toHaveAttribute('data-reaction');});
const first=[{id:'a',displayName:'Same Name',rank:1,points:10,correctCount:1,avatar:config},{id:'b',displayName:'Same Name',rank:2,points:5,correctCount:1,avatar:{...config,body:5}}];
it('derives one new leader reaction from actual ranks and reserves full sequences for completion',()=>{
  expect(rankingReactions(null,first,false,false)).toEqual({});const next=[{...first[1],rank:1,points:15},{...first[0],rank:2}];
  expect(rankingReactions(first,next,false,false)).toEqual({b:{type:'leader',delta:10}});expect(rankingReactions(next,next,false,false)).toEqual({});expect(rankingReactions(next,next,false,true)).toMatchObject({b:{type:'first'},a:{type:'second'}});expect(rankingReactions(next,next,true,true)).toEqual({});
});
it('reorders by actual identity without swapping the saved owls and does not replay completed podiums on mount',async()=>{
  const {container,rerender}=render(<Leaderboard rows={first} sessionKey="room"/>);const a=container.querySelector('[data-player-id="a"]')!,signature=a.querySelector('.player-owl')!.getAttribute('data-avatar');
  const next=[{...first[1],rank:1,points:15},{...first[0],rank:2}];rerender(<Leaderboard rows={next} sessionKey="room"/>);expect(container.querySelectorAll('[data-player-id]')[0]).toHaveAttribute('data-player-id','b');expect(container.querySelector('[data-player-id="a"] .player-owl')).toHaveAttribute('data-avatar',signature);expect(container.querySelector('[data-reaction="leader"]')).not.toBeInTheDocument();
  rerender(<Leaderboard rows={next} final sessionKey="different-room"/>);expect(container.querySelector('[data-reaction="first"]')).not.toBeInTheDocument();
});
it('previews and cancels locally, saving only on explicit confirmation',async()=>{
  let avatar=config;const saved:unknown[]=[];
  vi.stubGlobal('fetch',vi.fn(async(path:string,options?:RequestInit)=>{if(path==='/api/auth/csrf')return Response.json({token:'t',headerName:'X-CSRF-TOKEN'});if(path==='/api/me/avatar'){avatar=JSON.parse(String(options?.body));saved.push(avatar);return Response.json({});}if(path==='/api/me')return Response.json({id:'account',displayName:'Player',username:'player',avatar,statistics:{gamesPlayed:0,totalWins:0,totalPoints:0}});throw Error(path);}));
  render(<MemoryRouter><ToastProvider><AuthProvider><AvatarWhenReady/></AuthProvider></ToastProvider></MemoryRouter>);
  await screen.findByRole('button',{name:'Surprise Me'});fireEvent.click(screen.getByRole('button',{name:'Surprise Me'}));expect(screen.getByRole('status')).toHaveTextContent('Preview only');expect(saved).toHaveLength(0);fireEvent.click(screen.getByRole('button',{name:'Cancel avatar change'}));expect(saved).toHaveLength(0);
  fireEvent.click(screen.getByRole('button',{name:'Surprise Me'}));fireEvent.click(screen.getByRole('button',{name:'Save avatar'}));await waitFor(()=>expect(saved).toHaveLength(1));await waitFor(()=>expect(screen.queryByRole('button',{name:'Cancel avatar change'})).not.toBeInTheDocument());
});
import { useAuth } from '../context';
function AvatarWhenReady(){const {user}=useAuth();return user?<AvatarEditor/>:null;}
it('carries optional avatars through exports and keeps same-name award identities distinct',()=>{
  const make=(id:string,avatar= config)=>parseStats(JSON.stringify({format:'hootly.analytics',schemaVersion:1,exportedAt:'2026-11-16T00:00:00Z',player:{exportPlayerId:id,displayName:'Same Name',avatar},games:[{gameId:'g',playedAt:'2026-11-16T00:00:00Z',pointsEarned:5,maxPossiblePoints:5,won:true,scoringVersion:'retry-v1',presentedQuestions:1,questionsComplete:true,questions:[{questionId:'q',category:'Science',firstTryCorrect:true,finalCorrect:true,attemptCount:1,retriesUsed:0,elapsedMs:1000,pointsEarned:5,maxPossiblePoints:5,timedOut:false}]}]})).data;
  const one=mergeExport([],make('a')).players,two=mergeExport(one,make('b',{...config,body:5})).players;expect(two[1].avatar?.body).toBe(5);
  expect(awards(two,{period:periodFor('all'),category:'',mode:'',ruleset:''}).find(a=>a.title==='Points Leader')?.playerIds).toEqual(['a','b']);
});
it('uses curved clipped eyelids and clear glasses for every species without a rectangular eye seam',()=>{
  const {container,rerender}=render(<PlayerOwl identity="clean" avatar={config}/>);
  for(let species=0;species<8;species++){
    rerender(<PlayerOwl identity="clean" avatar={{...config,species,eyes:1}}/>);
    expect(container.querySelectorAll('path[data-part="player-eyelid"]')).toHaveLength(2);expect(container.querySelector('rect[data-part="player-eyelid"]')).toBeNull();expect(container.querySelector('[data-part="player-glasses"]')).toHaveAttribute('fill','none');expect(container.querySelector('.player-owl')).toHaveClass('player-owl-profile');
  }
});
it('uses the final rank facial layers for every species and saved eye style',()=>{
  const {container,rerender}=render(<PlayerOwl identity="ranked" avatar={config} rank={1}/>);
  for(let species=0;species<birdSpecies.length;species++)for(let eyes=0;eyes<4;eyes++)for(const rank of [1,2,3,4]){
    const avatar={...config,species,eyes,expression:3};
    rerender(<PlayerOwl identity="ranked" avatar={avatar} rank={rank} mood="sleepy"/>);
    const bird=container.querySelector('.player-owl')!;
    expect(bird).toHaveAttribute('data-species',birdSpecies[species]);
    expect(bird).toHaveAttribute('data-avatar',JSON.stringify(avatar));
    expect(bird).toHaveAttribute('data-expression',rank===1?'celebrating':rank===2?'disappointed':rank===3?'happy':'calm');
    expect(container.querySelectorAll('[data-part="player-happy-eye"]')).toHaveLength(rank===1||rank===3?2:0);
    expect(container.querySelectorAll('[data-part="player-rank-brow"]')).toHaveLength(rank===2?2:0);
    expect(container.querySelector('[data-part="player-mouth"]')).toHaveAttribute('d',rank===1||rank===3?'M72 116q8 11 16 0':rank===2?'M73 122q7-6 14 0':'M75 118q5 3 10 0');
    expect(container.querySelector('[data-part="player-glasses"]')).toBeInTheDocument();
    expect(container.querySelector('[data-part="player-silhouette"]')).toHaveAttribute('fill','#377f8c');
    expect(container.querySelector('[data-part="player-beak"]')).toBeInTheDocument();
  }
});
it('keeps final podium birds on distinct platforms, loops celebration only for completed standings and preserves ties',async()=>{
  const rows=[...first,{...first[0],id:'c',rank:3,points:2},{...first[0],id:'d',rank:4,points:0}];const {container,rerender}=render(<MotionProvider><Leaderboard rows={rows} final sessionKey="completed"/></MotionProvider>);
  expect(container.querySelectorAll('.podium-perch .player-owl-podium')).toHaveLength(3);expect(container.querySelectorAll('.podium-plinth')).toHaveLength(3);expect(container.querySelectorAll('.podium-confetti i')).toHaveLength(18);expect(container.querySelectorAll('[data-celebrating=true]')).toHaveLength(2);expect(container.querySelector('.podium-rank-1 .player-owl')).toHaveAttribute('data-expression','celebrating');expect(container.querySelector('.podium-rank-2 .player-owl')).toHaveAttribute('data-expression','disappointed');expect(container.querySelector('.podium-rank-3 .player-owl')).toHaveAttribute('data-expression','happy');expect(container.querySelector('[data-player-id="d"] .player-owl')).toHaveAttribute('data-expression','calm');expect(screen.getByRole('list',{name:'Other final rankings'}).children).toHaveLength(1);
  document.documentElement.dataset.reducedMotion='true';await waitFor(()=>expect(container.querySelector('.podium-confetti')).not.toBeInTheDocument());expect(container.querySelector('[data-celebrating=true]')).not.toBeInTheDocument();
  rerender(<MotionProvider><Leaderboard rows={[first[0],{...first[1],rank:1}]} final sessionKey="tied"/></MotionProvider>);expect(container.querySelectorAll('.podium-rank-1')).toHaveLength(2);
});
