import { useEffect,useRef,useState } from 'react';
import type { Ranking } from '../../api';
import type { OwlReaction } from '../avatar/PlayerOwl';
export interface RankReaction {type:OwlReaction['type'];delta:number}
export function rankingReactions(before:Ranking[]|null,rows:Ranking[],wasFinal:boolean,final:boolean):Record<string,RankReaction>{
  const result:Record<string,RankReaction>={};if(!before)return result;
  const previous=new Map(before.map(p=>[p.id,p]));
  for(const row of rows){const old=previous.get(row.id);if(!old)continue;const delta=Math.max(0,row.points-old.points);
    if(final&&!wasFinal)result[row.id]={type:row.rank===1?'first':row.rank===2?'second':row.rank===3?'third':'encourage',delta};
    else if(!final&&row.rank===1&&old.rank>1)result[row.id]={type:'leader',delta};
    else if(!final&&delta>0)result[row.id]={type:'gain',delta};
  }
  return result;
}
export function useLeaderboardReactions(rows:Ranking[],final:boolean,sessionKey:string){
  const previous=useRef<{rows:Ranking[];final:boolean}|null>(null),sequence=useRef(0);
  const [reactions,setReactions]=useState<Record<string,{reaction:OwlReaction;delta:number}>>({});
  useEffect(()=>{previous.current=null;setReactions({});},[sessionKey]);
  useEffect(()=>{const changes=rankingReactions(previous.current?.rows??null,rows,previous.current?.final??false,final);previous.current={rows:rows.map(r=>({...r})),final};
    if(!Object.keys(changes).length)return;const next:typeof reactions={};for(const [id,change] of Object.entries(changes))next[id]={reaction:{id:`${sessionKey}:${++sequence.current}:${id}`,type:change.type},delta:change.delta};setReactions(current=>({...current,...next}));
  },[rows,final,sessionKey]);
  return reactions;
}
