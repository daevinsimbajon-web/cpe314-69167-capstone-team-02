import { describe,expect,it } from 'vitest';
import { render,screen,fireEvent } from '@testing-library/react';
import { categories,filterGames,periodFor,summarize,timeline,rankPlayers,awards,type Filters,type StatsExport,type StatsGame } from '../analytics/model';
import { ImportConflict,mergeExport,parseStats } from '../analytics/import';
import { Chart } from '../analytics/Charts';
import { RetryChances,RetryStepper } from '../components/game/RetryChances';

const question={questionId:'q1',sourceQuestionId:'source1',category:'Science',firstTryCorrect:false,finalCorrect:true,attemptCount:2,retriesUsed:1,elapsedMs:800,pointsEarned:5,maxPossiblePoints:5,timedOut:false};
const game:StatsGame={gameId:'g1',playedAt:'2026-01-01T23:59:59.999Z',pointsEarned:5,maxPossiblePoints:10,won:true,scoringVersion:'retry-v1',presentedQuestions:2,questionsComplete:true,questions:[question,{...question,questionId:'q2',sourceQuestionId:'source2',firstTryCorrect:null,finalCorrect:false,attemptCount:0,retriesUsed:0,elapsedMs:5000,pointsEarned:0,timedOut:true}]};
const data:StatsExport={format:'hootly.analytics',schemaVersion:1,exportedAt:'2026-01-02T10:00:00Z',player:{exportPlayerId:'player-one',displayName:'Same Name'},games:[game]};
const filters:Filters={period:periodFor('all'),category:'',mode:'',ruleset:''};
const parse=(value:unknown)=>parseStats(JSON.stringify(value));

it('imports reduced retry-v2 awards without changing historical rules and separates efficiency from accuracy',()=>{
  const current={...game,scoringVersion:'retry-v2',pointsEarned:21,maxPossiblePoints:30,presentedQuestions:3,questions:[0,1,2].map((used,index)=>({...question,questionId:`new-${index}`,firstTryCorrect:used===0,attemptCount:used+1,retriesUsed:used,pointsEarned:[10,7,4][used],maxPossiblePoints:10}))};
  const imported=parse({...data,games:[current]}).data.games[0];expect(summarize([imported])).toMatchObject({points:21,maximum:30,accuracy:100,efficiency:70,retries:3});expect(parse(data).data.games[0]).toEqual(game);
  expect(()=>parse({...data,games:[{...current,questions:[{...current.questions[0],pointsEarned:7}]}]})).toThrow('retry-v2');
  expect(rankPlayers([{id:'new',label:'New',exportedAt:data.exportedAt,games:[current]},{id:'old',label:'Old',exportedAt:data.exportedAt,games:[game]}],filters,'rating').every(row=>!row.eligible)).toBe(true);
});

