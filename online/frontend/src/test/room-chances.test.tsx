import {afterEach,expect,it,vi} from 'vitest';
import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {RoomPage} from '../RoomPages';
vi.mock('../context',()=>({useAuth:()=>({user:{id:'player',displayName:'Player'},refresh:vi.fn()}),useToast:()=>vi.fn()}));
class Socket {onopen=null;onclose=null;onmessage=null;onerror=null;close(){};}
afterEach(()=>vi.unstubAllGlobals());
it('uses the retry response immediately so the last chance stays at zero',async()=>{
  const now=Date.now();
  const pending={code:'TLFSKBMH',title:'Quiz',status:'QUESTION',hostId:'host',invitationUrl:'/',maxPlayers:2,questionCount:2,secondsPerQuestion:20,deadline:new Date(now+25000).toISOString(),serverTime:new Date(now).toISOString(),startsAt:new Date(now-5000).toISOString(),participants:[],leaderboard:[],independentFlow:true,revealAnswers:true,retryAllowance:1,retriesRemaining:1,personalScore:0,questionFinalized:false,submittedIndex:1,question:{number:1,text:'The Sun is a star.',choices:['True','False'],difficulty:'easy',type:'TRUE_FALSE',correctAnswer:null},lastAttempt:{submissionId:'attempt',outcome:'RETRY',retriesRemaining:1,acceptedAt:new Date(now).toISOString(),decisionExpiresAt:new Date(now+5000).toISOString(),questionTimerPaused:true}};
  let retryCalls=0;
  vi.stubGlobal('WebSocket',Socket);
  vi.stubGlobal('fetch',vi.fn(async(input:string,options?:RequestInit)=>{
    if(input==='/api/auth/csrf')return Response.json({token:'test',headerName:'X-CSRF-TOKEN'});
    if(input==='/api/rooms/TLFSKBMH/retry'&&options?.method==='POST'){retryCalls++;return Response.json({...pending,retriesRemaining:0,submittedIndex:null,lastAttempt:{...pending.lastAttempt,retriesRemaining:0,decisionExpiresAt:null}});}
    if(input==='/api/rooms/TLFSKBMH')return Response.json(pending);
    throw Error(`Unexpected request: ${input}`);
  }));
  render(<MemoryRouter initialEntries={['/rooms/TLFSKBMH']}><Routes><Route path="/rooms/:code" element={<RoomPage/>}/></Routes></MemoryRouter>);
  await screen.findByRole('dialog',{name:'Use a chance?'});
  await userEvent.click(screen.getByRole('button',{name:'Yes, use a chance'}));
  await waitFor(()=>expect(screen.getByText(/No chances left/)).toBeInTheDocument());
  expect(screen.queryByRole('dialog',{name:'Use a chance?'})).not.toBeInTheDocument();expect(retryCalls).toBe(1);
});
