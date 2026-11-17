import { afterEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AnalyticsPage } from '../analytics/AnalyticsOverview';
import { mostMissed, type StatsExport, type StatsGame } from '../analytics/model';
import { downloadJSON } from '../analytics/import';

vi.mock('../analytics/import',async original=>({...await original<typeof import('../analytics/import')>(),downloadJSON:vi.fn()}));
afterEach(()=>{vi.unstubAllGlobals();vi.clearAllMocks();});
const playedAt=new Date().toISOString();
const question={questionId:'snapshot-a',sourceQuestionId:'source-a',questionLabel:'Which world has rings?',category:'Space',questionType:'MULTIPLE_CHOICE',firstTryCorrect:false,finalCorrect:true,attemptCount:2,retriesUsed:1,elapsedMs:800,pointsEarned:7,maxPossiblePoints:10,timedOut:false};
const game:StatsGame={gameId:'g1',playedAt,pointsEarned:7,maxPossiblePoints:20,won:true,scoringVersion:'retry-v2',presentedQuestions:2,questionsComplete:true,questions:[question,{...question,questionId:'snapshot-b',sourceQuestionId:'source-b',questionLabel:'The Moon is a star.',questionType:'TRUE_FALSE',category:'Science',firstTryCorrect:null,finalCorrect:false,attemptCount:0,retriesUsed:0,elapsedMs:null,pointsEarned:0,timedOut:true}]};
function mount(games:StatsGame[]=[game]){
  const data:StatsExport={format:'hootly.analytics',schemaVersion:1,exportedAt:playedAt,player:{displayName:'Player',exportPlayerId:'private-player'},games};
  const fetch=vi.fn(async(_input?:unknown)=>Response.json(data));vi.stubGlobal('fetch',fetch);
  const view=render(<MemoryRouter><AnalyticsPage/></MemoryRouter>);return {...view,fetch,data};
}
const summary=(label:string)=>screen.getByRole('article',{name:new RegExp(`^${label}:`)});
const openInsights=()=>fireEvent.click(screen.getByText('Detailed Insights',{exact:true}));

