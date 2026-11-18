import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ComparePlayersPage } from '../analytics/ComparePlayers';
import { ImportConflict, mergeExport, parseStats } from '../analytics/import';
import type { StatsExport } from '../analytics/model';
import type { SavedMember, ImportJob } from '../analytics/sharing';
import { api } from '../api';
vi.mock('../api',()=>({api:vi.fn()}));
let saved:SavedMember[]=[];
let jobs:ImportJob[]=[];
beforeEach(()=>{
  saved=[];jobs=[];
  vi.mocked(api).mockImplementation(async(path,options={})=>{
    if(path==='/analytics/comparisons')return structuredClone(saved);
    if(path==='/analytics/comparisons/import-jobs') {
      if(options.method!=='POST')return jobs;
    }
    if(path.startsWith('/analytics/comparisons/import-jobs')&&options.method==='POST'){
      try{
        const parsed=parseStats(options.body as string),expected=new URL('http://local'+path).searchParams.get('expected');
        if(expected&&expected!=='*'&&expected!==parsed.data.player.exportPlayerId)throw Error('This export belongs to a different player.');
        if(expected==='*'&&!saved.some(member=>member.snapshot.player.exportPlayerId===parsed.data.player.exportPlayerId))throw Error('Use Import Player Stats to add someone new.');
        const merged=mergeExport(saved.map(m=>({id:m.snapshot.player.exportPlayerId,label:m.label,games:m.snapshot.games,exportedAt:m.snapshot.exportedAt})),parsed.data);
        const current=saved.find(m=>m.snapshot.player.exportPlayerId===parsed.data.player.exportPlayerId);
        const member:SavedMember={membershipId:current?.membershipId??crypto.randomUUID(),snapshot:{...parsed.data,games:merged.players.find(p=>p.id===parsed.data.player.exportPlayerId)!.games},label:current?.label??parsed.data.player.displayName,verified:false,status:'SNAPSHOT',canRequest:false,lastSyncedAt:null};
        saved=current?saved.map(m=>m===current?member:m):[...saved,member];
        return {id:'job',status:'COMPLETED',progress:100,message:`${merged.newPlayer?'1 player imported. ':''}${merged.added?`${merged.added} new records added.`:'Already up to date · No new records found.'} ${merged.duplicates+parsed.duplicatesSkipped} duplicate records skipped.`};
      }catch(e){return {id:'job',status:'FAILED',progress:100,message:e instanceof ImportConflict?'Conflicting records found. Existing records kept; this file was not merged.':(e as Error).message};}
    }
    if(options.method==='DELETE'){saved=saved.filter(m=>!path.endsWith(m.membershipId));return;}
    if(path.endsWith('/request')){saved=saved.map(m=>path.includes(m.membershipId)?{...m,status:'PENDING',canRequest:false}:m);return;}
    if(path==='/analytics/comparisons/import-jobs/recovered')return {id:'recovered',status:'COMPLETED',progress:100,message:'History restored.'};
    throw Error('Unexpected API '+path);
  });
});

afterEach(()=>vi.restoreAllMocks());
const make=(id='alpha',games=1,year=2026):StatsExport=>({format:'hootly.analytics',schemaVersion:1,exportedAt:'2026-11-17T00:00:00Z',player:{exportPlayerId:id,displayName:id==='alpha'?'Alpha Owl':'Beta Raven',username:id+'_player'},games:Array.from({length:games},(_,index)=>({gameId:`${id}-${index}`,playedAt:`${year}-01-${String(index+1).padStart(2,'0')}T12:00:00Z`,pointsEarned:10,maxPossiblePoints:10,won:true,scoringVersion:'retry-v2',presentedQuestions:1,questionsComplete:true,questions:[{questionId:`q-${index}`,category:'Science',questionType:'TRUE_FALSE',firstTryCorrect:true,finalCorrect:true,attemptCount:1,retriesUsed:0,elapsedMs:900,pointsEarned:10,maxPossiblePoints:10,timedOut:false}]}))});
function file(data:unknown,name='stats.json'){const source=typeof data==='string'?data:JSON.stringify(data),value=new File([source],name,{type:'application/json'});Object.defineProperty(value,'text',{value:async()=>source});return value;}
async function mount(){const view=render(<MemoryRouter><ComparePlayersPage/></MemoryRouter>);await waitFor(()=>expect(screen.getByLabelText('JSON files')).toBeEnabled());return view;}

