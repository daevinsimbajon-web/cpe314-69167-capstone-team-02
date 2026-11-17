import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { MotionConfig } from 'motion/react';

const Preference = createContext(false);
export function MotionProvider({children}:{children:ReactNode}) {
  const [reduced,setReduced]=useState(()=>document.documentElement.dataset.reducedMotion==='true'||window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  useEffect(()=>{
    const visibility=()=>{document.documentElement.dataset.pageHidden=String(document.hidden);};
    visibility();document.addEventListener('visibilitychange',visibility);
    const media=window.matchMedia('(prefers-reduced-motion: reduce)');
    const update=()=>setReduced(media.matches||document.documentElement.dataset.reducedMotion==='true');
    const observer=new MutationObserver(update);observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-reduced-motion']});
    media.addEventListener('change',update);update();
    return()=>{observer.disconnect();media.removeEventListener('change',update);document.removeEventListener('visibilitychange',visibility);};
  },[]);
  return <Preference.Provider value={reduced}><MotionConfig reducedMotion={reduced?'always':'user'} transition={{duration:reduced?0:.24,ease:[.22,1,.36,1]}}>{children}</MotionConfig></Preference.Provider>;
}
export const useQuietMotion=()=>useContext(Preference);
