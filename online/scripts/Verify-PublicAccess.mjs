// Read-only connectivity check: never registers accounts, sends mail, or changes data.
import assert from 'node:assert/strict';
import {chromium} from '../frontend/node_modules/playwright/index.mjs';
const base=new URL(process.env.HOOTLY_PUBLIC_BASE_URL??'https://nail-secretary-presidential-places.trycloudflare.com');
assert(!base.username&&!base.password,'Do not put credentials in the URL.');
assert(base.protocol==='https:'||(['localhost','127.0.0.1'].includes(base.hostname)&&base.protocol==='http:'),'Use HTTPS or localhost.');
const browser=await chromium.launch({channel:'msedge',headless:true});
try {
 const page=await browser.newPage();const errors=[];let hmrConnected=false;
 page.on('pageerror',error=>errors.push(error.message));
 page.on('websocket',socket=>{if(new URL(socket.url()).pathname==='/')socket.on('framereceived',()=>{hmrConnected=true;});});
 const response=await page.goto(base.origin,{waitUntil:'networkidle',timeout:45000});
 assert.equal(response.status(),200);assert.match(await page.title(),/Hootly/);
 const health=await page.request.get(base.origin+'/api/health');assert.equal(health.status(),200);assert.equal((await health.json()).status,'ok');
 await page.getByRole('link',{name:/sign in/i}).first().click();await page.locator('input[type="password"]').waitFor();
 assert.deepEqual(errors,[]);console.log(JSON.stringify({origin:base.origin,homepage:true,apiHealth:true,signInPage:true,hmrWebSocket:hmrConnected,pageErrors:errors,emailsSent:0,accountMutations:0}));
} finally {await browser.close();}
