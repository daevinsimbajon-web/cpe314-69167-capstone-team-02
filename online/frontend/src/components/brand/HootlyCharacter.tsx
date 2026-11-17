import { useEffect, useId, useState } from 'react';
import { motion } from 'motion/react';
import { useQuietMotion } from '../ui/MotionProvider';
import type { MascotSceneName } from '../../config/mascotScenes';
import '../../styles/character.css';

export type OwlExpression = 'idle' | 'waving' | 'applauding' | 'welcoming' | 'happy' | 'sad' | 'curious' | 'focused' | 'surprised' | 'celebrating' | 'thinking';
export type OwlScene = MascotSceneName;
export interface HootlyCharacterProps {
  expression?: OwlExpression;
  scene?: OwlScene;
  privateInput?: boolean;
  attention?: number;
  size?: number;
  label?: string;
  /** Landing welcome or development review only; game and auth reactions stay finite. */
  loopMotion?: boolean;
  playGesture?: boolean;
  gestureKey?: string;
}

const silhouette='M64 71 54 27q33 4 48 21h36q15-17 48-21l-10 44q28 27 28 74c0 46-32 74-84 74s-84-28-84-74q0-47 28-74Z';

export function characterMovement(expression:OwlExpression,quiet:boolean,covered=false){
  const rest={y:0,rotate:0,scaleX:1,scaleY:1};
  if(quiet||covered||expression==='focused'||expression==='idle')return {pose:'still',body:rest,feet:{y:0,scaleY:1},shadow:{scaleX:1,opacity:.1},duration:0};
  if(expression==='waving'||expression==='applauding')return {pose:expression==='waving'?'welcome-wave':'ranking-applause',body:{y:[0,-2,0],rotate:[0,expression==='waving'?-2:1,0],scaleX:1,scaleY:1},feet:{y:0,scaleY:1},shadow:{scaleX:[1,.97,1],opacity:.1},duration:1.1};
  if(expression==='celebrating')return {pose:'victory-jumps',body:{y:[0,3,-24,0,3,-17,0,1,0],rotate:[0,-3,-5,2,0,4,0,-1,0],scaleX:[1,1.05,.97,1.05,1.04,.98,1.04,1.01,1],scaleY:[1,.94,1.06,.95,.96,1.04,.96,.99,1]},feet:{y:[0,0,-5,2,0,-3,2,0,0],scaleY:[1,.85,.75,1.12,.9,.8,1.1,1,1]},shadow:{scaleX:[1,1.06,.65,1.08,1.04,.75,1.05,1.01,1],opacity:[.1,.12,.045,.13,.12,.06,.12,.1,.1]},duration:1.5};
  if(expression==='welcoming'||expression==='happy'){
    const welcome=expression==='welcoming';
    return {pose:welcome?'welcome-hop':'happy-hop',body:{y:[0,3,welcome?-13:-18,0,2,0],rotate:[0,-2,welcome?-2:3,1,0,0],scaleX:[1,1.04,.98,1.05,1.01,1],scaleY:[1,.95,1.04,.94,.99,1]},feet:{y:[0,0,-3,2,0,0],scaleY:[1,.9,.8,1.1,1,1]},shadow:{scaleX:[1,1.04,welcome?.8:.72,1.08,1.02,1],opacity:[.1,.12,.06,.13,.11,.1]},duration:welcome?1.05:.85};
  }
  if(expression==='sad')return {pose:'gentle-settle',body:{y:[0,3,1,0],rotate:[0,-3,-2,0],scaleX:[1,1.015,1.005,1],scaleY:[1,.97,.99,1]},feet:{y:[0,1,0,0],scaleY:[1,.97,1,1]},shadow:{scaleX:[1,1.03,1.01,1],opacity:[.1,.11,.1,.1]},duration:1.3};
  if(expression==='curious')return {pose:'curious-lean',body:{y:[0,-2,0],rotate:[0,4,0],scaleX:1,scaleY:1},feet:{y:0,scaleY:1},shadow:{scaleX:[1,.97,1],opacity:.1},duration:1.4};
  if(expression==='surprised')return {pose:'startle',body:{y:[0,-7,0],rotate:0,scaleX:[1,.98,1],scaleY:[1,1.04,1]},feet:{y:[0,-2,0],scaleY:1},shadow:{scaleX:[1,.85,1],opacity:[.1,.07,.1]},duration:.55};
  return {pose:'weight-shift',body:{y:[0,-1,0],rotate:[0,-2,0],scaleX:1,scaleY:1},feet:{y:0,scaleY:1},shadow:{scaleX:[1,.98,1],opacity:.1},duration:2};
}

