import { RoomCode } from './components/game/RoomCode';
import { PlayerOwl } from './components/avatar/PlayerOwl';
﻿import { useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ArrowRight, DoorOpen, LogOut, Plus, Menu, X } from 'lucide-react';
import { NightIcon, type NightMotif } from './components/ui/NightIcon';
import { useAtmosphereMotion } from './components/brand/Atmosphere';
import { AuthPage } from './AuthPages';

import { api, type Profile } from './api';
import { useAuth, useToast } from './context';
import { CreateRoomPage, JoinRoomPage, RoomPage, HistoryPage, SettingsPage } from './RoomPages';
import { Campfire, NightForest } from './components/brand/NightWorld';
import { HootlyLogo } from './components/brand/HootlyLogo';
import { HootlyMascot } from './components/brand/HootlyMascot';
import { PageTransition } from './components/ui/PageTransition';
import { AnimatedCard } from './components/ui/AnimatedCard';
import { AnimatedNumber } from './components/ui/AnimatedNumber';
import { LoadingScreen } from './components/ui/LoadingScreen';
import { ProfileMenu } from './components/ui/ProfileMenu';
import { ProfilePage } from './AccountPages';
import { lazy, Suspense } from 'react';

const VerifyEmailPage=lazy(()=>import('./EmailSecurityPages').then(m=>({default:m.VerifyEmailPage})));
const ForgotPasswordPage=lazy(()=>import('./EmailSecurityPages').then(m=>({default:m.ForgotPasswordPage})));
const MyQuizzesPage = lazy(() => import('./QuizPages').then(m=>({default:m.MyQuizzesPage})));
const QuizPreviewPage = lazy(() => import('./QuizPages').then(m=>({default:m.QuizPreviewPage})));
const CreditsPage = lazy(() => import('./QuizPages').then(m=>({default:m.CreditsPage})));
const QuizEditorPage = lazy(() => import('./QuizEditorPage').then(m=>({default:m.QuizEditorPage})));
const QuizPracticePage = lazy(() => import('./QuizPracticePage').then(m=>({default:m.QuizPracticePage})));
const AnalyticsPage = lazy(() => import('./AnalyticsPages').then(module => ({default:module.AnalyticsPage})));
const ComparePlayersPage = lazy(() => import('./AnalyticsPages').then(module => ({default:module.ComparePlayersPage})));
const MascotReview = import.meta.env.DEV ? lazy(() => import('./MascotReviewPage').then(module => ({default:module.MascotReviewPage}))) : null;

