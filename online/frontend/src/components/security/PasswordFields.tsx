import { useId, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

export function PasswordFields(){
  const id=useId(),[show,setShow]=useState(false),[password,setPassword]=useState('');
  const score=Number(password.length>=12)+Number(/[A-Za-z]/.test(password))+Number(/[0-9]/.test(password))+Number(/[^A-Za-z0-9]/.test(password));
  return <><div className="password-field"><label htmlFor={`${id}-new`}>New password</label><div className="password-input"><input id={`${id}-new`} name="password" type={show?'text':'password'} autoComplete="new-password" minLength={8} maxLength={64} required aria-describedby={`${id}-requirements`} value={password} onChange={e=>setPassword(e.target.value)}/><button type="button" className="password-toggle" aria-label={show?'Hide new password':'Show new password'} aria-pressed={show} onClick={()=>setShow(!show)}>{show?<EyeOff size={19}/>:<Eye size={19}/>}</button></div></div><div className="password-guidance"><meter min={0} max={4} value={score} aria-label="Password strength"/><small id={`${id}-requirements`}>8–64 characters, including a letter and number. A longer, unique phrase is best.</small></div><label>Confirm new password<input name="confirmPassword" type={show?'text':'password'} autoComplete="new-password" required maxLength={64}/></label></>;
}
