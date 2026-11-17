// Fixture browser review. No requests reach live account, room, or mail APIs.
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {chromium} from '../frontend/node_modules/playwright/index.mjs';
const origin='http://localhost:5173',directory=new URL('../backend/target/comprehensive-ui/',import.meta.url);
await mkdir(directory,{recursive:true});
const players=[0,1,2,3].map((i)=>({id:`player-${i}`,displayName:`Player ${i+1}`,username:`player_${i}`,rank:i+1,points:40-i*10,correctCount:4-i,role:'PLAYER',connected:true,left:false}));
let host=true,phase='LOBBY';
function room(){const finalized=phase==='feedback';return {code:'TLFSKBMH',title:'Our magical quiz night',status:phase==='retry'||finalized?'QUESTION':phase,hostId:'host',invitationUrl:origin+'/join/TLFSKBMH',maxPlayers:8,questionCount:4,secondsPerQuestion:20,deadline:new Date(Date.now()+18000).toISOString(),serverTime:new Date().toISOString(),participants:[{id:'host',displayName:'Host',username:'host',role:'HOST',connected:true,left:false},...players],leaderboard:host?players:[players[0]],question:!host&&['QUESTION','retry','feedback'].includes(phase)?{number:2,text:'Which planet is known as the Red Planet?',choices:['Earth','Mars','Venus','Jupiter'],difficulty:'easy',type:'MULTIPLE_CHOICE',correctAnswer:finalized?'Mars':null}:null,submittedIndex:phase==='retry'?0:finalized?1:null,retryAllowance:2,retriesRemaining:2,questionFinalized:finalized,lastAttempt:phase==='retry'?{submissionId:'fixture-attempt',outcome:'RETRY',retriesRemaining:2,acceptedAt:new Date().toISOString()}:finalized?{submissionId:'fixture-correct',outcome:'CORRECT',retriesRemaining:2,acceptedAt:new Date().toISOString()}:null,awardedPoints:finalized?10:null,independentFlow:true,revealAnswers:true,startsAt:new Date(Date.now()+(phase==='COUNTDOWN'?5000:-10000)).toISOString(),personalScore:10,playerCompleted:false,playerProgress:host?players.map((p,i)=>({id:p.id,questionNumber:i+1,completed:phase==='FINISHED',points:p.points,retriesRemaining:i%3,answeredQuestions:4,correctAnswers:p.correctCount})):[]};}
const browser=await chromium.launch({channel:'msedge',headless:true});
try {
 const page=await browser.newPage({reducedMotion:'reduce'}),errors=[];page.on('pageerror',error=>errors.push(error.message));
 await page.route('**/api/**',route=>{assert.equal(route.request().method(),'GET','Fixture review must not mutate data');const path=new URL(route.request().url()).pathname;let data=[];
  if(path==='/api/me')data={id:host?'host':'player-0',displayName:'Justine',username:'justine',email:'a-long-private-address-for-layout-checks@example.test',createdAt:'2026-01-01T00:00:00Z',emailVerified:true,statistics:{gamesPlayed:1,totalPoints:10,totalWins:1}};
  else if(path==='/api/rooms/TLFSKBMH')data=room();else if(path==='/api/bank/opentdb/categories')data={categories:[{id:18,name:'Science: Computers'}]};else if(path==='/api/auth/username-availability')data={available:true,valid:true};
  return route.fulfill({json:data});});
 await page.routeWebSocket('**/ws/rooms/**',()=>{});
 const scenarios=[['settings','/settings',true,'LOBBY'],['create','/create',true,'LOBBY'],['lobby','/rooms/TLFSKBMH',true,'LOBBY'],['countdown','/rooms/TLFSKBMH',true,'COUNTDOWN'],['player','/rooms/TLFSKBMH',false,'QUESTION'],['retry','/rooms/TLFSKBMH',false,'retry'],['feedback','/rooms/TLFSKBMH',false,'feedback'],['spectator','/rooms/TLFSKBMH',true,'QUESTION'],['final','/rooms/TLFSKBMH',true,'FINISHED'],['compare','/analytics/compare',true,'LOBBY']];
 for(const width of [320,360,390,430,768,1024,1440]){
  await page.setViewportSize({width,height:1000});
  for(const [name,path,isHost,status] of scenarios){host=isHost;phase=status;await page.goto(origin+path,{waitUntil:'networkidle'});
   if(name==='settings')await page.getByRole('heading',{name:'Password & Security'}).waitFor();
   if(name==='retry'){await page.getByRole('dialog',{name:'Use a chance?'}).waitFor();assert(await page.getByRole('button',{name:'Yes, use a chance'}).isVisible());}
   if(name==='player')assert.equal(await page.getByRole('heading',{name:'Live leaderboard'}).count(),0);
   if(name==='spectator')await page.getByRole('heading',{name:'Live leaderboard'}).waitFor();
   const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);assert(!overflow,`${name} overflows at ${width}`);
   if([320,390,1440].includes(width))await page.screenshot({path:fileURLToPath(new URL(`${name}-${width}.png`,directory)),fullPage:true,animations:'disabled'});
  }
  console.log(`PASS ${width}px: Settings, Create, Lobby, Countdown, Player, Retry, Feedback, Spectator, Final, Compare`);
 }
 assert.deepEqual(errors,[]);console.log('PASS no browser runtime errors; no real account, room, database or email mutations.');
}finally{await browser.close();}