it('shows only four primary statistics, two charts and three recent games, with deep analysis collapsed',async()=>{
  const {container}=mount(Array.from({length:5},(_,i)=>({...game,gameId:`g${i}`})));
  await screen.findByRole('region',{name:'Overview statistics'});
  expect(container.querySelectorAll('.metric-card')).toHaveLength(4);
  expect(summary('Games Played')).toHaveTextContent('5');expect(summary('Total Points')).toHaveTextContent('35');expect(summary('Wins')).toHaveTextContent('5');expect(summary('Accuracy')).toHaveTextContent('50%');
  expect(screen.getByRole('heading',{name:'Accuracy by Category'})).toBeVisible();expect(screen.getByRole('heading',{name:'Points Over Time'})).toBeVisible();
  expect(screen.getByRole('heading',{name:'Accuracy Trend',hidden:true})).not.toBeVisible();expect(screen.queryByLabelText('Category')).not.toBeInTheDocument();
  expect(within(screen.getByRole('region',{name:'Recent Games'})).getAllByRole('listitem')).toHaveLength(3);expect(screen.getByRole('link',{name:'View All Games'})).toHaveAttribute('href','/history');expect(screen.queryByText('Game results')).not.toBeInTheDocument();
  openInsights();expect(screen.getByText('Points efficiency')).toBeVisible();expect(screen.getByText('35 earned / 100 available points, using original game maximums.')).toBeVisible();
});
it('filters all metrics and requests a server-filtered export without rewriting signed data',async()=>{
  const {fetch,data}=mount();await screen.findByRole('region',{name:'Overview statistics'});fireEvent.click(screen.getByRole('button',{name:'Filters'}));fireEvent.change(screen.getByLabelText('Category'),{target:{value:'Science'}});
  expect(summary('Total Points')).toHaveTextContent('0');expect(summary('Accuracy')).toHaveTextContent('0%');expect(summary('Wins')).toHaveTextContent('1');expect(screen.getByLabelText('1 active filters')).toBeInTheDocument();
  expect(screen.getByRole('button',{name:/Science \(0\/1\)/})).toBeVisible();expect(screen.queryByRole('button',{name:/Space \(1\/1\)/})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Export My Stats'}));await waitFor(()=>expect(downloadJSON).toHaveBeenCalled());expect(vi.mocked(downloadJSON).mock.calls[0][0]).toEqual(data);expect(fetch.mock.calls.some(call=>String(call[0]).includes('category=Science'))).toBe(true);
  fireEvent.click(screen.getByRole('button',{name:'Reset filters'}));expect(summary('Total Points')).toHaveTextContent('7');expect(screen.queryByLabelText('1 active filters')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Question type'),{target:{value:'MULTIPLE_CHOICE'}});expect(summary('Accuracy')).toHaveTextContent('100%');
});
it('shows markers instead of a fabricated line, aggregates grouping and makes exact values inspectable',async()=>{
  const {container}=mount();await screen.findByRole('region',{name:'Overview statistics'});
  const points=screen.getByRole('region',{name:'Points Over Time'});expect(points.querySelectorAll('.line-chart circle')).toHaveLength(1);
  expect(points.querySelectorAll('polyline')).toHaveLength(0);expect(within(points).getByText(/Play on more days/)).toBeVisible();
  await userEvent.selectOptions(screen.getByLabelText('Inspect date for Points Over Time'),'0');expect(within(points).getByRole('status')).toHaveTextContent('Points: 7 Points earned');
  fireEvent.change(screen.getByLabelText('Time grouping'),{target:{value:'month'}});expect(within(points).getByRole('option',{name:playedAt.slice(0,7)})).toBeInTheDocument();expect(container.querySelectorAll('.analytics-primary-charts .chart-card')).toHaveLength(2);
});
it('explains unavailable history and excludes a nonexistent first-try series without treating it as zero',async()=>{
  mount([{...game,scoringVersion:'legacy-v1',questionsComplete:false,questions:[{...question,firstTryCorrect:null,attemptCount:null,retriesUsed:null,elapsedMs:null,pointsEarned:10}],pointsEarned:10,maxPossiblePoints:10}]);await screen.findByRole('region',{name:'Overview statistics'});openInsights();
  expect(screen.getByText(/Not tracked in older games/)).toBeVisible();expect(screen.getByText(/Timing data wasn’t recorded/)).toBeVisible();expect(screen.getByText(/Retry data isn’t available/)).toBeVisible();
  fireEvent.click(screen.getByText('Accuracy Trend',{selector:'summary'}));const trend=screen.getByRole('region',{name:'Accuracy Trend'});expect(within(trend).getByText(/Only final-answer accuracy is shown/)).toBeVisible();expect(within(trend).queryByText('1. First try')).not.toBeInTheDocument();expect(trend.querySelectorAll('.line-chart circle')).toHaveLength(1);
});
it('distinguishes an empty report from zero retry usage and preserves no-missed states',async()=>{
  const {unmount,container}=mount([]);await screen.findByRole('region',{name:'Overview statistics'});expect(summary('Accuracy')).toHaveTextContent('No recorded answers');expect(within(container.querySelector('.analytics-primary-charts') as HTMLElement).getAllByText('No data in the selected period.')).toHaveLength(2);expect(screen.getByText(/Play a quiz to start tracking your progress/)).toBeVisible();unmount();
  mount([{...game,questions:[{...question,firstTryCorrect:true,attemptCount:1,retriesUsed:0,pointsEarned:10}],pointsEarned:10,maxPossiblePoints:10,presentedQuestions:1}]);await screen.findByRole('region',{name:'Overview statistics'});openInsights();expect(screen.getByText('Retries used').parentElement).toHaveTextContent('0');fireEvent.click(screen.getByText('Most Missed Questions',{selector:'summary'}));expect(screen.getByText('No missed questions in this period.')).toBeVisible();
});
it('counts missed question frequency across snapshots without losing correct denominators or category',()=>{
  const miss={...question,finalCorrect:false,pointsEarned:0};expect(mostMissed([{...game,questions:[miss]},{...game,gameId:'g2',questions:[{...miss,questionId:'another-snapshot'}]},{...game,gameId:'g3',questions:[question]}])).toEqual([{id:'source-a',label:question.questionLabel,category:'Space',value:2,answered:3,timeouts:0}]);
});
it('keeps historical maximums and points, filters scoring versions and blocks invalid date requests',async()=>{
  const legacy={...game,gameId:'old',scoringVersion:'legacy-v1',pointsEarned:5,maxPossiblePoints:5,presentedQuestions:1,questions:[{...question,pointsEarned:5,maxPossiblePoints:5,attemptCount:null,retriesUsed:null,firstTryCorrect:null,elapsedMs:null}]};
  const {fetch}=mount([game,legacy]);await screen.findByRole('region',{name:'Overview statistics'});expect(summary('Total Points')).toHaveTextContent('12');openInsights();expect(screen.getByText('12 earned / 25 available points, using original game maximums.')).toBeVisible();expect(screen.getByText(/Different scoring versions are included/)).toBeVisible();
  fireEvent.click(screen.getByRole('button',{name:'Filters'}));fireEvent.change(screen.getByLabelText('Scoring rules'),{target:{value:'legacy-v1'}});expect(summary('Total Points')).toHaveTextContent('5');expect(screen.getByText('5 earned / 5 available points, using original game maximums.')).toBeVisible();
  fireEvent.change(screen.getByLabelText('Date range'),{target:{value:'custom'}});const calls=fetch.mock.calls.length;fireEvent.change(screen.getByLabelText('From (UTC)'),{target:{value:'2099-12-31'}});expect(await screen.findByRole('alert')).toHaveTextContent('Choose an end date');expect(fetch).toHaveBeenCalledTimes(calls);expect(screen.getByRole('button',{name:'Export My Stats'})).toBeDisabled();
});
