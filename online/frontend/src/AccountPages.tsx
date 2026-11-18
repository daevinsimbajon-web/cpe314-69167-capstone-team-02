import { ProfileInformation } from './components/security/ProfileInformation';
import { AvatarEditor } from './components/avatar/AvatarEditor';
import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Settings, ShieldAlert, Trash2 } from 'lucide-react';
import { api, type History } from './api';
import { useAuth, useToast } from './context';
import { Loading, PageHeading, Stats, useLoad } from './App';

export function ProfilePage(){
  const {user}=useAuth(),history=useLoad<History[]>('/history');
  return <><PageHeading motif="profile" title="My profile" subtitle="Your details, your progress, and the rounds you’ve played."/>
    <section className="profile-summary panel"><AvatarEditor/><div className="profile-summary-info"><h2>{user!.displayName}</h2><p>@{user!.username}</p><p>{user!.email}</p><small>Joined {new Date(user!.createdAt).toLocaleDateString(undefined,{year:'numeric',month:'long',day:'numeric'})}</small></div><Link className="button small" to="/settings"><Settings size={16}/>Account Settings</Link></section>
    <Stats profile={user!}/><div className="profile-columns"><ProfileInformation showAvatar={false} buttonLabel="Save profile"/><section className="panel"><div className="section-title" style={{marginTop:0}}><div><h2>Recent games</h2></div><Link className="text-link" to="/history">View all<ArrowRight size={15}/></Link></div>
      {history.error?<p className="error" role="alert">{history.error}</p>:!history.data?<Loading/>:history.data.length===0?<div className="profile-empty"><h3>Make a little history.</h3><p>Finished games will appear here with your score and position.</p><Link className="button" to="/join">Join a room<ArrowRight size={16}/></Link></div>:<ol className="recent-games">{history.data.slice(0,5).map(game=><li key={game.roomCode}><Link to={`/rooms/${game.roomCode}`}><span className="recent-rank">#{game.rank}</span><span><strong>{game.title}</strong><small>{new Date(game.finishedAt).toLocaleDateString()}</small></span><span className="recent-score">{game.points}<small>points</small></span></Link></li>)}</ol>}
    </section></div></>;
}

export function DangerZone(){
  const [open,setOpen]=useState(false),[password,setPassword]=useState(''),[confirmation,setConfirmation]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const dialog=useRef<HTMLDialogElement>(null),submitting=useRef(false),id=useId(),{deleteAccount}=useAuth(),navigate=useNavigate(),toast=useToast();
  useEffect(()=>{if(open){setPassword('');setConfirmation('');setError('');if(!dialog.current?.open)dialog.current?.showModal();}else dialog.current?.close();},[open]);
  const ready=password.length>0&&confirmation==='DELETE'&&!busy;
  async function remove(event:FormEvent){
    event.preventDefault();if(!ready||submitting.current)return;submitting.current=true;setBusy(true);setError('');
    try{await deleteAccount(password,confirmation);setPassword('');setConfirmation('');setOpen(false);navigate('/',{replace:true});toast('Your account has been deleted.');}
    catch(failure){setError((failure as Error).message);}finally{submitting.current=false;setBusy(false);}
  }
  return <><section className="panel danger-zone"><div className="danger-zone-title"><ShieldAlert size={21}/><h3>Danger Zone</h3></div><p>Permanently remove your profile and sign-in details. This cannot be undone.</p><button className="button danger" onClick={()=>setOpen(true)}><Trash2 size={16}/>Delete account</button></section>
    <dialog ref={dialog} className="account-delete-dialog" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`} onCancel={event=>{event.preventDefault();if(!busy)setOpen(false);}} onClick={event=>{if(event.target===event.currentTarget&&!busy)setOpen(false);}}>
      <span className="eyebrow">PERMANENT ACCOUNT DELETION</span><h2 id={`${id}-title`}>Delete your Hootly account?</h2><p id={`${id}-description`}>Your profile and sign-in details will be permanently removed and you will be signed out on every device. Your hosted lobbies will close and active hosted quizzes will end. Anonymous game results stay available to the other players.</p>
      <form onSubmit={remove} aria-busy={busy}><label>Current password<input type="password" autoComplete="current-password" value={password} onChange={event=>setPassword(event.target.value)} maxLength={64} disabled={busy} required autoFocus/></label>
        <label>Type DELETE to confirm<input value={confirmation} onChange={event=>setConfirmation(event.target.value)} pattern="DELETE" autoComplete="off" spellCheck={false} disabled={busy} required/></label>
        {error&&<p className="error" role="alert">{error}</p>}<div className="actions"><button type="button" className="button" disabled={busy} onClick={()=>setOpen(false)}>Cancel</button><button type="submit" className="button danger" disabled={!ready}><Trash2 size={16}/>{busy?'Deleting…':'Delete my account'}</button></div>
      </form>
    </dialog></>;
}