export function App(){return <Routes><Route path="/verify-email" element={<Suspense fallback={<Loading/>}><VerifyEmailPage/></Suspense>}/><Route path="/forgot-password" element={<Suspense fallback={<Loading/>}><ForgotPasswordPage/></Suspense>}/>
  {MascotReview&&<Route path="/mascot-review" element={<Suspense fallback={<Loading/>}><MascotReview/></Suspense>}/>}
  <Route path="/" element={<Landing/>}/><Route path="/login" element={<AuthPage key="login"/>}/><Route path="/register" element={<AuthPage key="registration" register/>}/>
  <Route path="/dashboard" element={<Protected><Dashboard/></Protected>}/><Route path="/profile" element={<Protected><ProfilePage/></Protected>}/>
  <Route path="/bank/:id/practice" element={<Protected><Suspense fallback={<Loading/>}><QuizPracticePage/></Suspense></Protected>}/>
  <Route path="/create" element={<Protected><CreateRoomPage/></Protected>}/><Route path="/join" element={<Protected><JoinRoomPage/></Protected>}/><Route path="/join/:code" element={<Protected><JoinRoomPage/></Protected>}/>
  <Route path="/rooms/:code" element={<Protected><RoomPage/></Protected>}/><Route path="/rooms/:code/leaderboard" element={<Protected><RoomPage leaderboardOnly/></Protected>}/>
  <Route path="/bank/new" element={<Protected><Suspense fallback={<Loading/>}><QuizEditorPage key="new"/></Suspense></Protected>}/><Route path="/bank/:id/edit" element={<Protected><Suspense fallback={<Loading/>}><QuizEditorPage/></Suspense></Protected>}/><Route path="/bank/:id/preview" element={<Protected><Suspense fallback={<Loading/>}><QuizPreviewPage/></Suspense></Protected>}/><Route path="/credits" element={<Protected><Suspense fallback={<Loading/>}><CreditsPage/></Suspense></Protected>}/>
  <Route path="/analytics/compare" element={<Protected><Suspense fallback={<Loading/>}><ComparePlayersPage/></Suspense></Protected>}/><Route path="/history" element={<Protected><HistoryPage/></Protected>}/><Route path="/analytics" element={<Protected><Suspense fallback={<Loading/>}><AnalyticsPage/></Suspense></Protected>}/><Route path="/settings" element={<Protected><SettingsPage/></Protected>}/><Route path="/bank" element={<Protected><Suspense fallback={<Loading/>}><MyQuizzesPage/></Suspense></Protected>}/>
  <Route path="*" element={<main className="not-found"><HootlyMascot scene="error" size={160}/><h1>That round went missing.</h1><p>Let’s get you back to your people.</p><Link className="button primary" to="/">Back to Hootly <ArrowRight size={18}/></Link></main>}/>
</Routes>;}
function Landing(){const {user}=useAuth(),{ref,paused}=useAtmosphereMotion();return <div className="landing" ref={ref} data-theme-paused={paused||undefined}><div className="landing-sky"><NightForest scene="landing"/></div><header className="landing-nav"><Link to="/" className="brand"><HootlyLogo/></Link><nav><Link to={user?'/dashboard':'/login'}>{user?'Dashboard':'Sign in'}</Link><Link to={user?'/create':'/register'} className="button small">Join the night <ArrowRight size={16}/></Link></nav></header>
  <main><section className="hero"><PageTransition><h1>Bring your people.<br/>Find your <em>“I knew it!”</em><br/>Make it a night.</h1><p>Bring your people together for a smarter, more exciting quiz night. Host a live room, share the code, and play together.</p><div className="actions"><Link className="button primary" to={user?'/create':'/register'}>Start a quiz <NightIcon motif="wand" size={24}/></Link><Link className="button" to="/join">Join a room <NightIcon motif="join" size={24}/></Link></div><div className="hero-notes"><span><NightIcon motif="key" size={20}/> Your own private room</span><span><NightIcon motif="games" size={20}/> Live, together</span><span><NightIcon motif="wins" size={20}/> Earn every point</span></div></PageTransition>
  <div className="hero-art"><HootlyMascot scene="landing" attention={-1} size={360} loopMotion={!paused}/><Campfire/></div></section>
  <section className="landing-how"><div><h2>Three steps to<br/>“one more round.”</h2></div>{[['host','01','Make a little room','Choose your questions and set the pace.'],['people','02','Bring your people','Share a code. Meet in your private lobby.'],['wins','03','Let curiosity win','Answer live. Chase the podium. Repeat.']].map(([motif,n,title,copy])=>{return <article key={String(n)}><span className="step-number">{String(n)}</span><NightIcon motif={motif as NightMotif} size={32}/><h3>{String(title)}</h3><p>{String(copy)}</p></article>;})}</section></main></div>;}
