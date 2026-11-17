import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, ApiError, type Profile } from './api';
import { AnimatePresence, motion } from 'motion/react';
import { CheckCircle2, X } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';

interface Auth { clearSession:()=>void; user: Profile | null; loading: boolean; endingSession:boolean; refresh: () => Promise<void>; logout: () => Promise<void>; deleteAccount: (password:string,confirmation:string) => Promise<void> }
const AuthContext = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null), [loading, setLoading] = useState(true);
  const navigate=useNavigate(),location=useLocation(),[endingSession,setEndingSession]=useState(false);
  useEffect(()=>{if(endingSession&&['/','/login','/forgot-password'].includes(location.pathname))setEndingSession(false);},[endingSession,location.pathname]);
  const generation=useRef(0);
  const refresh = async () => { const current=generation.current;const profile=await api<Profile>('/me');if(current===generation.current)setUser(profile); };
  useEffect(() => {
    let active = true;const current=generation.current;
    api<Profile>('/me').then(u => { if (active&&current===generation.current) setUser(u); }).catch(error => {
      if (!(error instanceof ApiError && error.status === 401)) console.warn('Session check unavailable');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  const clearSession = () => {generation.current++;setEndingSession(true);setUser(null);};
  const finishSession = () => { generation.current++;setEndingSession(true);setUser(null);navigate('/',{replace:true}); };
  const logout = async () => { await api('/auth/logout', { method: 'POST' }); finishSession(); };
  const deleteAccount = async (password:string,confirmation:string) => { await api('/account/me',{method:'DELETE',body:JSON.stringify({password,confirmation})});finishSession(); };
  return <AuthContext.Provider value={{ user, loading, endingSession, refresh, logout, deleteAccount, clearSession }}>{children}</AuthContext.Provider>;
}
export function useAuth() { const auth = useContext(AuthContext); if (!auth) throw new Error('Auth provider missing'); return auth; }

const ToastContext = createContext<(text: string) => void>(() => {});
export function ToastProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<{id: number; text: string}[]>([]);
  const toast = (text: string) => {
    const id = Date.now() + Math.random(); setMessages(current => [...current, { id, text }]);
    setTimeout(() => setMessages(current => current.filter(message => message.id !== id)), 6000);
  };
  return <ToastContext.Provider value={toast}>{children}<div className="toasts" aria-live="polite">
    <AnimatePresence>{messages.map(message => <motion.div role="status" className="toast" key={message.id} initial={{opacity:0,y:8}} animate={{opacity:1,y:0}} exit={{opacity:0}}><CheckCircle2 size={18}/>{message.text}
      <button aria-label="Dismiss notification" onClick={() => setMessages(current => current.filter(m => m.id !== message.id))}><X size={16}/></button></motion.div>)}</AnimatePresence>
  </div></ToastContext.Provider>;
}
export const useToast = () => useContext(ToastContext);
