import { act, render } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { characterMovement, HootlyCharacter } from '../components/brand/HootlyCharacter';
import { MotionProvider } from '../components/ui/MotionProvider';

afterEach(()=>{vi.useRealTimers();vi.restoreAllMocks();delete document.documentElement.dataset.reducedMotion;});
it('draws the previous owl in separate controllable layers with welcoming heart eyes',()=>{
  const {container,rerender}=render(<HootlyCharacter expression="welcoming" scene="login"/>);
  expect(container.firstChild).toHaveAttribute('data-heart-eyes','true');
  for(const part of ['head','ears','left-eye','right-eye','left-pupil','right-pupil','left-eyelid','right-eyelid','beak','mouth','left-wing','right-wing','body','feet'])expect(container.querySelector(`[data-part="${part}"]`)).not.toBeNull();
  expect(container.querySelector('img')).toBeNull();
  rerender(<HootlyCharacter expression="sad" scene="gameplay"/>);
  expect(container.firstChild).not.toHaveAttribute('data-heart-eyes');
  expect(container.firstChild).toHaveAttribute('data-expression','sad');
});
it('covers and uncovers eyes with a distinct wing pose, including reduced motion',()=>{
  document.documentElement.dataset.reducedMotion='true';
  const {container,rerender}=render(<MotionProvider><HootlyCharacter scene="login" privateInput/></MotionProvider>);
  expect(container.firstChild).toHaveAttribute('data-covered','true');
  expect(container.firstChild).toHaveAttribute('data-blinking','true');
  expect(container.firstChild).toHaveAttribute('data-quiet','true');
  expect(container.querySelector('[data-part="left-wing"]')).toHaveAttribute('style',expect.stringContaining('translateX(28px)'));
  rerender(<MotionProvider><HootlyCharacter scene="login" privateInput={false}/></MotionProvider>);
  expect(container.firstChild).not.toHaveAttribute('data-covered');
});
it('blinks on a natural interval and never idles the focused gameplay eyes',()=>{
  vi.useFakeTimers();vi.spyOn(Math,'random').mockReturnValue(0);
  const {container,rerender}=render(<HootlyCharacter scene="landing"/>);
  act(()=>{vi.advanceTimersByTime(3300);});expect(container.firstChild).toHaveAttribute('data-blinking','true');
  act(()=>{vi.advanceTimersByTime(135);});expect(container.firstChild).not.toHaveAttribute('data-blinking');
  rerender(<HootlyCharacter scene="gameplay" expression="focused"/>);
  act(()=>{vi.advanceTimersByTime(10000);});expect(container.firstChild).not.toHaveAttribute('data-blinking');
});
it('uses distinct clip-path IDs when several expressions appear together',()=>{
  const {container}=render(<><HootlyCharacter expression="welcoming"/><HootlyCharacter expression="celebrating"/><HootlyCharacter expression="sad"/></>);
  const ids=Array.from(container.querySelectorAll('clipPath')).map(node=>node.id);
  expect(new Set(ids).size).toBe(ids.length);
});
it('lets happy gameplay reactions hop while focused gameplay remains still',()=>{
  const {container,rerender}=render(<HootlyCharacter scene="gameplay" expression="happy"/>);
  const body=container.querySelector('[data-part="character-body-motion"]');
  expect(body).toHaveAttribute('data-movement','happy-hop');
  expect(characterMovement('happy',false).body.y).toContain(-18);
  expect(body).toContainElement(container.querySelector('[data-part="feet"]'));
  expect(body).not.toContainElement(container.querySelector('[data-part="ground-shadow"]'));
  rerender(<HootlyCharacter scene="gameplay" expression="focused"/>);
  expect(container.querySelector('[data-part="character-body-motion"]')).toHaveAttribute('data-movement','still');
  expect(characterMovement('focused',false).body).toEqual({y:0,rotate:0,scaleX:1,scaleY:1});
});
it('uses different finite body reactions and removes them when motion is reduced',()=>{
  expect(characterMovement('welcoming',false).pose).toBe('welcome-hop');
  expect(characterMovement('sad',false).pose).toBe('gentle-settle');
  const victory=characterMovement('celebrating',false);
  expect(victory.body.y).toContain(-24);
  expect(victory.body.y).toContain(-17);
  expect(characterMovement('celebrating',true)).toMatchObject({pose:'still',duration:0,body:{y:0},shadow:{scaleX:1,opacity:.1}});
});
