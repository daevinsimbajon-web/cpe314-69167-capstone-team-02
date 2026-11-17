// Browser-only fixtures. All account/room HTTP and room WebSockets are intercepted.
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {chromium} from '../frontend/node_modules/playwright/index.mjs';
const origin=process.env.HOOTLY_PUBLIC_BASE_URL??'http://localhost:5173';
const output=new URL('../backend/target/rank-clipboard-review/',import.meta.url);
await mkdir(output,{recursive:true});
const rows=[0,3,6,7].map((species,index)=>({id:`review-${index+1}`,displayName:['Winner','Runner up','Third place','Relaxed player'][index],rank:index+1,points:40-index*10,correctCount:4-index,avatar:{version:1,species,body:index+2,eyes:0,iris:index,wings:0,marking:index,accessory:index,expression:0}}));
const user={...rows[0],username:'review_user',email:'review@example.test',emailVerified:true,statistics:{gamesPlayed:1,totalWins:1,totalPoints:40}};
let status='FINISHED';
const browser=await chromium.launch({channel:'msedge',headless:true});
try {
 const context=await browser.newContext();const page=await context.newPage();const errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 await page.route('**/api/**',route=>{
  assert.equal(route.request().method(),'GET','Review must never mutate account or game data');
  const path=new URL(route.request().url()).pathname;
  const room={code:'TLFSKBMH',title:'Rank and clipboard review',status,hostId:user.id,invitationUrl:origin+'/join/TLFSKBMH',maxPlayers:8,questionCount:4,secondsPerQuestion:15,deadline:null,serverTime:new Date().toISOString(),participants:rows.map(p=>({...p,role:p.id===user.id?'HOST':'PLAYER',connected:true,left:false})),leaderboard:rows,question:null,submittedIndex:null};
  return route.fulfill({json:path==='/api/me'?user:path==='/api/rooms/TLFSKBMH'?room:[]});
 });
 await page.routeWebSocket('**/ws/rooms/**',()=>{});
 for(const width of [1440,390]) {
  await page.setViewportSize({width,height:1000});
  await page.goto(origin+'/rooms/TLFSKBMH/leaderboard',{waitUntil:'networkidle'});
  await page.locator('.podium-rank-1').waitFor();
  const birds=await page.locator('[data-final-rank]').evaluateAll(nodes=>nodes.map(n=>({rank:n.dataset.finalRank,expression:n.dataset.expression,species:n.dataset.species,animation:getComputedStyle(n.querySelector('.player-celebration')).animationName})));
  assert.deepEqual(birds.map(b=>b.expression),['celebrating','disappointed','happy','calm']);
  assert.equal(new Set(birds.map(b=>b.species)).size,4);
  assert.equal(birds[0].animation,'player-winner-hop');assert.equal(birds[1].animation,'player-breathe');assert.equal(birds[2].animation,'podium-bird-hop');
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.waitForFunction(()=>!document.querySelector('[data-celebrating=true]'));
  const gaps=await page.locator('.podium-place').evaluateAll(nodes=>nodes.map(node=>node.querySelector('[data-part="player-feet"]').getBoundingClientRect().bottom-node.querySelector('.podium-plinth').getBoundingClientRect().top));
  assert(gaps.every(gap=>Math.abs(gap)<7),'Feet must land on the platform');
  await page.screenshot({path:new URL(`final-${width}.png`,output).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
  await page.emulateMedia({reducedMotion:'no-preference'});
  console.log(JSON.stringify({width,birds,landingGaps:gaps,overflow:false}));
 }
 // Secure Clipboard API path, without writing to the machine's clipboard.
 await page.evaluate(()=>{Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async value=>{window.__copied=value;}}});});
 await page.getByRole('button',{name:'Copy room code'}).click();
 await page.getByText('Copied!',{exact:true}).waitFor();assert.equal(await page.evaluate(()=>window.__copied),'TLFSKBMH');
 await page.getByText('Copied!',{exact:true}).waitFor({state:'hidden'});
 // The local HTTP/denied-permission path uses the same code and restores focus.
 await page.evaluate(()=>{Object.defineProperty(navigator,'clipboard',{configurable:true,value:undefined});document.execCommand=()=>{window.__copied=document.activeElement.value;return true;};});
 await page.getByRole('button',{name:'Copy room code'}).click();await page.getByText('Copied!',{exact:true}).waitFor();assert.equal(await page.evaluate(()=>window.__copied),'TLFSKBMH');
 status='QUESTION';await page.reload({waitUntil:'networkidle'});
 assert.equal(await page.locator('[data-final-rank]').count(),0);assert.equal(await page.locator('[data-celebrating=true]').count(),0);
 assert((await page.locator('.player-owl-podium').evaluateAll(nodes=>nodes.map(n=>n.dataset.expression))).every(value=>value==='calm'));
 status='LOBBY';await page.goto(origin+'/rooms/TLFSKBMH',{waitUntil:'networkidle'});assert.equal(await page.getByRole('button',{name:'Copy room code'}).count(),2);
 assert.deepEqual(errors,[]);console.log('PASS final ranks, distinct identities, mobile layout, reduced motion, landing alignment, clipboard, live neutrality and lobby copy controls; no live API mutations.');
} finally {await browser.close();}
