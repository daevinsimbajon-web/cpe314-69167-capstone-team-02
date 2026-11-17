import { useQuietMotion } from '../ui/MotionProvider';
export function Celebration() {
  const quiet=useQuietMotion();
  if(quiet)return null;
  return <div className="celebration" aria-hidden="true">{Array.from({length:24},(_,i)=><i key={i} style={{left:`${(i*43)%100}%`,background:['#8B5CF6','#C4B5FD','#22D3EE','#FBBF24'][i%4],animationDelay:`${(i%6)*.12}s`,rotate:`${i*31}deg`}}/>)}</div>;
}
