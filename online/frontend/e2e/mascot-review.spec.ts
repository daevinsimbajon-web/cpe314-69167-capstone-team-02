import { test, expect } from '@playwright/test';

test('the owl review renders distinct expressions and independently moves its wings and pupils',async({page},info)=>{
  await page.route('**/api/me',route=>route.fulfill({status:401,contentType:'application/json',body:'{"message":"Preview"}'}));
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/mascot-review');await expect(page.getByRole('heading',{name:/One familiar owl/})).toBeVisible();
  await expect(page.locator('.mascot-review-card svg')).toHaveCount(8);
  await expect(page.locator('[data-expression="sad"]')).toBeVisible();await expect(page.locator('[data-expression="celebrating"]')).toBeVisible();
  await page.getByRole('button',{name:'Replay movement'}).click();
  const movement=await page.evaluate(async()=>{
    const winner=document.querySelector('[data-expression="celebrating"]')!;
    const body=winner.querySelector('[data-part="character-body-motion"]')!,shadow=winner.querySelector('[data-part="ground-shadow"]')!,feet=winner.querySelector('[data-part="feet"]')!;
    const focused=document.querySelector('[data-expression="focused"] [data-part="character-body-motion"]')!;
    const samples:{body:string;shadow:string;feet:string;focused:string}[]=[];
    for(let index=0;index<24;index++){samples.push({body:getComputedStyle(body).transform,shadow:getComputedStyle(shadow).transform,feet:getComputedStyle(feet).transform,focused:getComputedStyle(focused).transform});await new Promise(resolve=>setTimeout(resolve,50));}
    return samples;
  });
  expect(new Set(movement.map(sample=>sample.body)).size).toBeGreaterThan(3);
  expect(new Set(movement.map(sample=>sample.shadow)).size).toBeGreaterThan(3);
  expect(new Set(movement.map(sample=>sample.feet)).size).toBeGreaterThan(3);
  expect(new Set(movement.map(sample=>sample.focused)).size).toBe(1);
  const interactive=page.locator('.mascot-review-login .hootly-character');
  await page.getByRole('checkbox',{name:'Cover the eyes with wings'}).check();await expect(interactive).toHaveAttribute('data-covered','true');
  await expect.poll(()=>interactive.locator('[data-part="left-wing"]').getAttribute('style')).toContain('translateX(28px)');
  await page.getByRole('checkbox',{name:'Cover the eyes with wings'}).uncheck();await expect(interactive).not.toHaveAttribute('data-covered');
  await page.getByRole('slider',{name:'Input attention'}).focus();await page.getByRole('slider',{name:'Input attention'}).press('End');
  await expect.poll(()=>interactive.locator('[data-part="left-pupil"]').getAttribute('style')).toContain('translateX(6px)');
  for(const width of [360,390,768,1024,1440]){await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);}
  await page.screenshot({path:info.outputPath('owl-expressions.png'),fullPage:true});
  await page.getByRole('checkbox',{name:'Loop movement'}).check();
  await page.emulateMedia({reducedMotion:'reduce'});await expect(interactive).toHaveAttribute('data-quiet','true');
  await expect(page.locator('[data-expression="celebrating"] [data-part="character-body-motion"]')).toHaveAttribute('data-movement','still');
  expect(errors).toEqual([]);
});
