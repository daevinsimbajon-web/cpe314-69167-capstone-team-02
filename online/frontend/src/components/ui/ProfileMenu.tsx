import { PlayerOwl } from '../avatar/PlayerOwl';
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion, useIsPresent } from 'motion/react';
import { ChevronDown, LogOut } from 'lucide-react';
import { useAuth, useToast } from '../../context';
import { useQuietMotion } from './MotionProvider';
import { NightIcon } from './NightIcon';

function MenuPanel({children,id,quiet}:{children:React.ReactNode;id:string;quiet:boolean}){
  const present=useIsPresent();
  return <motion.div id={id} role="menu" aria-label="Account" inert={!present} className="profile-menu-panel"
    initial={quiet?false:{opacity:0,y:-6,scale:.98}} animate={{opacity:1,y:0,scale:1}} exit={quiet?{opacity:0}:{opacity:0,y:-4,scale:.98}} transition={{duration:quiet?0:.16}}>{children}</motion.div>;
}

export function ProfileMenu(){
  const {user,logout}=useAuth(),toast=useToast(),navigate=useNavigate(),location=useLocation(),quiet=useQuietMotion();
  const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const root=useRef<HTMLDivElement>(null),trigger=useRef<HTMLButtonElement>(null),focusIndex=useRef<number|null>(null),id=useId();
  const items=()=>Array.from(root.current?.querySelectorAll<HTMLElement>('[role="menuitem"]')??[]).filter(item=>!item.hasAttribute('disabled'));
  useEffect(()=>{setOpen(false);setError('');},[location.pathname]);
  useEffect(()=>{
    if(!open)return;
    if(focusIndex.current!==null){const list=items();list[focusIndex.current===-1?list.length-1:0]?.focus();focusIndex.current=null;}
    function outside(event:PointerEvent){if(!root.current?.contains(event.target as Node))setOpen(false);}
    function escape(event:globalThis.KeyboardEvent){if(event.key==='Escape'){event.preventDefault();setOpen(false);trigger.current?.focus();}}
    document.addEventListener('pointerdown',outside);document.addEventListener('keydown',escape);
    return()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',escape);};
  },[open]);
  function keyboard(event:KeyboardEvent){
    const list=items(),index=list.indexOf(document.activeElement as HTMLElement);
    if(event.key==='Tab'){setOpen(false);trigger.current?.focus();return;}
    if(!['ArrowDown','ArrowUp','Home','End'].includes(event.key))return;
    event.preventDefault();
    const next=event.key==='Home'?0:event.key==='End'?list.length-1:(index+(event.key==='ArrowDown'?1:-1)+list.length)%list.length;
    list[next]?.focus();
  }
  if(!user)return null;
  return <div className="profile-menu" ref={root} onKeyDown={event=>{if(open)keyboard(event);}}>
    <button ref={trigger} className="profile-menu-trigger" aria-label={open?'Close account menu':'Open account menu'} aria-haspopup="menu" aria-expanded={open} aria-controls={id}
      onClick={()=>{setError('');setOpen(value=>!value);}} onKeyDown={event=>{if(!open&&['ArrowDown','ArrowUp'].includes(event.key)){event.preventDefault();focusIndex.current=event.key==='ArrowUp'?-1:0;setOpen(true);}}}>
      <PlayerOwl identity={user.id} avatar={user.avatar} size={38}/><ChevronDown size={14} aria-hidden="true"/>
    </button>
    <AnimatePresence>{open&&<MenuPanel id={id} quiet={quiet}><div className="profile-menu-identity"><PlayerOwl identity={user.id} avatar={user.avatar} size={48}/><div><strong>{user.displayName}</strong><small>@{user.username}</small><small>{user.email}</small></div></div>
      <div className="profile-menu-items"><Link role="menuitem" to="/profile"><NightIcon motif="profile" size={24}/>My Profile</Link><Link role="menuitem" to="/settings"><NightIcon motif="settings" size={24}/>Account Settings</Link></div>
      {error&&<p className="menu-error" role="alert">{error}</p>}
      <button role="menuitem" className="profile-menu-logout" disabled={busy} onClick={async()=>{if(busy)return;setBusy(true);setError('');try{await logout();setOpen(false);navigate('/',{replace:true});toast('You have signed out.');}catch(failure){setError((failure as Error).message);}finally{setBusy(false);}}}><LogOut size={17}/>{busy?'Signing out…':'Log out'}</button>
    </MenuPanel>}</AnimatePresence>
  </div>;
}
