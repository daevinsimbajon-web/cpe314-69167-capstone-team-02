import { test,expect } from '@playwright/test';

test('centered moon, glowing quiz buttons and a looping happy mascot without flying owls',async({page},info)=>{
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await page.setViewportSize({width:1440,height:900});await page.goto('/');
  await expect(page.locator('.sky-owl-flight')).toHaveCount(0);
  await expect(page.locator('.hero-art .hootly-character')).toBeVisible();
  await expect(page.locator('.hero .actions [data-motif="wand"]')).toBeVisible();
  await expect(page.locator('.hero .actions [data-motif="join"]')).toBeVisible();
  // Sample after the normal finite entrance has ended to prove the welcome repeats.
  await page.waitForTimeout(1800);
  await expect(page.locator('.hero-art .hootly-mascot')).toHaveAttribute('data-looping','true');
  const samples=await page.evaluate(async()=>{
    const selectors=['.sky-cloud-one','.sky-cloud-moon','.hero .actions [data-motif="wand"] .night-icon-art','.hero .actions [data-motif="join"] .night-icon-orbit','.hero-art [data-part="character-body-motion"]','.hero-art [data-part="right-wing"]'];
    const frames:string[][]=[];
    for(let i=0;i<32;i++){frames.push(selectors.map(selector=>getComputedStyle(document.querySelector(selector)!).transform));await new Promise(resolve=>setTimeout(resolve,125));}
    return selectors.map((selector,index)=>({selector,distinct:new Set(frames.map(frame=>frame[index])).size}));
  });
  for(const sample of samples)expect(sample.distinct,`${sample.selector} visibly moves`).toBeGreaterThan(3);
  expect(await page.locator('.sky-cloud-moon').evaluate(cloud=>{
    const a=cloud.getBoundingClientRect(),b=document.querySelector('.night-crescent circle:nth-of-type(2)')!.getBoundingClientRect();
    return a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top;
  })).toBe(true);
  await page.screenshot({path:info.outputPath('centered-moon-desktop.png'),fullPage:true});
  for(const width of [320,360,390,430,768,1024,1440]){
    await page.setViewportSize({width,height:844});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth),`Overflow at ${width}px`).toBeLessThanOrEqual(1);
    expect(await page.locator('.night-crescent circle:nth-of-type(2)').evaluate(el=>{const box=el.getBoundingClientRect();return Math.abs(box.left+box.width/2-document.documentElement.clientWidth/2);}),`Moon centered at ${width}px`).toBeLessThan(2);
  }
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:info.outputPath('centered-moon-mobile.png'),fullPage:true});
  await page.emulateMedia({reducedMotion:'reduce'});await expect(page.locator('.landing')).toHaveAttribute('data-theme-paused','true');
  await expect(page.locator('.sky-cloud-moon')).toHaveCSS('animation-name','none');await expect(page.locator('.hero-notes .night-icon-art').first()).toHaveCSS('animation-name','none');
  await expect(page.locator('.hero-art .hootly-mascot')).not.toHaveAttribute('data-looping');
  await expect(page.locator('.hero-art [data-part="character-body-motion"]')).toHaveAttribute('data-movement','still');
  await expect(page.locator('footer,[role=contentinfo]')).toHaveCount(0);expect(errors).toEqual([]);
});