export function Protected({children}:{children:ReactNode}){const {user,loading,endingSession}=useAuth(),location=useLocation();if(endingSession&&!user)return null;if(loading)return <Loading/>;if(!user)return <Navigate to="/login" state={{from:location.pathname}} replace/>;return <Shell>{children}</Shell>;}
function Shell({children}:{children:ReactNode}){const {user,logout}=useAuth(),toast=useToast(),location=useLocation(),navigate=useNavigate(),[menu,setMenu]=useState(false),[mobile,setMobile]=useState(()=>window.matchMedia('(max-width:760px)').matches);
  const {ref,paused}=useAtmosphereMotion();
  useEffect(()=>{const media=window.matchMedia('(max-width:760px)'),update=()=>setMobile(media.matches);media.addEventListener('change',update);return()=>media.removeEventListener('change',update);},[]);
  return <div className="app-layout magic-night" ref={ref} data-theme-paused={paused||undefined}><a className="skip-link" href="#main-content">Skip to content</a><aside className={`sidebar ${menu?'menu-open':''}`}><Link className="brand" to="/dashboard" onClick={()=>setMenu(false)}><HootlyLogo compactOnMobile/></Link><div className="sidebar-mobile-actions">{mobile&&<ProfileMenu/>}<button className="mobile-menu" aria-label={menu?'Close navigation':'Open navigation'} aria-expanded={menu} aria-controls="main-navigation" onClick={()=>setMenu(!menu)}>{menu?<X/>:<Menu/>}</button></div><nav id="main-navigation" aria-label="Main navigation">
    {([['/dashboard','overview','Overview'],['/create','host','Host a room'],['/join','join','Join a room'],['/history','history','Game history'],['/analytics','analytics','Analytics'],['/bank','bank','My Quizzes'],['/profile','profile','My profile'],['/settings','settings','Settings']] satisfies [string,NightMotif,string][]).map(([path,motif,label],index)=><NavLink key={path} to={path} className={index===3||index===6?'nav-group-start':undefined} onClick={()=>setMenu(false)}><NightIcon motif={motif}/><span>{label}</span></NavLink>)}</nav>
    <div className="sidebar-bottom"><PlayerOwl identity={user!.id} avatar={user!.avatar} size={44}/><div><strong>{user!.displayName}</strong><small>@{user!.username}</small></div><button title="Sign out" aria-label="Sign out" onClick={()=>void logout().then(()=>navigate('/',{replace:true})).catch(e=>toast(e.message))}><LogOut size={18}/></button></div></aside>
    <div className="app-main">{!location.pathname.startsWith('/rooms/')&&<NightForest edge scene={location.pathname.includes('analytics')||location.pathname==='/bank'?'data':location.pathname==='/dashboard'?'dashboard':['/create','/join'].some(path=>location.pathname.startsWith(path))?'invite':'quiet'}/>}<header className="app-header"><div className="header-actions"><Link to="/join" className="text-link"><DoorOpen size={15}/> Join a room</Link>{!mobile&&<ProfileMenu/>}</div></header><main id="main-content" className="content"><PageTransition key={location.pathname}>{children}</PageTransition></main></div></div>;
}
function Dashboard(){const {user}=useAuth(),activeRooms=useLoad<import('./api').RoomSummary[]>('/rooms');return <><PageHeading title={`Hey, ${user!.displayName.split(' ')[0]}. Ready for a round?`} subtitle="Host a private room or jump in with a code. Your next round starts here."/><Stats profile={user!}/>
  <AnimatedCard className="dashboard-hero"><div><h2>Good questions.<br/><em>Even better company.</em></h2><p>A private room. Your favourite people. A little friendly rivalry.</p><div className="actions"><Link className="button primary" to="/create">Host a quiz room <ArrowRight size={18}/></Link><Link className="button" to="/join">Join with a code</Link></div></div><div className="dashboard-owl"><div className="welcome-orbit" aria-hidden="true"><i/><i/><i/></div><HootlyMascot scene="dashboard" size={150}/></div></AnimatedCard>
  <section className="two-columns dashboard-actions"><Link className="action-card" to="/join"><span className="icon-tile cyan"><NightIcon motif="join" size={36}/></span><div><h3>Got an invitation?</h3><p>Your crew is one room code away.</p></div><ArrowRight size={20}/></Link><Link className="action-card" to="/history"><span className="icon-tile gold"><NightIcon motif="history" size={36}/></span><div><h3>Every round has a story.</h3><p>Revisit your scores and moments of glory.</p></div><ArrowRight size={20}/></Link></section>
  <div className="section-title"><div><h2>Your rooms</h2></div><Link className="text-link" to="/bank">My Quizzes <ArrowRight size={16}/></Link></div><AnimatedCard className="rooms-panel">{activeRooms.error?<p role="alert" className="error">{activeRooms.error}</p>:!activeRooms.data?<Loading/>:activeRooms.data.length===0?<div className="empty-state"><NightIcon motif="lantern" size={54}/><h3>Your next great night starts here.</h3><p>Make a room for your people, or jump in with their invitation.</p><Link className="button small" to="/create"><Plus size={16}/> Create your first room</Link></div>:<div className="room-grid">{activeRooms.data.map(room=><div className="room-preview" key={room.code}><div><span className="badge" data-status={room.status}>{room.status}</span><span className="room-role"><NightIcon motif={room.host?'host':'join'} size={22}/>{room.host?'Hosting':'Playing'}</span></div><h3><Link to={`/rooms/${room.code}`}>{room.title}</Link></h3><div><RoomCode code={room.code}/><Link className="text-link" to={`/rooms/${room.code}`}>Open room <ArrowRight size={16}/></Link></div></div>)}</div>}</AnimatedCard></>;}
export function Stats({profile}:{profile:Profile}){
  const entries=[['games',profile.statistics.gamesPlayed,'Games played'],['wins',profile.statistics.totalWins,'Total wins'],['points',profile.statistics.totalPoints,'Total points'],['accuracy',profile.statistics.averageAccuracy,'Average accuracy']] as const;
  return <div className="stats-grid">{entries.map(([motif,value,label],index)=><AnimatedCard className="stat-card" key={label} delay={index*.04}><span className={`icon-tile ${index===1?'gold':index===2?'cyan':''}`}><NightIcon motif={motif} size={34}/></span><div><strong>{value==null?<span aria-label="No completed answers yet">—</span>:<><AnimatedNumber value={Number(value)}/>{index===3?'%':''}</>}</strong><span>{label}</span></div></AnimatedCard>)}</div>;
}
export function PageHeading({title,subtitle,motif='overview'}:{title:string;subtitle:string;motif?:NightMotif}){return <div className="page-heading"><span className="heading-emblem"><NightIcon motif={motif} size={34}/></span><div><h1>{title}</h1><p>{subtitle}</p></div></div>;}
export function Loading(){return <LoadingScreen/>;}
export function initials(name:string){return name.split(/\s+/).map(word=>word[0]).slice(0,2).join('').toUpperCase();}
export function useLoad<T>(path:string){const [data,setData]=useState<T|null>(null),[error,setError]=useState('');useEffect(()=>{let active=true;api<T>(path).then(result=>{if(active){setData(result);setError('');}}).catch(e=>{if(active)setError(e.message);});return()=>{active=false;};},[path]);return {data,error,setData};}
