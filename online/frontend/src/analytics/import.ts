import type { ComparedPlayer, StatsExport, StatsGame, StatsQuestion } from './model';
import { readAvatar } from '../components/avatar/avatarConfig';
export const MAX_FILE_BYTES=5*1024*1024,MAX_FILES=20,MAX_PLAYERS=20,MAX_GAMES=5000;
type Obj=Record<string,unknown>;
const object=(value:unknown,name:string):Obj=>{if(!value||typeof value!=='object'||Array.isArray(value))throw Error(`${name} must be an object.`);return value as Obj;};
const text=(value:unknown,name:string,max=200)=>{if(typeof value!=='string'||!value.trim()||value.length>max)throw Error(`${name} must contain 1–${max} characters.`);return value;};
const number=(value:unknown,name:string,max=1000000000)=>{if(typeof value!=='number'||!Number.isSafeInteger(value)||value<0||value>max)throw Error(`${name} must be a nonnegative whole number up to ${max}.`);return value;};
const boolean=(value:unknown,name:string)=>{if(typeof value!=='boolean')throw Error(`${name} must be true or false.`);return value;};
function timestamp(value:unknown,name:string){
  const result=text(value,name,60),match=result.match(/^(\d{4})-(\d{2})-(\d{2})T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$/);
  if(!match||!Number.isFinite(Date.parse(result))||Number(match[2])<1||Number(match[2])>12||Number(match[3])<1||Number(match[3])>new Date(Date.UTC(Number(match[1]),Number(match[2]),0)).getUTCDate())throw Error(`${name} must be a valid ISO timestamp with a time zone.`);
  return new Date(result).toISOString();
}
const optionalNumber=(value:unknown,name:string,max?:number)=>value==null?null:number(value,name,max);
const optionalText=(value:unknown,name:string,max?:number)=>value==null?undefined:text(value,name,max);
// Question ordering is presentation, not identity. Compare validated fields in a stable order.
const gameSignature=(g:StatsGame)=>JSON.stringify([g.gameId,Date.parse(g.playedAt),g.pointsEarned,g.maxPossiblePoints,g.won,g.scoringVersion,g.presentedQuestions,g.questionsComplete,[...g.questions].sort((a,b)=>a.questionId.localeCompare(b.questionId)).map(q=>[q.questionId,q.sourceQuestionId,q.questionLabel,q.category,q.difficulty,q.questionType,q.firstTryCorrect,q.finalCorrect,q.attemptCount,q.retriesUsed,q.elapsedMs,q.pointsEarned,q.maxPossiblePoints,q.timedOut])]);
export function parseStats(source:string):{data:StatsExport;warnings:string[];duplicatesSkipped:number}{
  if(!source.trim())throw Error('This file is empty.');
  let raw:unknown;try{raw=JSON.parse(source);}catch{throw Error('This file is not valid JSON.');}
  const root=object(raw,'Export');if(root.format!=='hootly.analytics')throw Error('Choose a Hootly analytics export.');if(root.schemaVersion!==1)throw Error('Unsupported schema version. This app supports version 1.');
  const player=object(root.player,'Player');if(!Array.isArray(root.games)||root.games.length>MAX_GAMES)throw Error(`Games must be an array with at most ${MAX_GAMES} entries.`);
  const avatar=readAvatar(player.avatar);if(player.avatar!=null&&!avatar)throw Error('This file contains an invalid player avatar.');
  const warnings=new Set<string>();
  const games=root.games.map((value,index):StatsGame=>{
    const game=object(value,`Game ${index+1}`),gameId=text(game.gameId,'Game ID',120);
    const scoringVersion=optionalText(game.scoringVersion,'Scoring version',30)??'unknown';
    if(!Array.isArray(game.questions)||game.questions.length>50)throw Error('Each game must have at most 50 finalized questions.');
    const ids=new Set<string>();
    const questions=game.questions.map((value):StatsQuestion=>{
      const q=object(value,'Question'),questionId=text(q.questionId,'Question ID',120);if(ids.has(questionId))throw Error(`Question ${questionId} is repeated in a game.`);ids.add(questionId);
      const firstTryCorrect=q.firstTryCorrect==null?null:boolean(q.firstTryCorrect,'First-try correctness');
      const result:StatsQuestion={questionId,sourceQuestionId:optionalText(q.sourceQuestionId,'Source question ID',120),questionLabel:optionalText(q.questionLabel,'Question label',10000),category:text(q.category,'Category'),difficulty:optionalText(q.difficulty,'Difficulty',20),questionType:optionalText(q.questionType,'Question type',30),firstTryCorrect,
        finalCorrect:boolean(q.finalCorrect,'Final correctness'),attemptCount:optionalNumber(q.attemptCount,'Attempt count',11),retriesUsed:optionalNumber(q.retriesUsed,'Retries used',10),elapsedMs:optionalNumber(q.elapsedMs,'Elapsed milliseconds',86400000),pointsEarned:number(q.pointsEarned,'Question points',1000000),maxPossiblePoints:number(q.maxPossiblePoints,'Question maximum',1000000),timedOut:boolean(q.timedOut,'Timeout')};
      if(result.maxPossiblePoints===0||result.pointsEarned>result.maxPossiblePoints||!result.finalCorrect&&result.pointsEarned!==0||result.timedOut&&result.finalCorrect)throw Error('A question contains impossible points or correctness.');
      if(scoringVersion==='retry-v2'){
        if(result.maxPossiblePoints!==10||result.retriesUsed===null||result.retriesUsed>3||result.attemptCount===null||result.pointsEarned!==(result.finalCorrect?[10,7,4,2][result.retriesUsed]:0))throw Error('Question points do not match retry-v2 scoring.');
      }else if(['retry-v1','legacy-v1'].includes(scoringVersion)&&result.pointsEarned!==(result.finalCorrect?result.maxPossiblePoints:0))throw Error('Question points do not match historical scoring.');
      if(result.attemptCount!==null){
        if(result.attemptCount===0&&(!result.timedOut||firstTryCorrect!==null))throw Error('A question without attempts must be a timeout with no first-try result.');
        if(result.attemptCount>0&&firstTryCorrect===null)throw Error('Recorded attempts require first-try correctness.');
        if(result.retriesUsed!==null&&result.retriesUsed!==result.attemptCount-(result.timedOut?0:1))throw Error('Retries do not match the recorded attempts.');
        if(firstTryCorrect&&(result.attemptCount!==1||!result.finalCorrect))throw Error('First-try correctness conflicts with the final result.');
      }else warnings.add('Some first-attempt history is unavailable.');
      if(result.retriesUsed===null)warnings.add('Some retry counts are unavailable.');if(result.elapsedMs===null)warnings.add('Some answer times are unavailable.');
      return result;
    });
    const pointsEarned=number(game.pointsEarned,'Game points',50000000),maxPossiblePoints=number(game.maxPossiblePoints,'Game maximum',50000000);
    if(pointsEarned>maxPossiblePoints||questions.reduce((sum,q)=>sum+q.pointsEarned,0)!==pointsEarned||questions.reduce((sum,q)=>sum+q.maxPossiblePoints,0)>maxPossiblePoints)throw Error('Game totals do not match its question records.');
    if(questions.reduce((sum,q)=>sum+(q.retriesUsed??0),0)>(scoringVersion==='retry-v2'?3:10))throw Error('Retry usage exceeds this game ruleset.');
    const presentedQuestions=game.presentedQuestions==null?questions.length:number(game.presentedQuestions,'Presented questions',50);
    const questionsComplete=game.questionsComplete==null?false:boolean(game.questionsComplete,'Question coverage');
    if(presentedQuestions<questions.length||questionsComplete&&(presentedQuestions!==questions.length||questions.reduce((sum,q)=>sum+q.maxPossiblePoints,0)!==maxPossiblePoints))throw Error('Question coverage conflicts with game totals.');
    if(!questionsComplete)warnings.add('Some games have partial question history.');
    if(!['retry-v1','retry-v2'].includes(scoringVersion))warnings.add('Legacy or unknown scoring rules: overall rating is unavailable.');
    return {gameId,playedAt:timestamp(game.playedAt,'Game date'),pointsEarned,maxPossiblePoints,won:boolean(game.won,'Win result'),scoringVersion,presentedQuestions,questionsComplete,questions};
  });
  const unique=new Map<string,StatsGame>();let duplicatesSkipped=0;
  for(const game of games){const old=unique.get(game.gameId);if(old){if(gameSignature(old)!==gameSignature(game))throw Error('Conflicting duplicate games inside this file. Export a consistent history before importing.');duplicatesSkipped++;}else unique.set(game.gameId,game);}
  const username=optionalText(player.username,'Username',24);if(username&&!/^[A-Za-z0-9_]{3,24}$/.test(username))throw Error('Username must contain 3–24 letters, numbers or underscores.');
  return {data:{format:'hootly.analytics',schemaVersion:1,exportedAt:timestamp(root.exportedAt,'Export date'),player:{exportPlayerId:text(player.exportPlayerId,'Export player ID',120),displayName:text(player.displayName,'Player name',100),...(username?{username}:{}),...(avatar?{avatar}:{})},games:[...unique.values()]},warnings:[...warnings],duplicatesSkipped};
}
export class ImportConflict extends Error {constructor(public gameIds:string[]){super(`${gameIds.length} overlapping game record(s) conflict. Choose which version to keep.`);}}
export function mergeExport(players:ComparedPlayer[],data:StatsExport,replace=false):{players:ComparedPlayer[];duplicates:number;added:number;newPlayer:boolean}{
  const existing=players.find(p=>p.id===data.player.exportPlayerId);
  if(!existing&&players.length>=MAX_PLAYERS)throw Error(`Compare at most ${MAX_PLAYERS} players at once.`);
  const games=new Map(existing?.games.map(g=>[g.gameId,g])??[]),conflicts:string[]=[];let duplicates=0,added=0;
  for(const game of data.games){const old=games.get(game.gameId);if(old){if(gameSignature(old)!==gameSignature(game)){conflicts.push(game.gameId);if(replace)games.set(game.gameId,game);}else duplicates++;}else{games.set(game.gameId,game);added++;}}
  if(conflicts.length&&!replace)throw new ImportConflict(conflicts);
  if(games.size>MAX_GAMES)throw Error(`A player can have at most ${MAX_GAMES} games loaded.`);
  if(players.reduce((sum,p)=>sum+(p.id===data.player.exportPlayerId?0:p.games.length),games.size)>10000)throw Error('Keep comparisons below 10,000 games in total.');
  const older=!!existing&&Date.parse(existing.exportedAt)>Date.parse(data.exportedAt);
  const player:ComparedPlayer={id:data.player.exportPlayerId,label:existing?.label??data.player.displayName,username:older?existing?.username:data.player.username??existing?.username,exportedAt:older?existing!.exportedAt:data.exportedAt,games:[...games.values()],avatar:older?existing?.avatar:data.player.avatar??existing?.avatar};
  return {players:existing?players.map(p=>p.id===player.id?player:p):[...players,player],duplicates,added,newPlayer:!existing};
}
export function downloadJSON(value:unknown,filename:string){const url=URL.createObjectURL(new Blob([JSON.stringify(value,null,2)],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download=filename;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