it('merges by player/game identity, reports new records and skips reordered duplicates atomically',()=>{
  const first=parseStats(JSON.stringify(make('alpha',2))).data,added=mergeExport([],first);expect(added).toMatchObject({added:2,duplicates:0,newPlayer:true});
  const duplicate=mergeExport(added.players,{...first,games:[...first.games].reverse()});expect(duplicate).toMatchObject({added:0,duplicates:2,newPlayer:false});expect(duplicate.players).toHaveLength(1);
  const next=mergeExport(duplicate.players,parseStats(JSON.stringify(make('alpha',3))).data);expect(next).toMatchObject({added:1,duplicates:2});expect(next.players[0].games).toHaveLength(3);
  const conflict=make('alpha',4);conflict.games[0].won=false;expect(()=>mergeExport(next.players,conflict)).toThrow(ImportConflict);expect(next.players[0].games).toHaveLength(3);expect(next.players[0].games[0].won).toBe(true);
});
it('ignores presentation order in duplicate questions and keeps new metadata from being downgraded',()=>{
  const data=make();data.games[0].questions.push({...data.games[0].questions[0],questionId:'second'});Object.assign(data.games[0],{pointsEarned:20,maxPossiblePoints:20,presentedQuestions:2});
  const accepted=mergeExport([],parseStats(JSON.stringify(data)).data);const reversed=structuredClone(data);reversed.games[0].questions.reverse();expect(mergeExport(accepted.players,parseStats(JSON.stringify(reversed)).data).duplicates).toBe(1);
  const older={...data,exportedAt:'2026-01-01T00:00:00Z',player:{...data.player,username:'old_name'}};expect(mergeExport(accepted.players,older).players[0].username).toBe('alpha_player');
  expect(()=>parseStats(JSON.stringify({...data,player:{...data.player,username:'<script>'}}))).toThrow('Username');
});
it('imports, detects the same export, updates a player and recalculates rankings without duplicate cards',async()=>{
  await mount();const user=userEvent.setup({delay:null});await user.upload(screen.getByLabelText('JSON files'),[file(make('alpha',2),'alpha.json'),file(make('beta'),'beta.json')]);expect(screen.getAllByRole('article').filter(card=>card.classList.contains('player-comparison'))).toHaveLength(2);
  expect(document.querySelectorAll('.player-comparison')[0]).toHaveAttribute('data-player-id','alpha');await user.upload(screen.getByLabelText('JSON files'),file(make('alpha',2)));expect(await screen.findByText(/Already up to date/)).toBeInTheDocument();expect(screen.getByText(/2 duplicate records skipped/)).toBeInTheDocument();
  await user.upload(screen.getByLabelText('JSON files'),file(make('beta',3)));expect(await screen.findByText(/2 new records added/)).toBeInTheDocument();expect(document.querySelectorAll('.player-comparison')).toHaveLength(2);expect(document.querySelectorAll('.player-comparison')[0]).toHaveAttribute('data-player-id','beta');
  expect(screen.queryByRole('button',{name:'Yearly Rankings'})).not.toBeInTheDocument();expect(screen.getByRole('heading',{name:'Compare Players'})).toBeInTheDocument();
});
it('rejects invalid files and conflicting updates, preserving accepted records and independent file imports',async()=>{
  await mount();const user=userEvent.setup({delay:null});await user.upload(screen.getByLabelText('JSON files'),[file('{','broken.json'),file(make())]);expect(screen.getByText(/1 accepted · 1 rejected/)).toBeInTheDocument();expect(document.querySelectorAll('.player-comparison')).toHaveLength(1);
  const conflict=make('alpha',2);conflict.games[0].won=false;await user.upload(screen.getByLabelText('JSON files'),file(conflict));expect(await screen.findByText(/Existing records kept/)).toBeInTheDocument();const card=document.querySelector('.player-comparison') as HTMLElement;expect(within(card).getByText('10')).toBeInTheDocument();expect(within(card).getByText('1')).toBeInTheDocument();
});
it('removes manual updates, comparison download and sharing actions while retaining consent-based updates',async()=>{
  await mount();const user=userEvent.setup({delay:null});await user.upload(screen.getByLabelText('JSON files'),file(make()));expect(screen.queryByRole('button',{name:/^Update (Player )?Stats$/i})).not.toBeInTheDocument();expect(screen.queryByLabelText('Update exported Hootly stats')).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:'Download comparison report'})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:'Share summary'})).not.toBeInTheDocument();
});
it('opens player details, filters available years and keeps insufficient players visible',async()=>{
  await mount();const user=userEvent.setup({delay:null});const data=make('alpha',2);data.games[0].playedAt='2025-01-01T12:00:00Z';await user.upload(screen.getByLabelText('JSON files'),file(data));await user.click(screen.getByRole('button',{name:'View stats for Alpha Owl'}));const dialog=screen.getByRole('dialog',{name:'Alpha Owl'});expect(within(dialog).queryByRole('button',{name:'Update Stats'})).not.toBeInTheDocument();expect(within(dialog).getByText('@alpha_player')).toBeInTheDocument();expect(within(dialog).getByText('First-try accuracy')).toBeInTheDocument();
  fireEvent.change(within(dialog).getByLabelText('Player stats period'),{target:{value:'year:2025'}});expect(within(dialog).getByText('10 / 10 original available points')).toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Close player stats'}));fireEvent.change(screen.getByLabelText('Ranking metric'),{target:{value:'rating'}});expect(document.querySelectorAll('.player-comparison')).toHaveLength(1);expect(screen.getByLabelText('Not ranked')).toBeInTheDocument();expect(screen.getAllByText(/Needs 10 games/).length).toBeGreaterThan(0);
});

