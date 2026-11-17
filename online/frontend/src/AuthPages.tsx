import { UsernameField } from './components/security/UsernameField';
import { NightForest } from './components/brand/NightWorld';
﻿import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowRight, Eye, EyeOff } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import { api } from './api';
import { rememberVerification, type VerificationChallenge } from './emailSecurity';
import { useAuth, useToast } from './context';
import { HootlyLogo } from './components/brand/HootlyLogo';
import { HootlyMascot } from './components/brand/HootlyMascot';
import { AnimatedButton } from './components/ui/AnimatedButton';
import { PageTransition } from './components/ui/PageTransition';
import { MascotScene } from './components/brand/MascotScene';
import { useConfirmedNavigation } from './components/brand/useConfirmedNavigation';

export function AuthPage({ register = false }: { register?: boolean }) {
  const [registrationUsername,setRegistrationUsername]=useState('');
  const [error,setError]=useState(''),[busy,setBusy]=useState(false),[show,setShow]=useState(false),[success,setSuccess]=useState<string|null>(null),[activeField,setActiveField]=useState(''),[attention,setAttention]=useState(0);
  const {refresh}=useAuth(),toast=useToast(),location=useLocation(),navigate=useNavigate();
  useConfirmedNavigation(success,!register,register?1600:1150);
  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();const data=new FormData(event.currentTarget);setError('');
    if(register&&data.get('password')!==data.get('confirmPassword')){setError('Passwords must match.');return;}
    setBusy(true);
    try{
      if(register){const challenge=await api<VerificationChallenge>('/auth/register',{method:'POST',body:JSON.stringify(Object.fromEntries(data))});rememberVerification(challenge);navigate('/verify-email');toast('Check your email to activate your account.');}
      else{await api('/auth/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({username:String(data.get('username')),password:String(data.get('password'))})});await refresh();
        const from=(location.state as {from?:string}|null)?.from;setSuccess(from?.startsWith('/')&&!from.startsWith('//')?from:'/dashboard');}
    }catch(failure){setError(failure instanceof Error?failure.message:'Please try again.');setBusy(false);}
  }
  const scene = register ? 'registration' : 'login';
  const mascotProps = {scene, mood:error?'encouraging' as const:undefined, attention, privateInput:!show&&activeField.toLowerCase().includes('password')&&!success, event:success?{id:`auth:${register}`,type:'success' as const}:undefined} as const;
  return <main className={`auth-layout ${register?'auth-register':'auth-login'}`}><section className="auth-story"><NightForest scene="auth"/><Link className="brand" to="/" aria-label="Hootly home"><HootlyLogo/></Link>
    <div className="auth-story-content">
      <h1>{register?<>Make room for<br/><em>your people.</em></>:<>Good company.<br/>Great questions.<br/><em>Your kind of night.</em></>}</h1>
      <p className="auth-story-description">{register?'Create a room, invite your friends, and make a night of it.':'A private room. Your favourite people. A little friendly rivalry.'}</p>
      <div className="auth-owl-stage"><div className="mascot-halo"/><MascotScene {...mascotProps} size={280}/></div>
    </div></section>
    <section className="auth-form-area"><PageTransition key={register?'register':'login'}><div className="form-card">
      <div className="auth-mobile-owl"><HootlyMascot {...mascotProps} size={64}/></div>
      <h2>{register?'Create your account':'Welcome back'}</h2><p>{register?'Your next quiz night starts here.':'Sign in to host a quiz or join your friends.'}</p>
      {(location.state as {passwordChanged?:boolean}|null)?.passwordChanged&&<p className="email-success-notice" role="status">Your password has changed. Sign in again to continue.</p>}
      <form onSubmit={submit} aria-busy={busy} onBlur={e=>{if(!e.currentTarget.contains(e.relatedTarget as Node|null))setActiveField('');}} onFocus={e=>{const field=e.target;if(field instanceof HTMLInputElement){setActiveField(field.name);setAttention(field.name==='username'?-.7:field.name==='displayName'?-.4:field.name==='email'?.2:.6);}else if(field instanceof HTMLElement&&!field.closest('[data-password-toggle]'))setActiveField('');}} onInput={e=>{const field=e.target;if(field instanceof HTMLInputElement)setAttention(Math.min(1,field.value.length/30)-.5);}}>
        {register&&<label>Display name<input name="displayName" autoComplete="name" required minLength={1} maxLength={40} placeholder="What should we call you?"/></label>}
        {register?<UsernameField value={registrationUsername} onChange={setRegistrationUsername}/>:<label>{'Email or username'}<input name="username" autoComplete="username" required minLength={register?3:undefined} maxLength={register?24:254} pattern={register?'[A-Za-z0-9_]{3,24}':undefined} placeholder={register?'Choose your player name':'Enter your email or username'}/></label>}
        {register&&<label>Email address<input name="email" type="email" autoComplete="email" required maxLength={254} placeholder="you@example.com"/></label>}
        <div className="password-field"><label htmlFor="auth-password">Password</label><div className="password-input"><input id="auth-password" name="password" type={show?'text':'password'} autoComplete={register?'new-password':'current-password'} required minLength={register?8:undefined} maxLength={64} placeholder={register?'8+ characters, a letter and number':'Enter your password'}/><button type="button" className="password-toggle" data-password-toggle aria-label={show?'Hide password':'Show password'} aria-pressed={show} onClick={()=>setShow(!show)}>{show?<EyeOff size={19}/>:<Eye size={19}/>}</button></div></div>
        {register&&<label>Confirm password<input name="confirmPassword" type={show?'text':'password'} autoComplete="new-password" required maxLength={64} placeholder="One more time"/></label>}
        <AnimatePresence>{error&&<motion.p key="error" className="error" role="alert" initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}}>{error}</motion.p>}</AnimatePresence>
        <AnimatedButton className="primary full" disabled={busy}>{busy?'Please wait…':register?'Create account':'Sign in'}<ArrowRight size={18}/></AnimatedButton>
      </form>{!register&&<div className="auth-recovery"><Link to="/forgot-password">Forgot password?</Link></div>}<p className="auth-switch">{register?'Already part of the night?':'New around here?'} <Link to={register?'/login':'/register'}>{register?'Sign in':'Create an account'}</Link></p>
</div></PageTransition></section></main>;
}
