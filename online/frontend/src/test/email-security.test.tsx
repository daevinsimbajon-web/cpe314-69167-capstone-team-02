import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { useState } from 'react';
import { App } from '../App';
import { AuthProvider, ToastProvider } from '../context';
import { CodeInput } from '../components/security/CodeInput';
import { rememberVerification } from '../emailSecurity';

const challenge={ticket:'test-ticket',maskedEmail:'p•••@example.com',expiresAt:new Date(Date.now()+600000).toISOString(),resendAt:new Date(Date.now()+60000).toISOString(),purpose:'EMAIL_VERIFICATION' as const,deliveryStatus:'SENT'};
const profile={id:'account',displayName:'Test Player',username:'player',email:'player@example.com',emailVerified:false,createdAt:'2026-11-17T00:00:00Z',statistics:{gamesPlayed:0,totalPoints:0,totalWins:0}};
let signedIn=false;
function mount(path:string){render(<MemoryRouter initialEntries={[path]}><ToastProvider><AuthProvider><App/></AuthProvider></ToastProvider></MemoryRouter>);}
beforeEach(()=>{signedIn=false;sessionStorage.clear();vi.stubGlobal('fetch',vi.fn(async(input:string)=>{
  if(input==='/api/me')return Response.json(signedIn?profile:{message:'Sign in'},{status:signedIn?200:401});
  if(input==='/api/auth/csrf')return Response.json({token:'csrf',headerName:'X-CSRF-TOKEN'});
  if(input==='/api/auth/password/forgot')return Response.json({...challenge,purpose:'PASSWORD_RESET',deliveryStatus:'ACCEPTED'});
  if(input==='/api/auth/password/verify')return Response.json({authorized:true});
  if(input==='/api/auth/password/reset'){signedIn=false;return Response.json({passwordChanged:true});}
  if(input==='/api/auth/email/verify'){signedIn=true;return Response.json({verified:true,authenticated:true});}
  if(input==='/api/auth/email/resend')return Response.json({...challenge,resendAt:new Date(Date.now()+60000).toISOString()});
  return Response.json([]);
}));});
afterEach(()=>{vi.unstubAllGlobals();});
it('accepts a full pasted code, advances focus and supports backspace navigation',async()=>{
  function Wrapper(){const [value,setValue]=useState('');return <CodeInput value={value} onChange={setValue}/>;}
  render(<Wrapper/>);const user=userEvent.setup({delay:null});await user.click(screen.getByLabelText('Digit 1'));await user.paste('123456');
  expect(screen.getByLabelText('Digit 6')).toHaveValue('6');expect(screen.getByLabelText('Digit 6')).toHaveFocus();
  await user.keyboard('{Backspace}{Backspace}');expect(screen.getByLabelText('Digit 5')).toHaveFocus();expect(screen.getByLabelText('Digit 5')).toHaveValue('');
  expect(screen.getByLabelText('Digit 1')).toHaveAttribute('autocomplete','one-time-code');
});
it('reports provider acceptance without claiming inbox delivery',async()=>{
  rememberVerification({...challenge,deliveryStatus:'ACCEPTED'});mount('/verify-email');
  expect(await screen.findByText('Your email was accepted for delivery. Check your inbox and spam folder.')).toBeInTheDocument();
});
it('asks the user to check their inbox if provider acceptance is uncertain',async()=>{
  rememberVerification({...challenge,deliveryStatus:'UNKNOWN'});mount('/verify-email');
  expect(await screen.findByText("We couldn't confirm whether your code was sent. Check your inbox before requesting another.")).toBeInTheDocument();
});
it('shows a resend countdown and celebrates only after confirmed email verification',async()=>{
  rememberVerification({...challenge,resendAt:new Date(Date.now()+2000).toISOString()});mount('/verify-email');const user=userEvent.setup({delay:null});
  expect(await screen.findByRole('button',{name:/Try again in/})).toBeDisabled();expect(document.querySelector('.is-celebrating')).toBeNull();
  const resend=await screen.findByRole('button',{name:'Resend Code'},{timeout:3500});expect(resend).toBeEnabled();await user.click(resend);
  expect(await screen.findByRole('button',{name:/Try again in/})).toBeDisabled();
  await user.click(screen.getByLabelText('Digit 1'));await user.paste('123456');await user.click(screen.getByRole('button',{name:'Verify Email'}));
  expect(await screen.findByRole('heading',{name:'Email verified!'})).toBeInTheDocument();expect(document.querySelector('.is-celebrating')).not.toBeNull();expect(sessionStorage.length).toBe(0);
});
it('completes all recovery steps with generic lookup and requires signing in afterward',async()=>{
  mount('/forgot-password');const user=userEvent.setup({delay:null});await user.type(await screen.findByLabelText('Registered email address'),'player@example.com');await user.click(screen.getByRole('button',{name:'Send Code'}));
  expect(await screen.findByText(/If an account matches/)).toBeInTheDocument();await user.click(screen.getByLabelText('Digit 1'));await user.paste('123456');await user.click(screen.getByRole('button',{name:'Verify Code'}));
  expect(await screen.findByRole('heading',{name:'Create a new password'})).toBeInTheDocument();await user.type(screen.getByLabelText('New password',{exact:true}),'NewPwd42');await user.type(screen.getByLabelText('Confirm new password'),'NewPwd42');await user.click(screen.getByRole('button',{name:'Show new password'}));expect(screen.getByLabelText('New password',{exact:true})).toHaveAttribute('type','text');await user.click(screen.getByRole('button',{name:'Reset Password'}));
  expect(await screen.findByRole('heading',{name:"You're all set!"})).toBeInTheDocument();expect(screen.getByRole('link',{name:'Back to Sign In'})).toHaveAttribute('href','/login');expect(vi.mocked(fetch).mock.calls.some(([url])=>url==='/api/auth/login')).toBe(false);
});
it('provides security settings and fixes signed-in recovery to the registered address',async()=>{
  signedIn=true;mount('/settings');expect(await screen.findByRole('heading',{name:'Password & Security'},{timeout:5000})).toBeInTheDocument();expect(screen.getByText('Not verified')).toBeInTheDocument();expect(screen.getByRole('button',{name:'Verify email'})).toBeEnabled();
  const user=userEvent.setup({delay:null});await user.click(screen.getByRole('button',{name:'Change Password'}));expect(screen.getAllByLabelText('Current password').some(input=>input.hasAttribute('required'))).toBe(true);await user.click(screen.getByRole('link',{name:'Forgot Password?'}));await waitFor(()=>expect(screen.getByLabelText('Registered email address')).toHaveValue('player@example.com'));expect(screen.getByLabelText('Registered email address')).toHaveAttribute('readonly');
});
