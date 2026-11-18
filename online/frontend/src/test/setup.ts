import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup,configure } from '@testing-library/react';
// Lazy screen modules and deliberate success transitions can exceed a second on Windows CI.
configure({asyncUtilTimeout:5000});
Object.defineProperty(window,'matchMedia',{writable:true,value:(query:string)=>({matches:false,media:query,onchange:null,addListener:()=>{},removeListener:()=>{},addEventListener:()=>{},removeEventListener:()=>{},dispatchEvent:()=>true})});
HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};
HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};
afterEach(cleanup);