/** User-approved Hootly character with independent facial, wing and body motion. */
export function HootlyCharacter({expression='welcoming',scene='landing',privateInput=false,attention=0,size=240,label,loopMotion=false,playGesture=true,gestureKey='entry'}:HootlyCharacterProps){
  const quiet=useQuietMotion(),uid=useId().replaceAll(':',''),[blink,setBlink]=useState(false);
  useEffect(()=>{
    if(quiet||scene==='gameplay')return;
    let timer:ReturnType<typeof setTimeout>,opening:ReturnType<typeof setTimeout>;
    const schedule=()=>{timer=setTimeout(()=>{setBlink(true);opening=setTimeout(()=>{setBlink(false);schedule();},135);},3300+Math.random()*4200);};
    schedule();return()=>{clearTimeout(timer);clearTimeout(opening);};
  },[quiet,scene]);
  const covered=privateInput&&scene==='login',closed=covered||(!quiet&&blink),hearts=expression==='welcoming'&&!covered;
  const happy=['welcoming','happy','celebrating','waving','applauding'].includes(expression),sad=expression==='sad',celebrate=expression==='celebrating';
  const gaze=Math.max(-1,Math.min(1,attention));
  const headAngle=covered?0:sad?-5:expression==='curious'?gaze*5+4:expression==='thinking'?-4:scene==='login'?gaze*3:0;
  const wave=playGesture&&(scene==='dashboard'||expression==='welcoming'||expression==='waving');
  const movement=characterMovement(playGesture?expression:'focused',quiet,covered);
  const wingDuration=quiet?0:covered||!playGesture?.35:Math.max(movement.duration,1.05);
  const movementTransition={duration:movement.duration,ease:'easeInOut' as const,repeat:loopMotion&&movement.duration>0?Infinity:0,repeatDelay:celebrate?1.4:2.2};
  const left=covered?{x:28,y:-34,rotate:-55,scale:1.3}:quiet?{x:0,y:0,rotate:0,scale:1}
    :{x:0,y:0,rotate:playGesture&&celebrate?[0,32,-5,32,-5,24,0]:playGesture&&expression==='applauding'?[0,-25,-8,-25,0]:playGesture&&expression==='happy'?[0,20,0]:sad?-6:0,scale:1};
  const right=covered?{x:-28,y:-34,rotate:55,scale:1.3}:quiet?{x:0,y:0,rotate:0,scale:1}
    :{x:0,y:0,rotate:playGesture&&celebrate?[0,-32,5,-32,5,-24,0]:playGesture&&expression==='applauding'?[0,25,8,25,0]:wave?loopMotion&&scene==='landing'?[0,-55,-28,-55,0]:[0,-27,-8,-27,0]:playGesture&&expression==='happy'?[0,-20,0]:sad?6:0,scale:1};
  const entrance=scene==='landing'?{x:80,y:-20,rotate:9,opacity:.2}:scene==='join'?{x:30,y:0,rotate:-10,opacity:.3}:scene==='dashboard'?{x:0,y:5,rotate:0,opacity:.5}:scene==='lobby'?{x:15,y:0,rotate:-5,opacity:.3}:scene==='registration'?{x:0,y:6,rotate:4,opacity:.4}:{x:0,y:0,rotate:0,opacity:1};
  return <motion.div className="hootly-character" style={{width:size,maxWidth:'100%'}} data-expression={expression} data-scene={scene} data-heart-eyes={hearts||undefined} data-blinking={closed||undefined} data-covered={covered||undefined} data-quiet={quiet||undefined}
    initial={quiet?false:entrance} animate={{x:0,y:0,rotate:0,opacity:1}} transition={{duration:quiet?0:scene==='landing'?.8:.4,ease:'easeOut'}}>
    <svg viewBox="0 0 240 240" fill="none" role={label?'img':undefined} aria-label={label} aria-hidden={label?undefined:true}>
      <defs><clipPath id={`${uid}-body`}><rect x="0" y="154" width="240" height="86"/></clipPath><clipPath id={`${uid}-head`}><rect width="240" height="179"/></clipPath>
        {[86,154].map(cx=><clipPath key={cx} id={`${uid}-eye-${cx}`}><circle cx={cx} cy="112" r="29"/></clipPath>)}
      </defs>
      <motion.ellipse key={`${gestureKey}:shadow`} data-part="ground-shadow" cx="120" cy="228" rx="60" ry="7" fill="#a78bfa" opacity=".1" style={{transformOrigin:'120px 228px'}} initial={quiet||!playGesture?false:{scaleX:1,opacity:.1}} animate={movement.shadow} transition={movementTransition}/>
      <motion.g key={`${gestureKey}:body`} data-part="character-body-motion" data-movement={movement.pose} style={{transformOrigin:'120px 222px'}} initial={quiet||!playGesture?false:{y:0,rotate:0,scaleX:1,scaleY:1}} animate={movement.body} transition={movementTransition}>
      <g data-part="body" clipPath={`url(#${uid}-body)`}><path d={silhouette} fill="#6d47cb"/><path d="m110 189 10 8 10-8" stroke="#a78bfa" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round"/></g>
      <motion.g data-part="feet" style={{transformOrigin:'120px 218px'}} initial={quiet?false:{y:0,scaleY:1}} animate={movement.feet} transition={movementTransition}><path d="m87 213-7 9m16-8v10m57-11 7 9m-16-8v10" stroke="#fbbf24" strokeWidth="6" strokeLinecap="round"/></motion.g>
      <motion.g data-part="head" style={{transformOrigin:'120px 160px'}} initial={false} animate={{rotate:quiet?0:headAngle}} transition={{duration:quiet?0:.4}}>
        <g clipPath={`url(#${uid}-head)`}><path d={silhouette} fill="#6d47cb"/></g>
        <g data-part="ears"><path d="M64 71 59 39l33 22M176 71l5-32-33 22" stroke="#a78bfa" strokeWidth="6" strokeLinecap="round"/></g>
        <path data-part="face-mask" d="M120 94c-23-35-70-31-70 13 0 37 35 54 70 77 35-23 70-40 70-77 0-44-47-48-70-13Z" fill="#ddd4ff"/>
        <path d="M120 94v90c35-23 70-40 70-77 0-44-47-48-70-13Z" fill="#c4b5fd"/>
        {[86,154].map((cx,index)=><g key={cx} data-part={index?'right-eye':'left-eye'}>
          <circle cx={cx} cy="112" r="29" fill="#fbf9ff"/>
          <g clipPath={`url(#${uid}-eye-${cx})`}>
            <motion.g data-part={index?'right-pupil':'left-pupil'} initial={false} animate={{x:gaze*6,y:scene==='gameplay'?-3:scene==='login'?2:0,scale:expression==='surprised'?1.08:1}} style={{transformOrigin:`${cx}px 112px`}} transition={{duration:quiet?0:.22}}>
              {hearts?<g transform={`translate(${cx} 108)`}><path d="M0 16C-5 11-21 1-15-9C-11-16-3-15 0-8C3-15 11-16 15-9C21 1 5 11 0 16Z" fill="#8b45cf"/><path d="M-10-7q2-3 5-1" stroke="white" strokeWidth="3" strokeLinecap="round"/></g>
                :<><circle cx={cx+(index?-2:2)} cy="113" r={expression==='focused'?16:18} fill="#40216f"/><circle cx={cx+(index?-2:2)} cy="113" r="13" stroke="#8b5cf6" strokeWidth="3"/><circle cx={cx+6} cy="106" r="5" fill="white"/><circle cx={cx-5} cy="119" r="2" fill="#22d3ee"/></>}
            </motion.g>
            <motion.rect data-part={index?'right-eyelid':'left-eyelid'} x={cx-31} y="81" width="62" height="62" fill={index?'#c4b5fd':'#ddd4ff'} style={{transformOrigin:`${cx}px 81px`}} initial={false} animate={{scaleY:closed?1:0}} transition={{duration:quiet?0:.08}}/>
          </g>
          {closed&&<path d={`M${cx-14} 113q14 7 28 0`} stroke="#6d47cb" strokeWidth="3" strokeLinecap="round"/>}
          {sad&&<path d={index?'M135 85q15-9 31 0':'M74 85q15-9 31 0'} stroke="#6d47cb" strokeWidth="4" strokeLinecap="round"/>}
          {expression==='focused'&&<path d={index?'m135 85 28-6':'m77 79 28 6'} stroke="#6d47cb" strokeWidth="4" strokeLinecap="round"/>}
        </g>)}
        <path data-part="beak" d="m111 141 9 13 9-13q-9-7-18 0Z" fill="#fbbf24"/>
        {expression==='surprised'?<ellipse data-part="mouth" cx="120" cy="165" rx="5" ry="7" fill="#6d47cb"/>:<motion.path data-part="mouth" initial={false} animate={{d:sad?'M108 170q12-10 24 0':happy?'M108 163q12 15 24 0':expression==='focused'?'M114 166h12':'M109 165q11 6 22 0'}} stroke="#6d47cb" strokeWidth={happy?4:3} strokeLinecap="round" transition={{duration:quiet?0:.2}}/>}
      </motion.g>
      <motion.g data-part="left-wing" initial={quiet?false:{x:0,y:0,rotate:0,scale:1}} animate={left} style={{transformOrigin:'42px 137px'}} transition={{duration:wingDuration,ease:'easeInOut',repeat:loopMotion&&!quiet&&Array.isArray(left.rotate)?Infinity:0,repeatDelay:celebrate?1.4:2.2}}><path d="M46 123c-22 12-23 48 15 69 3-26 4-45-15-69Z" fill="#45328c"/><path d="m44 149 7 20" stroke="#8b5cf6" strokeWidth="4" strokeLinecap="round"/></motion.g>
      <motion.g data-part="right-wing" initial={quiet?false:{x:0,y:0,rotate:0,scale:1}} animate={right} style={{transformOrigin:'198px 137px'}} transition={{duration:wingDuration,ease:'easeInOut',repeat:loopMotion&&!quiet&&Array.isArray(right.rotate)?Infinity:0,repeatDelay:celebrate?1.4:2.2}}><path d="M194 123c22 12 23 48-15 69-3-26-4-45 15-69Z" fill="#45328c"/><path d="m196 149-7 20" stroke="#8b5cf6" strokeWidth="4" strokeLinecap="round"/></motion.g>
      </motion.g>
    </svg>
  </motion.div>;
}
