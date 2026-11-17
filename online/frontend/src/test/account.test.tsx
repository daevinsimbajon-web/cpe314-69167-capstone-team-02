import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { AuthProvider, ToastProvider } from '../context';

let signedIn:boolean,displayName:string;
const requests:{path:string;body:unknown}[]=[];
const profile=()=>({id:'account',displayName,username:'player',email:'player@example.com',createdAt:'2026-11-14T00:00:00Z',statistics:{gamesPlayed:2,totalWins:1,totalPoints:15,averageAccuracy:75}});
function mount(path='/dashboard'){render(<MemoryRouter initialEntries={[path]}><ToastProvider><AuthProvider><App/></AuthProvider></ToastProvider></MemoryRouter>);}
beforeEach(()=>{
  signedIn=true;displayName='Test Player';requests.length=0;
  vi.stubGlobal('fetch',vi.fn(async(path:string,options?:RequestInit)=>{
    if(path==='/api/auth/csrf')return Response.json({token:'csrf',headerName:'X-CSRF-TOKEN'});
    if(path==='/api/me'&&options?.method==='PATCH'){const body=JSON.parse(String(options.body));requests.push({path,body});displayName=body.displayName;return Response.json(profile());}
    if(path==='/api/me')return Response.json(signedIn?profile():{message:'Please sign in'},{status:signedIn?200:401});
    if(path==='/api/auth/logout'){requests.push({path,body:null});signedIn=false;return new Response(null,{status:204});}
    if(path==='/api/account/me'){
      const body=JSON.parse(String(options?.body));requests.push({path,body});
      if(body.password!=='CorrectPassword42!')return Response.json({message:'Your password is incorrect. Your account has not been deleted.'},{status:403});
      signedIn=false;return new Response(null,{status:204});
    }
    if(path==='/api/rooms')return Response.json([]);
    if(path==='/api/history')return Response.json([{roomCode:'ABC234XY',title:'Friday round',startedAt:'2026-11-14T00:00:00Z',finishedAt:'2026-11-14T00:05:00Z',points:15,rank:1}]);
    return Response.json([]);
  }));
});
afterEach(()=>vi.unstubAllGlobals());

it('opens the actual user menu, supports keyboard navigation, Escape and outside clicks',async()=>{
  mount();const user=userEvent.setup(),trigger=await screen.findByRole('button',{name:'Open account menu'});
  trigger.focus();await user.keyboard('{ArrowDown}');
  const menu=screen.getByRole('menu',{name:'Account'});
  expect(within(menu).getByText('player@example.com')).toBeInTheDocument();
  expect(screen.getByRole('menuitem',{name:'My Profile'})).toHaveFocus();
  await user.keyboard('{ArrowUp}');expect(screen.getByRole('menuitem',{name:'Log out'})).toHaveFocus();
  await user.keyboard('{Escape}');expect(trigger).toHaveFocus();expect(trigger).toHaveAttribute('aria-expanded','false');
  await user.click(trigger);expect(trigger).toHaveAttribute('aria-expanded','true');
  await user.click(screen.getByRole('heading',{name:/Hey, Test/}));expect(trigger).toHaveAttribute('aria-expanded','false');
  await user.click(trigger);await user.click(screen.getByRole('menuitem',{name:'My Profile'}));
  expect(await screen.findByRole('heading',{name:'My profile'})).toBeInTheDocument();
});

it('logs out through the backend and prevents returning to protected content',async()=>{
  mount();const user=userEvent.setup();await user.click(await screen.findByRole('button',{name:'Open account menu'}));
  await user.click(screen.getByRole('menuitem',{name:'Log out'}));
  expect(await screen.findByRole('heading',{name:/Bring your people/,level:1})).toBeInTheDocument();
  expect(requests.filter(request=>request.path==='/api/auth/logout')).toHaveLength(1);
  await user.click(screen.getByRole('link',{name:'Join a room'}));
  expect(await screen.findByRole('heading',{name:'Welcome back'})).toBeInTheDocument();
});

it('shows backend profile statistics/history and persists a validated display-name edit',async()=>{
  mount('/profile');const user=userEvent.setup();
  expect(await screen.findByRole('heading',{name:'My profile'})).toBeInTheDocument();
  expect(screen.getByText('Average accuracy')).toBeInTheDocument();
  expect(await screen.findByText('Friday round')).toBeInTheDocument();
  const name=screen.getByLabelText('Display name');await user.clear(name);await user.type(name,'   ');
  expect(screen.getByRole('button',{name:'Save profile'})).toBeDisabled();expect(name).toHaveAttribute('maxlength','40');
  expect(requests).toHaveLength(0);await user.clear(name);await user.type(name,'Updated Player');
  await user.dblClick(screen.getByRole('button',{name:'Save profile'}));
  await waitFor(()=>expect(screen.getByRole('heading',{name:'Updated Player'})).toBeInTheDocument());
  expect(requests.filter(request=>request.path==='/api/me')).toEqual([{path:'/api/me',body:{displayName:'Updated Player',username:'player'}}]);
});

it('cancels account deletion without a request and requires both password and exact confirmation',async()=>{
  mount('/settings');const user=userEvent.setup();await user.click(await screen.findByRole('button',{name:'Delete account'}));
  const dialog=screen.getByRole('dialog');expect(within(dialog).getByRole('button',{name:'Delete my account'})).toBeDisabled();
  await user.type(screen.getByLabelText('Type DELETE to confirm'),'DELETE');expect(within(dialog).getByRole('button',{name:'Delete my account'})).toBeDisabled();
  await user.type(screen.getByLabelText('Current password'),'CorrectPassword42!');expect(within(dialog).getByRole('button',{name:'Delete my account'})).toBeEnabled();
  await user.click(within(dialog).getByRole('button',{name:'Cancel'}));expect(requests).toHaveLength(0);
  expect(screen.getByRole('button',{name:'Delete account'})).toBeInTheDocument();
});

it('keeps the account on a wrong password and clears authentication only after backend deletion succeeds',async()=>{
  mount('/settings');const user=userEvent.setup();await user.click(await screen.findByRole('button',{name:'Delete account'}));
  await user.type(screen.getByLabelText('Type DELETE to confirm'),'DELETE');
  const password=screen.getByLabelText('Current password');await user.type(password,'WrongPassword42!');
  await user.click(screen.getByRole('button',{name:'Delete my account'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('incorrect');expect(signedIn).toBe(true);
  await user.clear(password);await user.type(password,'CorrectPassword42!');await user.click(screen.getByRole('button',{name:'Delete my account'}));
  expect(await screen.findByRole('heading',{name:/Bring your people/,level:1})).toBeInTheDocument();expect(signedIn).toBe(false);
  expect(requests.filter(request=>request.path==='/api/account/me')).toHaveLength(2);
});
