import { StatisticsSharing } from './sharing';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { Loading, PageHeading } from '../App';
import { useToast } from '../context';
import { NightIcon, type NightMotif } from '../components/ui/NightIcon';
import { AnalyticsControls } from './AnalyticsControls';
import { Chart, chartColors } from './Charts';
import { downloadJSON } from './import';
import { bestCategories, categories, dateError, filterGames, mostMissed, periodFor, summarize, timeline, type Filters, type StatsExport, type StatsGame } from './model';

const display=(value:number|null,suffix='')=>value===null?'—':`${Number(value.toFixed(1)).toLocaleString()}${suffix}`;
type Summary=ReturnType<typeof summarize>;

function OverviewMetrics({stats}:{stats:Summary}){
  const cards:{label:string;value:number|null;motif:NightMotif;suffix?:string;note?:string;help:string}[]=[
    {label:'Games Played',value:stats.games,motif:'games',help:'Completed games in the selected period.'},
    {label:'Total Points',value:stats.points,motif:'points',help:'Saved points under each game’s original scoring rules.'},
    {label:'Wins',value:stats.wins,motif:'wins',help:'First-place finishes, including ties. Category and type filters keep the original whole-game result.'},
    {label:'Accuracy',value:stats.accuracy,motif:'accuracy',suffix:'%',note:stats.accuracy===null?'No recorded answers':stats.partial?'Recorded final answers · partial history':'Final answers',help:'Correct finalized answers ÷ all recorded finalized answers, including timeouts.'},
  ];
  return <section className="analytics-metrics" aria-label="Overview statistics">{cards.map(card=><article className="metric-card" key={card.label} title={card.help} aria-label={`${card.label}: ${display(card.value,card.suffix)}`}><div className="metric-label"><NightIcon motif={card.motif} size={28}/><span>{card.label}</span></div><strong>{display(card.value,card.suffix)}</strong>{card.note&&<small>{card.note}</small>}<span className="sr-only">{card.help}</span></article>)}</section>;
}

function AdvancedMetrics({stats}:{stats:Summary}){
  const groups=[
    {title:'Scoring',motif:'points' as const,metrics:[
      {label:'Average points per game',value:stats.averagePoints,note:'Saved points ÷ completed games.'},
      {label:'Points efficiency',value:stats.efficiency,suffix:'%',note:`${stats.points.toLocaleString()} earned / ${stats.maximum.toLocaleString()} available points, using original game maximums.`},
    ]},
    {title:'Accuracy',motif:'accuracy' as const,metrics:[
      {label:'First-try accuracy',value:stats.firstAccuracy,suffix:'%',note:stats.firstAccuracy===null?'Not tracked in older games, or no first attempts recorded.':`${stats.firstCount} recorded first attempts${stats.firstPartial?' · older history is incomplete':''}.`},
      {label:'Final-answer accuracy',value:stats.accuracy,suffix:'%',note:stats.accuracy===null?'No finalized answer history available.':`Includes timeouts${stats.partial?' · recorded history only':''}.`},
      {label:'Questions finalized',value:stats.questions,note:stats.partial?'Recorded questions only; some older history is incomplete.':'Each finalized question counted once.'},
    ]},
    {title:'Gameplay',motif:'games' as const,metrics:[
      {label:'Win rate',value:stats.winRate,suffix:'%',note:'Tied first-place finishes count as wins.'},
      {label:'Average answer time',value:stats.answerMs===null?null:stats.answerMs/1000,suffix:'s',note:stats.answerMs===null?'Timing data wasn’t recorded, or no timed attempts are available.':`${stats.timedCount} timed answers, including retries; unattempted timeouts excluded.`},
      {label:'Retries used',value:stats.retries,note:stats.retries===null?'Retry data isn’t available for these games.':stats.retriesPartial?'Recorded retry history only.':'Confirmed extra chances consumed.'},
    ]},
  ];
  return <div className="insight-metric-groups">{groups.map(group=><section key={group.title}><h3><NightIcon motif={group.motif} size={24}/>{group.title}</h3><dl>{group.metrics.map(metric=><div key={metric.label}><dt>{metric.label}</dt><dd>{display(metric.value,'suffix' in metric?metric.suffix:undefined)}</dd><small>{metric.value===null&&stats.games===0?'Play a quiz to start tracking this metric.':metric.note}</small></div>)}</dl></section>)}</div>;
}

