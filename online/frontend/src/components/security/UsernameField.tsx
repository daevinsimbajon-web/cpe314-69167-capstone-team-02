import { useEffect,useState } from 'react';
import { api } from '../../api';
export function useUsernameAvailability(value:string,current?:string){
  const [result,setResult]=useState<{value:string;state:'checking'|'available'|'taken'|'error'}|null>(null);
  const valid=/^[A-Za-z0-9_]{3,24}$/.test(value),unchanged=current?.toLowerCase()===value.toLowerCase();
  useEffect(()=>{if(!valid||unchanged)return;let active=true;const timer=setTimeout(()=>{api<{available:boolean}>(`/auth/username-availability?username=${encodeURIComponent(value)}`).then(data=>{if(active)setResult({value,state:data.available?'available':'taken'});}).catch(()=>{if(active)setResult({value,state:'error'});});},300);return()=>{active=false;clearTimeout(timer);};},[value,current,valid,unchanged]);
  const state=!valid?'invalid':unchanged?'available':result?.value===value?result.state:'checking';
  return {valid,available:state==='available',message:!value?'':state==='invalid'?'Use 3–24 letters, numbers or underscores.':state==='checking'?'Checking username…':state==='available'?'Username available.':state==='taken'?'That username is already taken.':'Could not check availability. Try again shortly.'};
}
export function UsernameField({value,onChange,current,disabled=false}:{value:string;onChange:(value:string)=>void;current?:string;disabled?:boolean}){
  const status=useUsernameAvailability(value,current);
  return <label>Username<input name="username" aria-label="Username" value={value} onChange={event=>onChange(event.target.value)} autoComplete="username" required minLength={3} maxLength={24} pattern="[A-Za-z0-9_]{3,24}" disabled={disabled} aria-describedby="username-feedback"/><small className="help-text">Your unique identity on Hootly.</small><small id="username-feedback" className="help-text" role="status">{status.message}</small></label>;
}
