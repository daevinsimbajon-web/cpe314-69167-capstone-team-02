import { memo,useEffect,useId,useRef,useState } from 'react';
import { motion } from 'motion/react';
import { useQuietMotion } from '../ui/MotionProvider';
import { birdShapes } from './birdShapes';
import { avatarFor,birdSpecies,readAvatar,type AvatarConfig } from './avatarConfig';
import '../../styles/player-owl.css';

export type PlayerMood='idle'|'happy'|'curious'|'calm'|'sleepy'|'excited'|'focused'|'surprised'|'thinking'|'celebrating'|'encouraged'|'disappointed';
export interface OwlReaction {id:string;type:'gain'|'leader'|'first'|'second'|'third'|'encourage'|'correct'|'incorrect'|'timeout'}
// Rich body / light face / darker wing; identity never changes with emotion.
const palettes=[['#9274cf','#eee3ff','#65469a'],['#794dc2','#e1ceff','#50318f'],['#445d91','#dce6ff','#283d69'],['#377f8c','#d7f5f1','#235460'],['#40846c','#dcf4dc','#245d4a'],['#b86b91','#ffe0ed','#87466c'],['#c48a72','#ffeadb','#8d5a4b'],['#987653','#faead7','#694d39'],['#6564b1','#e2dfff','#444276']];
const irises=['#563281','#1a6472','#346043','#744b25','#344d88','#883f68'];
const restMoods:PlayerMood[]=['happy','curious','calm','sleepy','excited','focused'];