export function MetricPolicy(){return <details className="panel metric-policy"><summary>How these metrics and rankings work</summary><p>Dates include both selected UTC calendar days, using game completion dates. Category and question-type filters narrow points and question metrics; games and wins keep the results of matching whole games.</p><p>Final accuracy includes finalized timeouts. First-try accuracy excludes timeouts without an attempt and older games without attempt history. Missing data is never counted as zero. Maximum points uses the original maximum of questions actually presented, including timeouts; unpresented questions are excluded.</p><p>Historical scores are never recalculated. Current retry-v2 awards 10, 7, 4 or 2 points for a correct answer after zero, one, two or three retries; incorrect answers and timeouts earn zero. Earlier scoring versions retain their saved points and maximums. A zero-point win is valid when everyone ties at zero under the server’s first-place rule.</p><p>Overall rating = 50% points efficiency + 30% first-try accuracy + 20% win rate. It requires 10 games, 50 recorded first attempts, complete question history and one supported version (retry-v1 or retry-v2). Mixed, legacy and unknown versions are ineligible. Accuracy rankings require 20 finalized questions; win-rate rankings require 5 games.</p><p>Equal metric values share rank. Display order then uses games, points, label and export identity. Raw points reflect activity as well as performance. Imported comparisons are unverified and never affect official rankings.</p></details>;}

function RecentGames({games}:{games:StatsGame[]}){
  const recent=[...games].sort((a,b)=>b.playedAt.localeCompare(a.playedAt)).slice(0,3);
  return <section className="panel analytics-recent" aria-labelledby="recent-games-title"><div className="analytics-section-heading"><h2 id="recent-games-title"><NightIcon motif="history" size={27}/>Recent Games</h2><Link className="text-link" to="/history">View All Games <span aria-hidden="true">→</span></Link></div>{!recent.length?<p className="help-text">No completed games in this selection.</p>:<ul>{recent.map(game=>{const stats=summarize([game]);return <li key={game.gameId}><time dateTime={game.playedAt}>{new Intl.DateTimeFormat(undefined,{year:'numeric',month:'short',day:'numeric',timeZone:'UTC'}).format(new Date(game.playedAt))}<small>UTC</small></time><strong>{game.pointsEarned.toLocaleString()} <small>points</small></strong><span className={`recent-result ${game.won?'is-win':''}`} title="Original whole-game result; tied first place counts as a win.">{game.won?'Won':'Lost'}</span>{stats.accuracy!==null?<span className="recent-accuracy">{display(stats.accuracy,'%')} <small>accuracy{stats.partial?' · partial':''}</small></span>:<span className="recent-accuracy muted">No answer history</span>}</li>;})}</ul>}</section>;
}

function MissedQuestions({games}:{games:StatsGame[]}){
  const missed=mostMissed(games);
  return <section className="panel missed-questions" aria-labelledby="missed-title"><h2 id="missed-title"><NightIcon motif="question" size={26}/>Most Missed Questions</h2><p className="help-text">Up to 10 questions, ranked by incorrect finalized answers, including timeouts.</p>{!missed.length?<p className="chart-empty">{games.length?'No missed questions in this period.':'No question history in this selection.'}</p>:<ol>{missed.map(question=><li key={question.id}><details><summary><span><strong>{question.label}</strong><small>{question.category}</small></span><span className="missed-count">{question.value}<small>incorrect</small></span></summary><p>{question.value} incorrect / {question.answered} recorded finalized answers · {question.timeouts} timed out.</p></details></li>)}</ol>}</section>;
}

function BestCategories({games}:{games:StatsGame[]}){
  const best=bestCategories(games);
  return <section className="panel" aria-labelledby="best-category-title"><h2 id="best-category-title">Best-performing categories</h2>
    <p className="help-text">Highest final-answer accuracy in this selection. Ties are shown together; sample sizes help you compare fairly.</p>
    {!best.length?<p>No recorded answers yet.</p>:<ul>{best.map(category=><li key={category.label}>
      <strong>{category.label}</strong> · {display(category.value,'%')} · {category.correct} / {category.answered} correct
    </li>)}</ul>}
  </section>;
}

