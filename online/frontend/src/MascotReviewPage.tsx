import { PlayerOwl } from './components/avatar/PlayerOwl';
import { avatarFor,birdSpecies } from './components/avatar/avatarConfig';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, RotateCcw } from 'lucide-react';
import { HootlyCharacter, type OwlExpression, type OwlScene } from './components/brand/HootlyCharacter';
import './styles/mascot-review.css';

const poses:{title:string;copy:string;expression:OwlExpression;scene:OwlScene;privateInput?:boolean}[]=[
  {title:'A warm welcome',copy:'Landing and login · a soft welcome hop, heart eyes and a wave.',expression:'welcoming',scene:'landing'},
  {title:'Your password is private',copy:'Login · wings cover the eyes independently.',expression:'welcoming',scene:'login',privateInput:true},
  {title:'Getting to know you',copy:'Registration · curious head tilt and attentive eyes.',expression:'curious',scene:'registration'},
  {title:'Nicely played',copy:'Correct answer · a happy little jump, smile and wing lift.',expression:'happy',scene:'gameplay'},
  {title:'There’s another round',copy:'Incorrect answer or loss · a gentle body sway and settle.',expression:'sad',scene:'gameplay'},
  {title:'You won!',copy:'Results · two celebratory jumps with wing flapping.',expression:'celebrating',scene:'results'},
  {title:'In the zone',copy:'Gameplay · focused eyes without idle distractions.',expression:'focused',scene:'gameplay'},
  {title:'A little time to think',copy:'Loading · thoughtful gaze and natural blinking.',expression:'thinking',scene:'loading'},
];
export function MascotReviewPage(){
  const [replay,setReplay]=useState(0),[privateInput,setPrivateInput]=useState(false),[attention,setAttention]=useState(0),[loopMotion,setLoopMotion]=useState(false);
  return <main className="mascot-review"><Link className="text-link" to="/"><ArrowLeft size={16}/>Back to Hootly</Link><div className="mascot-review-heading"><div><span className="eyebrow">APPROVED CHARACTER</span><h1>One familiar owl.<br/><em>A personality for every moment.</em></h1><p>The body now hops, leans and settles, with feet and a ground shadow that react together. These are movement previews, not game results.</p></div><div className="mascot-review-controls"><button className="button" onClick={()=>setReplay(value=>value+1)}><RotateCcw size={16}/>Replay movement</button><label className="review-checkbox"><input type="checkbox" checked={loopMotion} onChange={event=>{setLoopMotion(event.target.checked);setReplay(value=>value+1);}}/>Loop movement</label></div></div>
    <div className="mascot-review-grid">{poses.map(pose=><section className="panel mascot-review-card" key={pose.title}><HootlyCharacter key={`${pose.title}:${replay}`} expression={pose.expression} scene={pose.scene} privateInput={pose.privateInput} size={210} label={pose.title} loopMotion={loopMotion}/><h2>{pose.title}</h2><p>{pose.copy}</p></section>)}</div>
    <section className="panel mascot-review-login"><div><span className="eyebrow">TRY THE LOGIN REACTION</span><h2>Welcoming, with a little discretion.</h2><p>Move the attention slider and toggle password privacy to review the separate pupils and wings.</p><label>Input attention<input type="range" min={-1} max={1} step={.1} value={attention} onChange={event=>setAttention(Number(event.target.value))}/></label><label className="review-checkbox"><input type="checkbox" checked={privateInput} onChange={event=>setPrivateInput(event.target.checked)}/>Cover the eyes with wings</label></div><HootlyCharacter scene="login" expression="welcoming" attention={attention} privateInput={privateInput} size={280} label="Interactive welcoming owl"/></section>
    <section className="panel"><h2>Player owls</h2><p>Procedural avatar previews, separate from the approved Hootly brand character. Real accounts keep their saved colors and accessories across expressions.</p><div className="player-owl-gallery">{Array.from({length:16},(_,i)=><div key={i}><PlayerOwl identity={`preview:${i}`} avatar={{...avatarFor(`preview:${i}`),body:i%9,accessory:i%8,species:i%8}} size={112} name={`Preview ${i+1}`} reaction={replay?{id:`preview:${replay}:${i}`,type:'gain'}:undefined}/><small>{birdSpecies[i%8]} / {i<8?'moonlight':'twilight'}</small></div>)}</div></section>
    <p className="mascot-review-note">This character is approved and active. See the <Link className="text-link" to="/">landing page</Link> or <Link className="text-link" to="/login">login page</Link>.</p>
  </main>;
}
