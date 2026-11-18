import { memo,useEffect,useState } from 'react';
import { motion } from 'motion/react';
import { Trophy } from 'lucide-react';
import type { Ranking } from '../../api';
import { AnimatedNumber } from '../ui/AnimatedNumber';
import { useQuietMotion } from '../ui/MotionProvider';
import { PlayerOwl } from '../avatar/PlayerOwl';
import { useLeaderboardReactions } from './useLeaderboardReactions';

function ScoreDelta({id,value}:{id:string;value:number}){const [shown,setShown]=useState(true),quiet=useQuietMotion();useEffect(()=>{setShown(true);const timer=setTimeout(()=>setShown(false),1800);return()=>clearTimeout(timer);},[id]);return shown&&value>0?<motion.span className="score-delta" initial={quiet?false:{opacity:0,y:5}} animate={{opacity:1,y:0}} aria-label={`${value} points gained`}>+{value}</motion.span>:null;}

export const Leaderboard=memo(function Leaderboard({rows,userId,final=false,podium=false,sessionKey='ranking'}:{rows:Ranking[];userId?:string;final?:boolean;podium?:boolean;sessionKey?:string}){
  const eligible=rows.filter(p=>!p.forfeited),others=final?rows.filter(p=>p.forfeited||eligible.indexOf(p)>=3):rows;
  const quiet=useQuietMotion(),events=useLeaderboardReactions(eligible,final,sessionKey),showPodium=final||podium;
  return <>{showPodium&&eligible.length>0&&<div className={`podium-stage ${final?'podium-stage-final':''}`} data-quiet={quiet||undefined}>{final&&!quiet&&<div className="podium-confetti" aria-hidden="true">{Array.from({length:18},(_,index)=><i key={index} style={{left:`${5+(index*37)%90}%`,background:['#d4b4fa','#eed28c','#9bcfdb','#cca5bc'][index%4],animationDelay:`${-(index*.73)}s`,animationDuration:`${5+index%3}s`}}/>)}</div>}<div className={`podium ${final?'podium-final':'podium-live'}`} aria-label="Leading players">{eligible.slice(0,3).map((p,index)=><motion.div layout={!quiet} key={p.id} className={`podium-place podium-place-${index+1} podium-rank-${p.rank}`} data-rank={p.rank} data-player-id={final?p.id:undefined} initial={false} animate={{opacity:1,y:0}} transition={{layout:{duration:quiet?0:.4}}}>
    <span className="podium-crown">{p.rank===1?<Trophy size={25} aria-label="Rank 1"/>:<span>#{p.rank}</span>}</span>
    <div className="podium-perch"><PlayerOwl identity={p.id} avatar={p.avatar} name={p.displayName} size={100} presentation="podium" celebrate={final&&(p.rank===1||p.rank===3)} rank={final?p.rank:undefined} animateIdle mood={final?p.rank===1?'celebrating':p.rank===2?'disappointed':p.rank===3?'happy':'calm':'calm'}/></div>
    <div className="podium-plinth" aria-label={`Rank ${p.rank} platform`}><span>{p.rank}</span></div><div className="podium-caption"><strong>{p.displayName}</strong>{p.username&&eligible.filter(row=>row.displayName===p.displayName).length>1&&<small>@{p.username}</small>}<small><AnimatedNumber value={p.points}/> points</small></div>
  </motion.div>)}</div></div>}
  <ol className={`leaderboard-list ${final?'final-rankings':''}`} start={final?4:1} aria-label={final?'Other final rankings':'Current ranking'}>{others.map(p=><motion.li layout={quiet?false:'position'} initial={false} animate={{opacity:1,y:0}} transition={{layout:{type:'spring',stiffness:320,damping:32,duration:quiet?0:undefined}}} className={`${p.rank===1?'ranking-winner':''} ${p.id===userId?'ranking-you':''}`} key={p.id} data-player-id={p.id} data-rank={p.rank}>
    <span className="ranking-position">{p.forfeited?'—':p.rank===1?<Trophy size={20} aria-label="Rank 1"/>:`${p.rank}`}</span>
    <PlayerOwl identity={p.id} avatar={p.avatar} name={p.displayName} size={52} animateIdle rank={final&&!p.forfeited?p.rank:undefined} celebrate={final&&!p.forfeited&&(p.rank===1||p.rank===3)} mood={final&&!p.forfeited?p.rank===1?'celebrating':p.rank===2?'disappointed':p.rank===3?'happy':'calm':'calm'}/>
    <div className="ranking-name"><strong>{p.displayName}{p.id===userId&&<span className="you-label">YOU</span>}</strong><small>{p.username&&eligible.filter(row=>row.displayName===p.displayName).length>1?`@${p.username} ? `:""}{p.forfeited?'Left the game':`${p.correctCount} correct ${p.correctCount===1?'answer':'answers'}`}</small></div>
    <strong className="ranking-points">{events[p.id]?.delta>0&&<ScoreDelta key={events[p.id].reaction.id} id={events[p.id].reaction.id} value={events[p.id].delta}/>}<AnimatedNumber value={p.points}/><small>PTS</small></strong>
  </motion.li>)}</ol></>;
});