/** Procedural player artwork, separate from Hootly's approved branded character. */
export const PlayerOwl=memo(function PlayerOwl({identity,avatar,name,size=44,mood='idle',reaction,animateIdle=false,presentation='profile',celebrate=false,rank}:{identity:string;avatar?:AvatarConfig|null;name?:string;size?:number;mood?:PlayerMood;reaction?:OwlReaction;animateIdle?:boolean;presentation?:'profile'|'podium';celebrate?:boolean;rank?:number}){
  const config=readAvatar(avatar)??avatarFor(identity),quiet=useQuietMotion(),uid=useId().replaceAll(':',''),seen=useRef(new Set<string>()),[active,setActive]=useState<OwlReaction|null>(null),[blink,setBlink]=useState(false);
  const pending=useRef<{reaction:OwlReaction;until:number}|null>(null);
  useEffect(()=>{if(!reaction)return;if(!seen.current.has(reaction.id)){seen.current.add(reaction.id);pending.current={reaction,until:Date.now()+(reaction.type==='first'?1500:1050)};}
    const current=pending.current;if(!current||current.reaction.id!==reaction.id)return;
    if(quiet||current.until<=Date.now()){setActive(null);pending.current=null;return;}
    setActive(current.reaction);const timer=setTimeout(()=>{setActive(null);pending.current=null;},current.until-Date.now());return()=>clearTimeout(timer);
  },[reaction?.id,quiet]);
  useEffect(()=>{if(quiet||!animateIdle)return;let timer:ReturnType<typeof setTimeout>,open:ReturnType<typeof setTimeout>;const schedule=()=>{clearTimeout(timer);if(document.hidden)return;timer=setTimeout(()=>{setBlink(true);open=setTimeout(()=>{setBlink(false);schedule();},135);},4000+(config.iris*353+config.eyes*719)%2700);};const visibility=()=>{setBlink(false);clearTimeout(open);schedule();};schedule();document.addEventListener('visibilitychange',visibility);return()=>{clearTimeout(timer);clearTimeout(open);document.removeEventListener('visibilitychange',visibility);};},[animateIdle,quiet,config.iris,config.eyes]);
  useEffect(()=>{if(quiet||active?.type!=='first')return;const shut=setTimeout(()=>setBlink(true),450),open=setTimeout(()=>setBlink(false),585);return()=>{clearTimeout(shut);clearTimeout(open);setBlink(false);};},[active?.id,quiet]);
  const expression=rank!==undefined?(rank===1?'celebrating':rank===2?'disappointed':rank===3?'happy':'calm'):active?(active.type==='first'?'celebrating':active.type==='second'?'disappointed':active.type==='leader'?'excited':active.type==='encourage'||active.type==='incorrect'?'encouraged':active.type==='timeout'?'surprised':'happy'):mood==='idle'?restMoods[config.expression]:mood;
  const species=config.species??0,shape=birdShapes[species];
  const [body,face,wing]=palettes[config.body],iris=irises[config.iris],win=active?.type==='first',reacting=!!active&&!quiet;
  const sleepy=expression==='sleepy'||rank===undefined&&config.eyes===1,focused=expression==='focused',happy=['happy','excited','celebrating'].includes(expression),rankHappy=rank===1||rank===3;
  const tilt=expression==='disappointed'?4:expression==='curious'?5:expression==='thinking'?-4:expression==='encouraged'?-3:0;
  const motionKey=active?.id??'rest',hop=win?-17:active?.type==='third'?-6:active?.type==='second'?-9:active?.type==='leader'?-5:-3;
  const wingRaised=rank===1?18:rank===3?8:rank!==undefined?0:config.wings===2?-8:0;
  return <span className={`player-owl player-owl-${presentation}`} style={{width:size,height:size}} data-final-rank={rank} data-idle={animateIdle&&!quiet||undefined} data-avatar={JSON.stringify(config)} data-species={birdSpecies[species]} data-identity={identity} data-expression={expression} data-reaction={active?.type} data-reaction-id={active?.id} data-quiet={quiet||undefined} data-celebrating={celebrate&&!quiet||undefined}>
    <svg viewBox="0 0 160 168" fill="none" role={name?'img':undefined} aria-label={name?`${birdSpecies[species]} avatar for ${name}`:undefined} aria-hidden={name?undefined:true}>
      <defs>{[58,102].map(cx=><clipPath id={`${uid}-eye-${cx}`} key={cx}><ellipse cx={cx} cy="78" rx="21" ry={species===6?17:species===3?21:23}/></clipPath>)}</defs>
      <motion.ellipse key={`${motionKey}:shadow`} cx="80" cy="157" rx="40" ry="5" fill="#b8a2e9" opacity=".12" animate={reacting?{scaleX:[1,.75,1],opacity:[.12,.05,.12]}:{scaleX:1,opacity:.12}} transition={{duration:quiet?0:.9}}/>
      <g className="player-celebration"><motion.g key={`${motionKey}:body`} data-part="player-body" style={{transformOrigin:'80px 148px'}} initial={false} animate={reacting?{y:expression==='encouraged'?[0,1,2,1,0,0]:[0,2,hop,0,1,0],scaleX:[1,1.03,.98,1.03,1,1],scaleY:[1,.96,1.04,.97,1,1]}:{y:0,scaleX:1,scaleY:1}} transition={{duration:win?1.4:quiet?0:.95,ease:'easeInOut'}}>
        <path data-part="player-silhouette" d={shape.body} fill={body}/>
        {(species===0||species===4)&&<path d={species===4?"m33 48-5-22 24 21m55 1 5-22-24 21":"m40 51-4-17 22 14m62 3 4-17-22 14"} stroke={face} strokeOpacity=".5" strokeWidth="4" strokeLinecap="round"/>}{species===2&&<path d="m43 36 6 4 6-4m18-7 7 4 7-4m18 7 6 4 6-4" stroke={face} strokeOpacity=".6" strokeWidth="3" strokeLinecap="round"/>}{species===5&&<path d="m42 37 8 5m60-5-8 5M76 29l4 6 4-6" stroke={wing} strokeWidth="4" strokeLinecap="round"/>}{species===6&&<path d="m42 47 14 6m62-6-14 6" stroke={face} strokeOpacity=".7" strokeWidth="3" strokeLinecap="round"/>}
        <path data-part="player-feet" d="m55 147-7 8m16-8v9m41-9 7 8m-16-8v9" stroke="#f4c568" strokeWidth="5" strokeLinecap="round"/>
        <motion.g data-part="player-head" initial={false} animate={{rotate:tilt,y:expression==='disappointed'?2:0}} style={{transformOrigin:'80px 106px'}} transition={{duration:quiet?0:.3}}>
          <path data-part="player-facial-disk" d={shape.face} fill={face} opacity={species===7?.7:1}/>
          {[58,102].map((cx,index)=><g key={cx} data-part={index?'player-right-eye':'player-left-eye'}><ellipse cx={cx} cy="78" rx="21" ry={species===6?17:species===3?21:23} fill="#fffdfb"/>
            {rankHappy?<path data-part="player-happy-eye" d={`M${cx-12} 82q12-16 24 0`} stroke={wing} strokeWidth="4" strokeLinecap="round"/>:<><g clipPath={`url(#${uid}-eye-${cx})`}><motion.g data-part="player-pupil" initial={false} animate={{x:expression==='curious'?index?-1:2:focused?1:0,y:focused?-2:expression==='thinking'?-3:0}} transition={{duration:quiet?0:.25}}><circle cx={cx} cy="80" r="13" fill={iris}/><circle cx={cx} cy="81" r="8" fill="#17213a"/><circle cx={cx+4} cy="74" r={config.eyes===3?5:4} fill="white"/>{config.eyes===3&&<path d={`m${cx-8} 83 2-4 2 4 4 2-4 2-2 4-2-4-4-2Z`} fill="white"/>}</motion.g>
              <motion.path data-part="player-eyelid" d={`M${cx-24} 4h48v42q-24 12-48 0Z`} fill={face} stroke="none" initial={false} animate={{y:blink?58:sleepy?22:focused?11:0}} transition={{duration:quiet?0:.09}}/>
            </g>{blink&&<path d={`M${cx-9} 80q9 5 18 0`} stroke={wing} strokeWidth="2" strokeLinecap="round"/>}</>}
            {rank===2?<path data-part="player-rank-brow" d={index?`M${cx-12} 56q12 3 24 7`:`M${cx-12} 63q12-4 24-7`} stroke={body} strokeWidth="3" strokeLinecap="round"/>:rank===undefined&&config.eyes===2?<path d={`M${cx-14} 51q10-6 23 1`} stroke={body} strokeWidth="3" strokeLinecap="round"/>:null}
          </g>)}
          <path data-part="player-beak" d={shape.beak} fill={species===7?wing:'#e9ad49'} stroke={species===7?'#d3baf2':undefined} strokeWidth="1.5"/>
          {expression==='surprised'?<ellipse cx="80" cy="119" rx="4" ry="5" fill={wing}/>:<path data-part="player-mouth" d={happy?'M72 116q8 11 16 0':expression==='disappointed'?'M73 122q7-6 14 0':expression==='encouraged'?'M74 120q6 4 12 0':'M75 118q5 3 10 0'} stroke={wing} strokeWidth="2.5" strokeLinecap="round"/>}
          {config.accessory===4&&<g data-part="player-glasses" fill="none" stroke={wing} strokeWidth="2"><circle cx="58" cy="79" r="20.5"/><circle cx="102" cy="79" r="20.5"/><path d="M78.5 77q1.5-3 3 0M37 75l-7-3m93 3 7-3"/></g>}
        </motion.g>
        <g className="player-celebration-wing-left"><motion.g data-part="player-left-wing" initial={false} animate={{rotate:reacting?(win?[0,25,-5,25,0]:[0,12,0]):wingRaised}} style={{transformOrigin:'25px 91px'}} transition={{duration:quiet?0:win?1.3:.9}}><path d={config.wings===1?'M26 84q-18 13 8 41 10-24-8-41Z':'M26 84q-24 14 9 55 12-30-9-55Z'} fill={wing}/><path d="m26 106 5 13" stroke={face} strokeOpacity=".35" strokeWidth="3" strokeLinecap="round"/></motion.g></g>
        <g className="player-celebration-wing-right"><motion.g data-part="player-right-wing" initial={false} animate={{rotate:reacting?(win?[0,-25,5,-25,0]:active?.type==='second'?[0,-28,-8,-25,0]:[0,-12,0]):-wingRaised}} style={{transformOrigin:'135px 91px'}} transition={{duration:quiet?0:win?1.3:.9}}><path d={config.wings===1?'M134 84q18 13-8 41-10-24 8-41Z':'M134 84q24 14-9 55-12-30 9-55Z'} fill={wing}/><path d="m134 106-5 13" stroke={face} strokeOpacity=".35" strokeWidth="3" strokeLinecap="round"/></motion.g></g>
        <g data-part="player-marking" fill={face} opacity=".65">{config.marking===0?<path d="m73 136 7 5 7-5-7 9Z"/>:config.marking===1?<><circle cx="70" cy="137" r="3"/><circle cx="90" cy="137" r="3"/></>:config.marking===2?<path d="m65 134 5 5 5-5m10 0 5 5 5-5" stroke={face} strokeWidth="3" strokeLinecap="round"/>:<path d="M82 129a8 8 0 1 0 6 13 7 7 0 0 1-6-13Z"/>}</g>
        <g data-part="player-accessory">{config.accessory===0?<path d="M65 45a9 9 0 1 0 7 14 8 8 0 0 1-7-14Z" fill="#ffe0a3"/>:config.accessory===1?<path d="m81 38 3 7 8 1-6 5 2 8-7-4-7 4 2-8-6-5 8-1Z" fill="#f6ce78"/>:config.accessory===2?<><path d="M45 121q35 17 70 0l-1 12q-34 15-68-1Z" fill="#b8d8e8"/><path d="m98 132 14 15-13 1-9-14Z" fill="#8fb8d6"/></>:config.accessory===3?<path d="M39 52q41-13 82 0" stroke="#f0cd96" strokeWidth="6"/>:config.accessory===5?<path d="M108 47q-5-21 16-24 1 17-16 24Zm1-1 11-16" fill="#b4d4a0" stroke="#739b6e" strokeWidth="1.5"/>:config.accessory===6?<path d="m62 39-3-15 14 7 7-14 8 14 14-7-4 15Z" fill="#e5bd63" stroke="#ffe4a2" strokeWidth="2"/>:config.accessory===7?<path d="M81 41q-9-24 4-30 14 17-4 30Z" fill={face}/>:null}</g>
      </motion.g></g>
    </svg>
  </span>;
},(a,b)=>a.identity===b.identity&&a.name===b.name&&a.size===b.size&&a.mood===b.mood&&a.animateIdle===b.animateIdle&&a.presentation===b.presentation&&a.celebrate===b.celebrate&&a.rank===b.rank&&a.reaction?.id===b.reaction?.id&&a.reaction?.type===b.reaction?.type&&(a.avatar===b.avatar||a.avatar?.version===b.avatar?.version&&(['body','eyes','iris','wings','marking','accessory','expression','species'] as const).every(key=>a.avatar?.[key]===b.avatar?.[key])));
