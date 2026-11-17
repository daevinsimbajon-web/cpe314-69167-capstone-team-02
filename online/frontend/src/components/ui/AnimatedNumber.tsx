import { useEffect, useState } from 'react';
import { useQuietMotion } from './MotionProvider';
export function AnimatedNumber({value,precision=0}:{value:number;precision?:number}) {
  const quiet=useQuietMotion(),[shown,setShown]=useState(()=>quiet||document.hidden?value:0);
  useEffect(()=>{if(quiet||document.hidden){setShown(value);return;}let frame=0;const start=performance.now();const from=shown;
    const factor=10**precision;
    const tick=(now:number)=>{const progress=Math.max(0,Math.min(1,(now-start)/500));setShown(Math.round((from+(value-from)*(1-(1-progress)**3))*factor)/factor);if(progress<1)frame=requestAnimationFrame(tick);};
    frame=requestAnimationFrame(tick);return()=>cancelAnimationFrame(frame);
  // Start from the last displayed score only when the authoritative score changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[value,quiet,precision]);
  return <span className="animated-number" aria-label={value.toLocaleString(undefined,{maximumFractionDigits:precision})}>{shown.toLocaleString(undefined,{maximumFractionDigits:precision})}</span>;
}
