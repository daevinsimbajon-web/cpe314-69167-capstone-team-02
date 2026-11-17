import { useEffect, useRef, useState } from 'react';
import { mascotScenes, reactionDuration, reactionMoods, type MascotEvent, type MascotMood, type MascotSceneName } from '../../config/mascotScenes';
import type { OwlExpression } from './HootlyCharacter';

const expressions:Record<MascotMood,OwlExpression>={idle:'idle',welcoming:'welcoming',curious:'curious',focused:'focused',happy:'happy',sad:'sad',surprised:'surprised',encouraging:'sad',celebrating:'celebrating',loading:'thinking',waving:'waving',applauding:'applauding'};

/** One finite gesture per confirmed event, followed by the page's resting expression. */
export function useMascotAnimationController(scene:MascotSceneName,mood:MascotMood|undefined,event:MascotEvent|undefined,quiet:boolean,onComplete?: (event:MascotEvent)=>void){
  const hasEntry=!['gameplay','leaderboard','results','lobby','settings','loading'].includes(scene);
  const [entryActive,setEntryActive]=useState(hasEntry),[reaction,setReaction]=useState<MascotEvent|null>(null);
  const seen=useRef(new Set<string>()),lastGesture=useRef(`entry:${scene}`),completed=useRef(onComplete);
  useEffect(()=>{completed.current=onComplete;},[onComplete]);
  useEffect(()=>{setEntryActive(hasEntry);const timer=setTimeout(()=>setEntryActive(false),quiet?0:1600);return()=>clearTimeout(timer);},[scene,quiet,hasEntry]);
  useEffect(()=>{
    if(!event||seen.current.has(event.id))return;
    seen.current.add(event.id);lastGesture.current=event.id;setEntryActive(false);setReaction(event);
  },[event?.id,event?.type]);
  useEffect(()=>{
    if(!reaction)return;
    const timer=setTimeout(()=>{setReaction(null);completed.current?.(reaction);},quiet?0:reactionDuration(reaction.type,scene));
    return()=>clearTimeout(timer);
  },[reaction,scene,quiet]);
  const currentMood=reaction?.type==='success'&&['registration','create'].includes(scene)?'celebrating':reaction?reactionMoods[reaction.type]:mood??mascotScenes[scene].mood;
  return {reaction,currentMood,expression:expressions[currentMood],gestureKey:lastGesture.current,playGesture:!quiet&&(reaction?reaction.type!=='selection':entryActive)};
}
