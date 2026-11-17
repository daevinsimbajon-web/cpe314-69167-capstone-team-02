import type { AvatarConfig } from '../components/avatar/avatarConfig';
export interface StatsQuestion {
  questionId:string;sourceQuestionId?:string;questionLabel?:string;category:string;difficulty?:string;questionType?:string;
  firstTryCorrect:boolean|null;finalCorrect:boolean;attemptCount:number|null;retriesUsed:number|null;elapsedMs:number|null;
  pointsEarned:number;maxPossiblePoints:number;timedOut:boolean;
}
export interface StatsGame {
  gameId:string;playedAt:string;pointsEarned:number;maxPossiblePoints:number;won:boolean;scoringVersion:string;
  presentedQuestions:number;questionsComplete:boolean;questions:StatsQuestion[];
}
export interface StatsExport {format:'hootly.analytics';schemaVersion:1;exportedAt:string;player:{exportPlayerId:string;displayName:string;username?:string;avatar?:AvatarConfig};games:StatsGame[]}
export interface Period {preset:string;from:string;to:string}
export interface Filters {period:Period;category:string;mode:string;ruleset:string}
const day=86400000;
export function periodFor(preset:string,now=new Date()):Period {
  const to=now.toISOString().slice(0,10),year=now.getUTCFullYear();let from='';let end=to;
  if(['7','30','90'].includes(preset))from=new Date(Date.parse(to)-(Number(preset)-1)*day).toISOString().slice(0,10);
  else if(preset==='12months'){const date=new Date(`${to}T00:00:00Z`);date.setUTCFullYear(date.getUTCFullYear()-1);from=date.toISOString().slice(0,10);}
  else if(preset==='year')from=`${year}-01-01`;
  else if(preset==='lastYear'){from=`${year-1}-01-01`;end=`${year-1}-12-31`;}
  else if(/^year:\d{4}$/.test(preset)){const value=preset.slice(5);from=`${value}-01-01`;end=`${value}-12-31`;}
  return {preset,from,to:preset==='all'?'':end};
}
export function dateError(period:Period){return period.from&&period.to&&period.from>period.to?'Choose an end date on or after the start date.':'';}
export function filterGames(games:StatsGame[],filters:Filters):StatsGame[]{
  if(dateError(filters.period))return [];
  const from=filters.period.from?Date.parse(`${filters.period.from}T00:00:00Z`):-Infinity;
  const until=filters.period.to?Date.parse(`${filters.period.to}T00:00:00Z`)+day:Infinity;
  return games.filter(g=>Date.parse(g.playedAt)>=from&&Date.parse(g.playedAt)<until&&(!filters.ruleset||g.scoringVersion===filters.ruleset)).flatMap(game=>{
    if(!filters.category&&!filters.mode)return [game];
    const questions=game.questions.filter(q=>(!filters.category||q.category===filters.category)&&(!filters.mode||q.questionType===filters.mode));
    return questions.length?[{...game,questions,pointsEarned:questions.reduce((sum,q)=>sum+q.pointsEarned,0),maxPossiblePoints:questions.reduce((sum,q)=>sum+q.maxPossiblePoints,0),presentedQuestions:questions.length}]:[];
  });
}
export const percentage=(n:number,d:number)=>d?100*n/d:null;
export function summarize(games:StatsGame[]){
  const questions=games.flatMap(g=>g.questions),first=questions.filter(q=>q.firstTryCorrect!==null),timed=questions.filter(q=>q.elapsedMs!==null&&q.attemptCount!==0),retries=questions.filter(q=>q.retriesUsed!==null);
  const points=games.reduce((sum,g)=>sum+g.pointsEarned,0),maximum=games.reduce((sum,g)=>sum+g.maxPossiblePoints,0),wins=games.filter(g=>g.won).length;
  const firstAccuracy=percentage(first.filter(q=>q.firstTryCorrect).length,first.length),winRate=percentage(wins,games.length),efficiency=percentage(points,maximum);
  const partial=games.some(g=>!g.questionsComplete),firstPartial=questions.some(q=>q.attemptCount===null);
  const ratingAvailable=games.length>0&&!partial&&!firstPartial&&new Set(games.map(g=>g.scoringVersion)).size===1&&games.every(g=>['retry-v1','retry-v2'].includes(g.scoringVersion))&&firstAccuracy!==null&&efficiency!==null;
  return {points,maximum,games:games.length,wins,questions:questions.length,accuracy:percentage(questions.filter(q=>q.finalCorrect).length,questions.length),
    firstAccuracy,firstCount:first.length,firstPartial,partial,winRate,efficiency,averagePoints:games.length?points/games.length:null,
    answerMs:timed.length?timed.reduce((sum,q)=>sum+q.elapsedMs!,0)/timed.length:null,timedCount:timed.length,
    retries:retries.length?retries.reduce((sum,q)=>sum+q.retriesUsed!,0):null,retriesPartial:retries.length<questions.length,
    rating:ratingAvailable?0.5*efficiency!+0.3*firstAccuracy!+0.2*winRate!:null,
    eligible:ratingAvailable&&games.length>=10&&first.length>=50};
}
export function categories(games:StatsGame[]){
  const grouped=new Map<string,StatsQuestion[]>();
  for(const q of games.flatMap(g=>g.questions)){if(!grouped.has(q.category))grouped.set(q.category,[]);grouped.get(q.category)!.push(q);}
  return [...grouped].sort(([a],[b])=>a.localeCompare(b)).map(([label,qs])=>({label,answered:qs.length,correct:qs.filter(q=>q.finalCorrect).length,value:percentage(qs.filter(q=>q.finalCorrect).length,qs.length)}));
}
export function mostMissed(games:StatsGame[]){
  const grouped=new Map<string,{id:string;label:string;category:string;value:number;answered:number;timeouts:number}>();
  for(const q of games.flatMap(g=>g.questions)){
    const key=q.sourceQuestionId??q.questionId,old=grouped.get(key);
    grouped.set(key,{id:key,label:q.questionLabel??`Question ${key.slice(0,8)}`,category:q.category,value:(old?.value??0)+Number(!q.finalCorrect),answered:(old?.answered??0)+1,timeouts:(old?.timeouts??0)+Number(q.timedOut)});
  }
  return [...grouped.values()].filter(q=>q.value>0).sort((a,b)=>b.value-a.value||a.label.localeCompare(b.label)).slice(0,10);
}
export function bestCategories(games:StatsGame[]){
  const rows=categories(games);
  if(!rows.length)return [];
  const maximum=Math.max(...rows.map(row=>row.value??0));
  return rows.filter(row=>row.value===maximum);
}
export function timeline(games:StatsGame[],grouping:'day'|'week'|'month'){
  const groups=new Map<string,StatsGame[]>();
  for(const game of games){const date=new Date(game.playedAt);if(grouping==='week')date.setUTCDate(date.getUTCDate()-((date.getUTCDay()+6)%7));const key=date.toISOString().slice(0,grouping==='month'?7:10);if(!groups.has(key))groups.set(key,[]);groups.get(key)!.push(game);}
  return [...groups].sort(([a],[b])=>a.localeCompare(b)).map(([label,items])=>({label,...summarize(items)}));
}
export type RankingMode='points'|'accuracy'|'winRate'|'rating';
export interface ComparedPlayer {id:string;label:string;username?:string;exportedAt:string;games:StatsGame[];avatar?:AvatarConfig;membershipId?:string;verified?:boolean;status?:import('./sharing').SharingStatus;canRequest?:boolean;lastSyncedAt?:string|null}
export function rankPlayers(players:ComparedPlayer[],filters:Filters,mode:RankingMode){
  const versions=new Set(players.flatMap(player=>filterGames(player.games,filters).map(game=>game.scoringVersion)));
  const rows=players.map(player=>{const stats=summarize(filterGames(player.games,filters));const value=mode==='points'?stats.points:stats[mode];const eligible=mode==='points'?stats.games>0:mode==='accuracy'?stats.questions>=20&&!stats.partial:mode==='winRate'?stats.games>=5:stats.eligible;return {...player,stats,value,eligible:eligible&&value!==null&&(mode!=='rating'||versions.size===1)};});
  rows.sort((a,b)=>Number(b.eligible)-Number(a.eligible)||(b.value??-1)-(a.value??-1)||b.stats.games-a.stats.games||b.stats.points-a.stats.points||a.label.localeCompare(b.label)||a.id.localeCompare(b.id));
  let rank=0,previous:number|null=null;return rows.map(row=>{if(row.eligible&&row.value!==previous){rank++;previous=row.value;}return {...row,rank:row.eligible?rank:null};});
}
export function awards(players:ComparedPlayer[],filters:Filters){
  const rows=rankPlayers(players,filters,'points');const output:{title:string;players:string[];playerIds:string[];evidence:string}[]=[];
  function best(title:string,items:{id:string;label:string;value:number}[],evidence:string,ascending=false){if(!items.length)return;const value=(ascending?Math.min:Math.max)(...items.map(i=>i.value));output.push({title,players:items.filter(i=>i.value===value).map(i=>i.label),playerIds:items.filter(i=>i.value===value).map(i=>i.id),evidence});}
  best('Points Leader',rows.filter(r=>r.stats.games>0).map(r=>({id:r.id,label:r.label,value:r.stats.points})),'Total points in the selected period');
  best('Most Active Player',rows.filter(r=>r.stats.games>0).map(r=>({id:r.id,label:r.label,value:r.stats.games})),'Completed games');
  best('Most Wins',rows.filter(r=>r.stats.wins>0).map(r=>({id:r.id,label:r.label,value:r.stats.wins})),'Tied first-place finishes count as wins');
  best('Accuracy Master',rows.filter(r=>r.stats.questions>=20&&!r.stats.partial).map(r=>({id:r.id,label:r.label,value:r.stats.accuracy!})),'At least 20 finalized questions');
  best('Fastest Thinker',rows.filter(r=>r.stats.timedCount>=10).map(r=>({id:r.id,label:r.label,value:r.stats.answerMs!})),'Lowest average recorded final answer time; at least 10 timed answers',true);
  best('Overall Champion',rows.filter(r=>r.stats.eligible).map(r=>({id:r.id,label:r.label,value:r.stats.rating!})),'At least 10 games and 50 recorded first attempts');
  const improvement=players.flatMap(p=>{const games=filterGames(p.games,filters);const periods=timeline(games,'month');if(periods.length<2)return [];const first=periods[0],last=periods.at(-1)!;return first.questions>=10&&last.questions>=10&&!first.partial&&!last.partial&&last.accuracy!>first.accuracy!?[{id:p.id,label:p.label,value:last.accuracy!-first.accuracy!}]:[];});
  best('Most Improved',improvement,'Final accuracy gain between first and last month; at least 10 questions in each');
  const allCategories=[...new Set(players.flatMap(p=>filterGames(p.games,filters).flatMap(g=>g.questions.map(q=>q.category))))].sort();
  for(const category of allCategories){const eligible=players.flatMap(p=>{const games=filterGames(p.games,{...filters,category});const s=summarize(games);return s.questions>=20&&!s.partial?[{id:p.id,label:p.label,value:s.accuracy!}]:[];});best(`${category} Specialist`,eligible,'At least 20 finalized questions in this category');}
  return output;
}
