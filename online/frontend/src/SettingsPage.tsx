import { useEffect,useState } from 'react';
import { Link,useNavigate } from 'react-router-dom';
import { useAuth,useToast } from './context';
import { PageHeading } from './App';
import { DangerZone } from './AccountPages';
import { ProfileInformation } from './components/security/ProfileInformation';
import './styles/settings.css';
import { PasswordSecurityPanel } from './EmailSecurityPages';
export function SettingsPage(){
  const {logout}=useAuth(),toast=useToast(),navigate=useNavigate(),[reduced,setReduced]=useState(()=>localStorage.getItem('quiz-reduced-motion')==='true');
  useEffect(()=>{document.documentElement.dataset.reducedMotion=String(reduced);localStorage.setItem('quiz-reduced-motion',String(reduced));},[reduced]);
  return <div className="settings-page"><PageHeading motif="settings" title="Settings" subtitle="Your profile, security, and a few personal preferences."/><div className="settings-grid"><ProfileInformation/><PasswordSecurityPanel/><section className="panel"><h3>Account Settings</h3><label className="settings-preference"><input type="checkbox" checked={reduced} onChange={event=>setReduced(event.target.checked)}/>Reduce animations on this device</label><p className="help-text">Your system’s reduced-motion preference is always respected.</p><button className="button" onClick={()=>void logout().then(()=>navigate('/',{replace:true})).catch(e=>toast(e.message))}>Sign out</button></section><section className="panel"><h3>About Hootly</h3><p>A little wiser. A lot closer.</p><p className="help-text">Instant Quiz questions are provided by Open Trivia Database under CC BY-SA 4.0.</p><Link className="text-link" to="/credits">Credits &amp; Licenses</Link></section><DangerZone/></div></div>;
}
