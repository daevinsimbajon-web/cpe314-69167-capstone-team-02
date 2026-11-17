import { useRef,useState } from 'react';
import { Shuffle } from 'lucide-react';
import { api } from '../../api';
import { useAuth,useToast } from '../../context';
import { avatarFor,birdSpecies,surpriseAvatar,type AvatarConfig } from './avatarConfig';
import { PlayerOwl } from './PlayerOwl';

export function AvatarEditor(){
  const {user,refresh}=useAuth(),toast=useToast(),[preview,setPreview]=useState<AvatarConfig|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),saving=useRef(false);
  const saved=user!.avatar??avatarFor(user!.id);
  async function save(){if(!preview||saving.current)return;saving.current=true;setBusy(true);setError('');
    try{await api('/me/avatar',{method:'PATCH',body:JSON.stringify(preview)});await refresh();setPreview(null);toast('Your night bird has been saved.');}
    catch(e){setError((e as Error).message);}finally{saving.current=false;setBusy(false);}
  }
  return <div className="profile-owl-editor" aria-label="Your night-bird avatar"><div className={preview?'player-owl-preview':''}><PlayerOwl identity={user!.id} avatar={preview??saved} name={preview?'your unsaved preview':user!.displayName} size={126} animateIdle mood={error?'encouraged':busy?'thinking':preview?'curious':'idle'}/></div>
    <label className="bird-species-select">Night-bird species<select value={(preview??saved).species??0} disabled={busy} onChange={event=>{setError('');setPreview({...(preview??saved),species:Number(event.target.value)});}}>{birdSpecies.map((species,index)=><option key={species} value={index}>{species}</option>)}</select></label>
    <button type="button" className="button small" disabled={busy} onClick={()=>{setError('');setPreview(surpriseAvatar(preview??saved));}}><Shuffle size={17}/>Surprise Me</button>
    {preview&&<><p className="help-text" role="status">Preview only. Save this bird or cancel to keep your current one.</p><div className="avatar-save-actions"><button type="button" className="button primary" disabled={busy} onClick={()=>void save()}>{busy?'Saving avatar…':'Save avatar'}</button><button type="button" className="button" disabled={busy} onClick={()=>{setPreview(null);setError('');}}>Cancel avatar change</button></div></>}
    {error&&<p className="error" role="alert">{error}</p>}
  </div>;
}
