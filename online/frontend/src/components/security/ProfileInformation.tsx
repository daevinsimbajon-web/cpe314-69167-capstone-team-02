import { useEffect,useRef,useState,type FormEvent } from 'react';
import { api } from '../../api';
import { useAuth,useToast } from '../../context';
import { AvatarEditor } from '../avatar/AvatarEditor';
import { UsernameField,useUsernameAvailability } from './UsernameField';
export function ProfileInformation({showAvatar=true,buttonLabel='Save Changes'}:{showAvatar?:boolean;buttonLabel?:string}){
  const {user,refresh}=useAuth(),toast=useToast(),[name,setName]=useState(user!.displayName),[username,setUsername]=useState(user!.username),[busy,setBusy]=useState(false),[error,setError]=useState(''),saving=useRef(false);
  const availability=useUsernameAvailability(username,user!.username);
  useEffect(()=>{setName(user!.displayName);setUsername(user!.username);},[user!.displayName,user!.username]);
  const changed=name.trim()!==user!.displayName||username.toLowerCase()!==user!.username,valid=name.trim().length>0&&name.trim().length<=40&&availability.valid;
  async function save(event:FormEvent){event.preventDefault();if(saving.current||!valid||!changed||!availability.available)return;saving.current=true;setBusy(true);setError('');try{await api('/me',{method:'PATCH',body:JSON.stringify({displayName:name.trim(),username})});await refresh();toast('Your profile has been updated.');}catch(e){setError((e as Error).message);}finally{saving.current=false;setBusy(false);}}
  return <section className="panel profile-information"><h3>Profile Information</h3>{showAvatar&&<AvatarEditor/>}<form onSubmit={save} aria-busy={busy}><label>Display name<input name="displayName" aria-label="Display name" value={name} onChange={event=>setName(event.target.value)} maxLength={40} required autoComplete="nickname" disabled={busy}/><small className="help-text">This is how other players see you in games.</small></label><UsernameField value={username} onChange={setUsername} current={user!.username} disabled={busy}/>{error&&<p className="error" role="alert">{error}</p>}<button className="button primary" disabled={busy||!changed||!valid||!availability.available}>{busy?'Saving…':buttonLabel}</button></form></section>;
}
