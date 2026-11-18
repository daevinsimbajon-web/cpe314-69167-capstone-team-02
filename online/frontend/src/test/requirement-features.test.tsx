import {afterEach,expect,it,vi} from 'vitest';
import {act,render,screen,within} from '@testing-library/react';
import {Leaderboard} from '../components/game/Leaderboard';
import {AnimatedNumber} from '../components/ui/AnimatedNumber';
import {MotionProvider} from '../components/ui/MotionProvider';
import {bestCategories,timeline,type StatsGame} from '../analytics/model';
import {categoryPath} from '../quizzes';

const question={questionId:'q',category:'Science',firstTryCorrect:true,finalCorrect:true,attemptCount:1,
  retriesUsed:0,elapsedMs:1000,pointsEarned:10,maxPossiblePoints:10,timedOut:false};
const game:StatsGame={gameId:'g',playedAt:'2026-11-17T10:00:00Z',pointsEarned:10,maxPossiblePoints:10,
  won:true,scoringVersion:'retry-v2',presentedQuestions:1,questionsComplete:true,questions:[question]};
afterEach(()=>{delete document.documentElement.dataset.reducedMotion;vi.restoreAllMocks();});

it('computes average scores from each periods games and preserves tied best categories',()=>{
  const games=[game,{...game,gameId:'g2',pointsEarned:20,questions:[{...question,category:'Space'}]}];
  expect(timeline(games,'day')[0]).toMatchObject({points:30,averagePoints:15,games:2});
  expect(bestCategories(games).map(category=>category.label)).toEqual(['Science','Space']);
  expect(bestCategories([])).toEqual([]);
});

it('uses recursive category paths while terminating a malformed cycle',()=>{
  const root={id:'a',name:'Science'},child={id:'b',name:'Physics',parentId:'a'},leaf={id:'c',name:'Space',parentId:'b'};
  expect(categoryPath(leaf,[root,child,leaf])).toBe('Science / Physics / Space');
  expect(categoryPath({...root,parentId:'b'},[{...root,parentId:'b'},child])).toContain('Science');
});

it('keeps a departed high scorer off the podium while preserving earned points in the results',()=>{
  document.documentElement.dataset.reducedMotion='true';
  render(<MotionProvider><Leaderboard final rows={[
    {id:'winner',displayName:'Active player',points:0,correctCount:0,rank:1},
    {id:'departed',displayName:'Departed player',points:100,correctCount:10,rank:0,forfeited:true},
  ]}/></MotionProvider>);
  const podium=screen.getByLabelText('Leading players');
  expect(within(podium).getByText('Active player')).toBeInTheDocument();
  expect(within(podium).queryByText('Departed player')).not.toBeInTheDocument();
  expect(screen.getByText('Left the game')).toBeInTheDocument();
  expect(screen.getByText('Departed player').closest('li')).toHaveTextContent('100');
});

it('never animates a positive score below zero when a frame timestamp precedes the effect clock',()=>{
  let tick:FrameRequestCallback=()=>{};
  vi.spyOn(globalThis,'requestAnimationFrame').mockImplementation(callback=>{tick=callback;return 1;});
  vi.spyOn(globalThis,'cancelAnimationFrame').mockImplementation(()=>{});
  vi.spyOn(performance,'now').mockReturnValue(1000);
  render(<AnimatedNumber value={100}/>);
  act(()=>tick(900));expect(screen.getByLabelText('100')).toHaveTextContent(/^0$/);
  act(()=>tick(1500));expect(screen.getByLabelText('100')).toHaveTextContent(/^100$/);
});