export function AnalyticsPage(){
  const [filters,setFilters]=useState<Filters>(()=>({period:periodFor('30'),category:'',mode:'',ruleset:''}));
  const [group,setGroup]=useState<'day'|'week'|'month'>('day'),[exporting,setExporting]=useState(false),[data,setData]=useState<StatsExport|null>(null),[error,setError]=useState('');
  const toast=useToast(),invalid=dateError(filters.period);
  const query=invalid?null:new URLSearchParams({zone:'UTC',...(filters.period.from?{from:filters.period.from}:{}),...(filters.period.to?{to:filters.period.to}:{})}).toString();
  useEffect(()=>{let active=true;setData(null);setError('');if(query!==null)api<StatsExport>(`/analytics/report?${query}`).then(result=>{if(active)setData(result);}).catch(e=>{if(active)setError(e.message);});return()=>{active=false;};},[query]);
  const selected=useMemo(()=>filterGames(data?.games??[],filters),[data,filters]),stats=summarize(selected),category=categories(selected),trend=timeline(selected,group);
  const versions=[...new Set(selected.map(game=>game.scoringVersion))];
  const accuracySeries=[{label:'First try',color:chartColors[1],values:trend.map(t=>t.firstAccuracy)},{label:'Final answer',color:chartColors[0],values:trend.map(t=>t.accuracy)}].filter(series=>series.values.some(value=>value!==null));
  async function exportStats(){setExporting(true);try{const extra=new URLSearchParams({category:filters.category,mode:filters.mode,ruleset:filters.ruleset});const result=await api<StatsExport>(`/analytics/export?${query}&${extra}`);downloadJSON(result,`hootly-my-stats-${new Date().toISOString().slice(0,10)}.json`);toast('Your statistics export is ready.');}catch(e){toast((e as Error).message);}finally{setExporting(false);}}
  const grouping=(label:string)=><label className="analytics-grouping">{label}<select aria-label={label} value={group} onChange={e=>setGroup(e.target.value as typeof group)}><option value="day">Daily</option><option value="week">Weekly (Monday)</option><option value="month">Monthly</option></select></label>;
  return <div className="analytics-overview"><PageHeading motif="analytics" title="My Analytics" subtitle="See how your trivia skills are growing."/>
    <StatisticsSharing/>
    <AnalyticsControls compact filters={filters} onChange={setFilters} games={data?.games??[]}><div className="analytics-toolbar-actions"><button className="button small" disabled={!data||exporting||!!invalid} onClick={()=>void exportStats()}><NightIcon motif="down" size={23}/>{exporting?'Preparing export…':'Export My Stats'}</button><Link className="button small" to="/analytics/compare"><NightIcon motif="people" size={23}/>Compare Players</Link></div></AnalyticsControls>
    {invalid?<p className="error" role="alert">{invalid}</p>:error?<p className="error" role="alert">{error}</p>:!data?<Loading/>:<>
      <OverviewMetrics stats={stats}/>
      {!selected.length&&<section className="panel analytics-empty"><NightIcon motif="lantern" size={40}/><div><h2>{data.games.length?'No matching games':'No games in this period'}</h2><p>{data.games.length?'Try another category, question type, or scoring version.':'No games yet? Play a quiz to start tracking your progress, or choose a wider date range.'}</p><Link className="text-link" to="/join">Join a room <span aria-hidden="true">→</span></Link></div></section>}
      <div className="analytics-chart-grid analytics-primary-charts">
        <Chart title="Accuracy by Category" axis="Accuracy (%)" labels={category.map(c=>`${c.label} (${c.correct}/${c.answered})`)} max={100} series={[{label:'Final answers',color:chartColors[0],values:category.map(c=>c.value)}]}/>
        <Chart title="Points Over Time" axis="Points earned" labels={trend.map(t=>t.label)} line controls={grouping('Time grouping')} description={trend.length===1?'Play on more days to see your progress. One recorded time period is shown.':undefined} series={[{label:'Points',color:chartColors[1],values:trend.map(t=>t.points)}]}/>
      </div>
      <details className="analytics-insights"><summary><NightIcon motif="history" size={29}/><span><strong>Detailed Insights</strong><small>Scoring, accuracy trends and missed questions</small></span><span className="insight-chevron" aria-hidden="true">⌄</span></summary><div className="analytics-insight-content"><AdvancedMetrics stats={stats}/>
        {versions.length>1&&<p className="analytics-scoring-note">Different scoring versions are included. Saved points and original maximums are preserved. Use Filters to view one scoring version.</p>}
        <details className="analytics-subsection"><summary>Average Score Trend</summary><Chart title="Average Score Over Time" axis="Average points per game" labels={trend.map(t=>t.label)} line controls={grouping('Average score time grouping')} description="Saved points divided by completed games in each period. Different scoring versions retain their original points." series={[{label:'Average score',color:chartColors[2],values:trend.map(t=>t.averagePoints)}]}/></details>
        <BestCategories games={selected}/>
        <details className="analytics-subsection"><summary>Accuracy Trend</summary><Chart title="Accuracy Trend" axis="Accuracy (%)" labels={accuracySeries.length?trend.map(t=>t.label):[]} max={100} line controls={grouping('Accuracy trend grouping')} description={accuracySeries.length===1&&accuracySeries[0].label==='Final answer'?'First-try history wasn’t recorded. Only final-answer accuracy is shown.':trend.length===1?'One recorded time period; more games are needed to show a trend.':undefined} series={accuracySeries}/></details>
        <details className="analytics-subsection"><summary>Most Missed Questions</summary><MissedQuestions games={selected}/></details>
        <MetricPolicy/>
      </div></details>
      <RecentGames games={selected}/>
    </>}
  </div>;
}
