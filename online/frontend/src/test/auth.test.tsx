import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { AuthProvider, ToastProvider } from '../context';
import * as brandConfig from '../config/mascotScenes';

function mount(path:string){render(<MemoryRouter initialEntries={[path]}><ToastProvider><AuthProvider><App/></AuthProvider></ToastProvider></MemoryRouter>);}
const profile={id:'account',displayName:'Test Player',username:'player',email:'player@example.com',createdAt:'2026-11-14T00:00:00Z',statistics:{gamesPlayed:0,totalPoints:0,totalWins:0}};
let signedIn=false;
beforeEach(()=>{signedIn=false;localStorage.clear();sessionStorage.clear();vi.stubGlobal('fetch',vi.fn(async(input:string,options?:RequestInit)=>{
  if(input==='/api/me')return Response.json(signedIn?profile:{message:'Sign in'},{status:signedIn?200:401});
  if(input==='/api/auth/csrf')return Response.json({token:'csrf',headerName:'X-CSRF-TOKEN'});
  if(input==='/api/auth/login'){const form=options?.body as URLSearchParams;if(form.get('password')==='ValidPassword42!'){signedIn=true;return Response.json({authenticated:true});}return Response.json({message:'Invalid username/email or password'},{status:401});}
  if(input==='/api/auth/register')return Response.json({ticket:'test-ticket',maskedEmail:'p***@example.com',purpose:'EMAIL_VERIFICATION',deliveryStatus:'SENT',expiresAt:new Date(Date.now()+600000).toISOString(),resendAt:new Date(Date.now()+60000).toISOString()},{status:201});
  if(input==='/api/rooms')return Response.json([]);
  return Response.json({});
}));});
// Keep keyboard events, without synthetic inter-key setTimeout delays in these flow tests.
afterEach(()=>{vi.unstubAllGlobals();vi.restoreAllMocks();});
it('redirects a protected room page to login',async()=>{mount('/create');expect(await screen.findByRole('heading',{name:'Welcome back'})).toBeInTheDocument();expect(screen.getByRole('link',{name:'Forgot password?'})).toBeInTheDocument();expect(screen.queryByRole('link',{name:'Verify email'})).not.toBeInTheDocument();});
it('toggles password visibility without submitting credentials',async()=>{mount('/login');const user=userEvent.setup({delay:null});const password=screen.getByLabelText('Password',{exact:true});expect(password).toHaveAttribute('type','password');await user.click(screen.getByRole('button',{name:'Show password'}));expect(password).toHaveAttribute('type','text');await user.click(screen.getByRole('button',{name:'Hide password'}));expect(password).toHaveAttribute('type','password');expect(vi.mocked(fetch).mock.calls.filter(([url])=>url==='/api/auth/login')).toHaveLength(0);});
it('rejects mismatched passwords before contacting registration',async()=>{
  mount('/register');const user=userEvent.setup({delay:null});
  await user.type(screen.getByLabelText('Display name'),'Test Player');await user.type(screen.getByLabelText('Username'),'player');
  await user.type(screen.getByLabelText('Email address'),'player@example.com');
  await user.type(screen.getByLabelText('Password',{exact:true}),'ValidPassword42!');
  await user.type(screen.getByLabelText('Confirm password'),'DifferentPassword42!');
  await user.click(screen.getByRole('button',{name:'Create account'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('Passwords must match.');
  expect(vi.mocked(fetch).mock.calls.filter(([url])=>url==='/api/auth/register')).toHaveLength(0);
},10000);
it('shows login errors and then signs in with a cookie session',async()=>{
  mount('/login');const user=userEvent.setup({delay:null});
  await user.type(screen.getByLabelText('Email or username'),'player');
  const password=screen.getByLabelText('Password',{exact:true});await user.type(password,'wrong');
  await user.click(screen.getByRole('button',{name:'Sign in'}));expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username/email or password');
  await user.clear(password);await user.type(password,'ValidPassword42!');await user.click(screen.getByRole('button',{name:'Sign in'}));
  await waitFor(()=>expect(screen.getByRole('heading',{name:/Hey, Test/})).toBeInTheDocument(),{timeout:3000});
  expect(localStorage.length).toBe(0);
});

it('protects the mascot view while entering a hidden password and clears it when visibility is enabled',async()=>{
  vi.spyOn(brandConfig,'hasBrandAsset').mockReturnValue(true);
  mount('/login');const user=userEvent.setup({delay:null});
  await user.type(screen.getByLabelText('Password',{exact:true}),'private');
  expect(document.querySelectorAll('[data-scene="login"][data-private="true"]')).toHaveLength(2);
  await user.click(screen.getByRole('button',{name:'Show password'}));
  expect(document.querySelector('[data-private="true"]')).toBeNull();
  expect(document.querySelector('[data-event="success"]')).toBeNull();
});

it('uses the approved owl and requires email verification after registration',async()=>{
  vi.spyOn(brandConfig,'hasBrandAsset').mockReturnValue(false);
  mount('/register');const user=userEvent.setup({delay:null});
  await user.type(screen.getByLabelText('Display name'),'Test Player');
  await user.type(screen.getByLabelText('Username'),'player');
  await user.type(screen.getByLabelText('Email address'),'player@example.com');
  await user.type(screen.getByLabelText('Password',{exact:true}),'ValidPassword42!');
  await user.type(screen.getByLabelText('Confirm password'),'ValidPassword42!');
  await user.click(screen.getByRole('button',{name:'Create account'}));
  expect(vi.mocked(fetch).mock.calls.filter(([url])=>url==='/api/auth/register')).toHaveLength(1);
  expect(await screen.findByRole('heading',{name:'Check your email'},{timeout:3000})).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'Verify Email'})).toBeDisabled();
  expect(document.querySelector('[data-event="success"]')).toBeNull();
});

it('renders the approved SVG on login without a footer or monogram even when PNG files are missing',async()=>{
  vi.spyOn(brandConfig,'hasBrandAsset').mockReturnValue(false);
  mount('/login');
  expect(await screen.findByRole('heading',{name:'Welcome back'})).toBeInTheDocument();
  expect(document.querySelector('footer,[role="contentinfo"],.hootly-mascot-placeholder,.hootly-monogram')).toBeNull();
  expect(screen.getByRole('link',{name:'Hootly home'})).toBeInTheDocument();
  expect(document.querySelector('.auth-owl-stage .hootly-character svg')).toBeInTheDocument();
});
