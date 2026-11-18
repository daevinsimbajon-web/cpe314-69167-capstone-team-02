import { useEffect, useRef, useState } from 'react';
import { useQuietMotion } from '../ui/MotionProvider';

/** One visibility observer per scene; stars/flames use inexpensive CSS transforms. */
export function useAtmosphereMotion(){
  const ref=useRef<HTMLDivElement>(null),quiet=useQuietMotion(),[paused,setPaused]=useState(()=>document.hidden);
  useEffect(()=>{let visible=true;const update=()=>setPaused(document.hidden||!visible||!!(navigator as Navigator&{connection?:{saveData:boolean}}).connection?.saveData);
    const observer=typeof IntersectionObserver==='undefined'?null:new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;update();});
    if(ref.current)observer?.observe(ref.current);document.addEventListener('visibilitychange',update);update();
    return()=>{observer?.disconnect();document.removeEventListener('visibilitychange',update);};
  },[]);
  return {ref,paused:quiet||paused};
}