it('restores saved verified players, requests consent once and persists pending state across remounts',async()=>{
  saved=[{membershipId:'verified-player',snapshot:make(),label:'Alpha Owl',verified:true,status:'SNAPSHOT',canRequest:true,lastSyncedAt:null}];
  const first=await mount();const user=userEvent.setup();await user.click(screen.getByRole('button',{name:'Request Auto-Update'}));
  await screen.findByText('Request Pending');expect(screen.queryByRole('button',{name:'Request Auto-Update'})).not.toBeInTheDocument();
  expect(vi.mocked(api).mock.calls.filter(([path])=>path.endsWith('/request'))).toHaveLength(1);
  first.unmount();await mount();expect(screen.getByText('Request Pending')).toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Remove Alpha Owl'}));expect(saved).toHaveLength(1);await user.click(screen.getByRole('button',{name:'Remove'}));
  await waitFor(()=>expect(document.querySelectorAll('.player-comparison')).toHaveLength(0));expect(saved).toHaveLength(0);
});
it('keeps unverified imports historical, shows denied/disconnected states and recovers background progress',async()=>{
  saved=[{membershipId:'old-history',snapshot:make(),label:'Alpha Owl',verified:false,status:'SNAPSHOT',canRequest:false,lastSyncedAt:null}];
  const first=await mount();expect(screen.getByText('Automatic updates are unavailable for this imported history.')).toBeInTheDocument();expect(screen.queryByText('Request Auto-Update')).not.toBeInTheDocument();first.unmount();
  saved[0]={...saved[0],verified:true,status:'DENIED'};const denied=await mount();expect(screen.getByText('Request Denied')).toBeInTheDocument();expect(screen.getByText(/seven-day cooldown/)).toBeInTheDocument();denied.unmount();
  saved[0]={...saved[0],status:'REVOKED'};jobs=[{id:'recovered',status:'PROCESSING',progress:45,message:null}];render(<MemoryRouter><ComparePlayersPage/></MemoryRouter>);expect(await screen.findByLabelText('JSON import progress')).toHaveAttribute('value','45');
  await screen.findByText(/History restored/);expect(screen.getByText('Sharing Disconnected')).toBeInTheDocument();
});
it('refreshes disconnected sharing and comparison scores on returning to the page',async()=>{
  saved=[{membershipId:'verified-player',snapshot:make(),label:'Alpha Owl',verified:true,status:'ALLOWED',canRequest:false,lastSyncedAt:null}];
  await mount();expect(screen.getByText('Auto-Update Enabled')).toBeInTheDocument();
  saved[0]={...saved[0],status:'REVOKED',canRequest:true,snapshot:make('alpha',2)};
  fireEvent.focus(window);
  expect(await screen.findByText('Sharing Disconnected')).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'Request Auto-Update'})).toBeEnabled();
  expect(within(document.querySelector('.player-comparison') as HTMLElement).getByText('20')).toBeInTheDocument();
});
