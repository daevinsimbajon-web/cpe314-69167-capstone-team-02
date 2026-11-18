import { afterEach, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { AnswerCard } from '../components/game/AnswerCard';
import { CountdownTimer } from '../components/game/CountdownTimer';
import { Celebration } from '../components/game/Celebration';
import { MotionProvider } from '../components/ui/MotionProvider';
import { Tabs } from '../components/ui/Tabs';

afterEach(()=>{delete document.documentElement.dataset.reducedMotion;});
it('locks a selected answer without disclosing correctness before reveal',()=>{
  render(<AnswerCard choice="Mercury" index={0} selected correct revealed={false} disabled onClick={()=>{throw new Error('Locked answer submitted');}}/>);
  const button=screen.getByRole('button');expect(button).toBeDisabled();expect(button).toHaveAttribute('aria-pressed','true');
  expect(screen.getByLabelText('Answer locked')).toBeInTheDocument();expect(screen.queryByLabelText('Correct answer')).not.toBeInTheDocument();expect(button).not.toHaveClass('correct');
});
it('provides explicit correct and incorrect labels at reveal',()=>{
  const {rerender}=render(<AnswerCard choice="Mercury" index={0} selected correct revealed disabled onClick={()=>{}}/>);
  expect(screen.getByLabelText('Correct answer')).toBeInTheDocument();
  rerender(<AnswerCard choice="Mercury" index={0} selected correct={false} revealed disabled onClick={()=>{}}/>);
  expect(screen.getByLabelText('Incorrect answer')).toBeInTheDocument();
});
it('displays server-derived seconds rounded up and never below zero',()=>{
  const {rerender}=render(<CountdownTimer remaining={4.1} total={20}/>);expect(screen.getByLabelText('Time remaining: 5 seconds')).toBeInTheDocument();
  rerender(<CountdownTimer remaining={-.3} total={20}/>);expect(screen.getByLabelText('Time remaining: 0 seconds')).toBeInTheDocument();
});
it('removes celebratory motion immediately when the device preference changes',async()=>{
  const {container}=render(<MotionProvider><Celebration/></MotionProvider>);expect(container.querySelector('.celebration')).toBeInTheDocument();
  document.documentElement.dataset.reducedMotion='true';await waitFor(()=>expect(container.querySelector('.celebration')).not.toBeInTheDocument());
});
it('lets keyboard users switch question tools with arrow keys and Home',async()=>{
  function Tools(){const [value,setValue]=useState('import');return <Tabs value={value} onChange={setValue} items={[{value:'import',label:'Import'},{value:'fetch',label:'Discover'}]}/>;}
  render(<Tools/>);const user=userEvent.setup();await user.tab();await user.keyboard('{ArrowRight}');expect(screen.getByRole('tab',{name:'Discover'})).toHaveFocus();expect(screen.getByRole('tab',{name:'Discover'})).toHaveAttribute('aria-selected','true');await user.keyboard('{Home}');expect(screen.getByRole('tab',{name:'Import'})).toHaveFocus();
});
