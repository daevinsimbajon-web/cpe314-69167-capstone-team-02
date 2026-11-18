import { afterEach,expect,it,vi } from 'vitest';
import { act,fireEvent,render,screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { GameExperience } from '../components/game/GameExperience';
import { MotionProvider } from '../components/ui/MotionProvider';
import type { Room } from '../api';
let userId='player';
vi.mock('../context',()=>({useAuth:()=>({user:{id:userId}})}));
const room:Room={code:'TLFSKBMH',title:'Our quiz',status:'QUESTION',hostId:'host',invitationUrl:'/join/TLFSKBMH',maxPlayers:2,questionCount:2,secondsPerQuestion:20,deadline:new Date(Date.now()+20000).toISOString(),serverTime:new Date().toISOString(),participants:[],leaderboard:[{id:'player',displayName:'Player',rank:0,points:0,correctCount:0}],question:{number:1,text:'The Sun is a star.',choices:['True','False'],difficulty:'easy',type:'TRUE_FALSE',correctAnswer:null},submittedIndex:null,independentFlow:true,revealAnswers:false,retryAllowance:2,retriesRemaining:2,personalScore:0,startsAt:new Date(Date.now()-5000).toISOString()};
function mount(value:Room,commands=vi.fn().mockResolvedValue(undefined),answer=vi.fn().mockResolvedValue(undefined),reload=vi.fn().mockResolvedValue(undefined)){render(<MemoryRouter><MotionProvider><GameExperience room={value} busy={false} onCommand={commands} onAnswer={answer} onReload={reload}/></MotionProvider></MemoryRouter>);return {commands,answer,reload};}
afterEach(()=>{vi.useRealTimers();userId='player';delete document.documentElement.dataset.reducedMotion;});
it('uses the same persisted GO time and requests the first question only when it arrives',async()=>{
  document.documentElement.dataset.reducedMotion='true';vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-17T00:00:00Z'));const {reload}=mount({...room,status:'COUNTDOWN',question:null,serverTime:new Date().toISOString(),startsAt:new Date(Date.now()+5000).toISOString()});
  expect(document.querySelector('.ready-number')).toHaveTextContent('READY?');
  for(const label of ['3','2','1','GO!']){await act(()=>vi.advanceTimersByTimeAsync(1000));expect(document.querySelector('.ready-number')).toHaveTextContent(label);}
  expect(reload).not.toHaveBeenCalled();await act(()=>vi.advanceTimersByTimeAsync(1000));expect(reload).toHaveBeenCalledTimes(1);expect(screen.queryByRole('button',{name:/True/})).not.toBeInTheDocument();
});
it('shows five seconds for a chance while the question timer is paused',async()=>{
  vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-17T00:00:00Z'));
  const acceptedAt=new Date().toISOString();
  const {commands,answer}=mount({...room,serverTime:acceptedAt,deadline:new Date(Date.now()+25000).toISOString(),submittedIndex:1,lastAttempt:{submissionId:'attempt',outcome:'RETRY',retriesRemaining:2,acceptedAt,decisionExpiresAt:new Date(Date.now()+5000).toISOString(),questionTimerPaused:true},questionFinalized:false});
  expect(screen.getByRole('dialog')).toHaveAccessibleName('Use a chance?');expect(commands).not.toHaveBeenCalled();expect(answer).not.toHaveBeenCalled();
  expect(screen.getByRole('timer',{name:'Chance decision time'})).toHaveTextContent('5 seconds');expect(screen.getByLabelText('Time remaining: 20 seconds')).toBeInTheDocument();
  await act(()=>vi.advanceTimersByTimeAsync(1000));expect(screen.getByRole('timer',{name:'Chance decision time'})).toHaveTextContent('4 seconds');expect(screen.getByLabelText('Time remaining: 20 seconds')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Yes, use a chance'}));expect(commands).toHaveBeenCalledWith('retry',{submissionId:'attempt',useChance:true});expect(screen.queryByLabelText('Correct answer')).not.toBeInTheDocument();
});
it('shows the final leaderboard and personal result to a player',()=>{
  mount({...room,status:'FINISHED',question:null,personalScore:10,leaderboard:[{id:'player',displayName:'Player',rank:1,points:10,correctCount:1},{id:'other',displayName:'Other player',rank:2,points:5,correctCount:1}]});
  expect(screen.getByText('You’ve finished!')).toBeInTheDocument();expect(screen.getByRole('heading',{name:'Final standings'})).toBeInTheDocument();expect(screen.getByText('Other player')).toBeInTheDocument();
});
it('lets players continue at zero chances and hides host data and reveal timers',()=>{
  mount({...room,retriesRemaining:0});expect(screen.getByText(/No chances left/)).toBeInTheDocument();expect(screen.getByRole('button',{name:/True/})).toBeEnabled();expect(screen.queryByText('Live leaderboard')).not.toBeInTheDocument();expect(screen.queryByText('Full leaderboard')).not.toBeInTheDocument();expect(screen.queryByText('REVEAL')).not.toBeInTheDocument();
});
it('provides final incorrect feedback without leaking the solution when reveal is off',()=>{
  mount({...room,submittedIndex:1,questionFinalized:true,awardedPoints:0,lastAttempt:{submissionId:'last',outcome:'INCORRECT',retriesRemaining:0,acceptedAt:room.serverTime}});
  expect(screen.getByText('Incorrect!')).toBeInTheDocument();expect(screen.getByLabelText('Incorrect answer')).toBeInTheDocument();expect(screen.queryByLabelText('Correct answer')).not.toBeInTheDocument();expect(screen.queryByLabelText(/Time remaining/)).not.toBeInTheDocument();
});
it('renders a spectator dashboard for the host without answer controls',()=>{
  userId='host';mount({...room,question:null,playerProgress:[{id:'player',questionNumber:1,completed:false,points:0,retriesRemaining:2}]});expect(screen.getByRole('heading',{name:'Live leaderboard'})).toBeInTheDocument();expect(screen.queryByRole('button',{name:/True/})).not.toBeInTheDocument();expect(screen.getByText(/Question 1 of 2/)).toBeInTheDocument();
});
