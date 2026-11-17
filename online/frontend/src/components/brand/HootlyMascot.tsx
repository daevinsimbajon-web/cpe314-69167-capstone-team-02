import { useQuietMotion } from '../ui/MotionProvider';
import { HootlyCharacter } from './HootlyCharacter';
import { useMascotAnimationController } from './MascotAnimationController';
import type { MascotEvent, MascotMood, MascotSceneName } from '../../config/mascotScenes';

export interface HootlyMascotProps {
  scene:MascotSceneName; mood?:MascotMood; event?:MascotEvent; size?:number;
  className?:string; label?:string; attention?:number; privateInput?:boolean;
  /** Repeat the landing welcome only; authenticated reactions remain event-driven. */
  loopMotion?:boolean;
  onReactionComplete?:(event:MascotEvent)=>void;
}

/** The approved layered character; static logo assets are independent of this renderer. */
export function HootlyMascot({scene,mood,event,size=180,className='',label,attention,privateInput=false,loopMotion=false,onReactionComplete}:HootlyMascotProps){
  const quiet=useQuietMotion(),animation=useMascotAnimationController(scene,mood,event,quiet,onReactionComplete);
  const loop=scene==='landing'&&loopMotion&&!quiet;
  return <div className={`hootly-mascot owl-mascot scene-${scene} ${className}`} style={{width:size,maxWidth:'100%'}}
    data-scene={scene} data-mood={animation.currentMood} data-event={animation.reaction?.type} data-reaction-id={animation.reaction?.id} data-private={privateInput||undefined} data-quiet={quiet||undefined} data-looping={loop||undefined}
    role={label?'img':undefined} aria-label={label} aria-hidden={label?undefined:true}>
    <HootlyCharacter scene={scene} expression={animation.expression} attention={attention??(scene==='landing'?-.65:scene==='gameplay'?.6:0)} privateInput={privateInput} size={size} playGesture={animation.playGesture||loop} loopMotion={loop} gestureKey={animation.gestureKey}/>
    {scene==='loading'&&<span className="hootly-loading-dots"><i/><i/><i/></span>}
  </div>;
}