describe('analytics definitions and rankings',()=>{
  it('counts finalized questions once, distinguishes first try, excludes unattempted timeout timing',()=>{
    const stats=summarize([game]);expect(stats).toMatchObject({points:5,maximum:10,games:1,wins:1,accuracy:50,firstAccuracy:0,firstCount:1,retries:1,answerMs:800,eligible:false,rating:45});
    expect(categories([game])[0]).toMatchObject({value:50,answered:2,correct:1});expect(timeline([game],'week')[0].label).toBe('2025-12-29');
  });
  it('handles empty denominators and historic missing fields without guessed zeros',()=>{
    expect(summarize([])).toMatchObject({accuracy:null,firstAccuracy:null,answerMs:null,rating:null});
    const old={...game,scoringVersion:'legacy-v1',questionsComplete:false,questions:[{...question,attemptCount:null,retriesUsed:null,elapsedMs:null,firstTryCorrect:null}]};
    expect(summarize([old])).toMatchObject({firstAccuracy:null,firstPartial:true,retries:null,answerMs:null,rating:null,partial:true});
  });
  it('uses inclusive UTC game dates rather than export dates and really filters categories',()=>{
    expect(filterGames([game],{...filters,period:{preset:'custom',from:'2026-01-01',to:'2026-01-01'}})).toHaveLength(1);
    expect(filterGames([game],{...filters,period:{preset:'custom',from:'2026-01-02',to:'2026-01-02'}})).toHaveLength(0);
    expect(filterGames([game],{...filters,category:'Unknown'})).toHaveLength(0);expect(periodFor('lastYear',new Date('2026-11-16T00:00:00Z'))).toMatchObject({from:'2025-01-01',to:'2025-12-31'});
  });
  it('enforces minimums, ties deterministically and withholds unsupported awards',()=>{
    const one={id:'a',label:'One',exportedAt:data.exportedAt,games:[game]},two={...one,id:'b',label:'Two'};
    expect(rankPlayers([two,one],filters,'points').map(r=>[r.id,r.rank])).toEqual([['a',1],['b',1]]);
    for(const mode of ['accuracy','winRate','rating'] as const)expect(rankPlayers([one],filters,mode)[0].eligible).toBe(false);
    expect(awards([one],filters).map(a=>a.title)).not.toContain('Accuracy Master');
    const enough={...one,games:Array.from({length:10},(_,i)=>({...game,gameId:`g${i}`,questions:Array.from({length:5},(_,j)=>({...question,questionId:`q${j}`})),presentedQuestions:5,maxPossiblePoints:25,pointsEarned:25}))};
    expect(rankPlayers([enough],filters,'rating')[0].eligible).toBe(true);expect(awards([enough],filters).map(a=>a.title)).toContain('Overall Champion');
  });
});
describe('untrusted multi-export processing',()=>{
  it('accepts timeout records with and without a consumed retry and rejects impossible counts',()=>{
    const timeout=(attemptCount:number,retriesUsed:number)=>({...question,firstTryCorrect:attemptCount?false:null,finalCorrect:false,attemptCount,retriesUsed,pointsEarned:0,timedOut:true});
    for(const [attempts,retries] of [[0,0],[1,0],[1,1],[2,1]]){
      const g={...game,pointsEarned:0,maxPossiblePoints:5,presentedQuestions:1,questions:[timeout(attempts,retries)]};
      expect(()=>parse({...data,games:[g]})).not.toThrow();
    }
    const invalid={...game,pointsEarned:0,maxPossiblePoints:5,presentedQuestions:1,questions:[timeout(2,0)]};
    expect(()=>parse({...data,games:[invalid]})).toThrow('Retries');
  });
  it('accepts real versioned exports and same-name distinct identities; skips overlapping games',()=>{
    const one=parse(data).data;const first=mergeExport([],one);const again=mergeExport(first.players,parse({...data,games:[game,game]}).data);
    expect(again.duplicates).toBe(1);expect(parse({...data,games:[game,game]}).duplicatesSkipped).toBe(1);
    expect(mergeExport(first.players,{...one,player:{...one.player,exportPlayerId:'player-two'}}).players).toHaveLength(2);
  });
  it('requires explicit conflict resolution and never mutates the accepted history',()=>{
    const original=mergeExport([],parse(data).data).players,conflict={...data,games:[{...game,won:false}]};
    expect(()=>mergeExport(original,parse(conflict).data)).toThrow(ImportConflict);expect(original[0].games[0].won).toBe(true);
    expect(mergeExport(original,parse(conflict).data,true).players[0].games[0].won).toBe(false);
  });
  it.each(['','{','[]','null'])('rejects malformed or empty input %s',source=>expect(()=>parseStats(source)).toThrow());
  it('rejects unsupported versions, invalid dates, impossible totals and attempt history',()=>{
    expect(()=>parse({...data,schemaVersion:2})).toThrow('version');
    expect(()=>parse({...data,games:[{...game,playedAt:'2026-02-30T00:00:00Z'}]})).toThrow('timestamp');
    expect(()=>parse({...data,games:[{...game,pointsEarned:999}]})).toThrow('totals');
    expect(()=>parse({...data,games:[{...game,questions:[{...question,retriesUsed:0}]}]})).toThrow('Retries');
  });
});
it('renders readable chart axes, keyboard/touch details and retry controls',()=>{
  render(<><Chart title="Actual data" axis="Accuracy (%)" labels={['Science']} max={100} series={[{label:'First try',color:'#fff',values:[50]}]}/><RetryChances remaining={2} total={3}/><RetryStepper value={3} onChange={()=>{}}/></>);
  expect(screen.getByRole('status',{name:'2 of 3 retry chances remaining'})).toBeInTheDocument();expect(screen.getByRole('radio',{name:'3'})).toBeChecked();
  fireEvent.click(screen.getByRole('button',{name:/Science/}));expect(screen.getByText('First try: 50 Accuracy (%)')).toBeInTheDocument();
  expect(screen.getByText('View data table and exact values')).toBeInTheDocument();
});
