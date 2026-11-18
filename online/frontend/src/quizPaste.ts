import {type QuizQuestion} from './quizzes';

export interface ArrangedQuestions {questions:QuizQuestion[];errors:string[]}
export const pasteExample=`1. Which planet is known as the Red Planet?
A. Mars
B. Earth
C. Venus
D. Jupiter
Answer: A

2. The Sun is a star.
Type: True / False
Answer: True`;

const normalize=(value:string)=>value.normalize('NFKC').trim().replace(/\s+/g,' ').toLowerCase();
export function questionIssues(q:QuizQuestion):string[]{
  const issues:string[]=[];
  if(!q.text.trim())issues.push('Add a question.');
  if(q.options.length<2||q.options.some(a=>!a.trim()))issues.push('Complete the answer options.');
  if(q.options.length>6)issues.push('Use up to six answer options.');
  const filled=q.options.filter(a=>a.trim()).map(normalize);
  if(new Set(filled).size!==filled.length)issues.push('Answer options must be different.');
  if(q.correctIndex===null||q.correctIndex<0||q.correctIndex>=q.options.length)issues.push('Choose one correct answer.');
  return issues;
}

/** Explicit answer keys only. Uncertain answers stay unselected for creator review. */
export function arrangeQuestions(input:string):ArrangedQuestions {
  if(input.length>500000)return {questions:[],errors:['Paste fewer than 500,000 characters at a time.']};
  if(!input.trim())return {questions:[],errors:['Paste your questions first.']};
  const lines=input.replace(/\r\n?/g,'\n').split('\n');
  const numbered=lines.some(line=>/^\s*(?:Q(?:uestion)?\s*)?\d+[.):]\s+\S/i.test(line));
  const blocks:string[][]=[];let current:string[]=[];
  for(const line of lines){
    const heading=/^\s*(?:Q(?:uestion)?\s*)?\d+[.):]\s+(.*)$/i.exec(line);
    if(heading){if(current.some(l=>l.trim()))blocks.push(current);current=[heading[1]];}
    else if(!numbered&&!line.trim()){if(current.some(l=>l.trim()))blocks.push(current);current=[];}
    else current.push(line);
  }
  if(current.some(l=>l.trim()))blocks.push(current);
  if(blocks.length>50)return {questions:[],errors:['Arrange up to 50 questions at a time.']};
  const errors:string[]=[];
  const questions=blocks.map((block,index):QuizQuestion=>{
    const text:string[]=[],options:{letter:string;text:string}[]=[],keys:string[]=[];
    let booleanType=false,declaredType=false;
    for(const raw of block){
      const line=raw.trim();if(!line)continue;
      const type=/^type\s*:\s*(.*)$/i.exec(line);
      if(type){declaredType=true;booleanType=/^(?:true\s*\/\s*false|true\s+or\s+false|boolean)$/i.test(type[1]);if(!booleanType&&!/^(?:multiple\s*choice|mcq)$/i.test(type[1]))errors.push(`Question ${index+1}: choose Multiple Choice or True / False.`);continue;}
      const answer=/^(?:correct\s*answer|answer|correct)\s*:\s*(.*)$/i.exec(line);
      if(answer){keys.push(answer[1].trim());continue;}
      const option=/^([A-Z])[.):]\s*(.*)$/i.exec(line);
      if(option){options.push({letter:option[1].toUpperCase(),text:option[2]});continue;}
      if(options.length)errors.push(`Question ${index+1}: put each answer on one labelled line.`);
      else text.push(line);
    }
    if(!declaredType&&!options.length&&keys.length&&keys.every(k=>/^(true|false)$/i.test(k)))booleanType=true;
    if(!declaredType&&/\(\s*true\s*\/\s*false\s*\)\s*$/i.test(text.join(' ')))booleanType=true;
    const values=booleanType?['True','False']:options.map(o=>o.text);
    const candidates=keys.map(key=>{
      if(booleanType){const exact=values.findIndex(v=>normalize(v)===normalize(key));if(exact>=0)return exact;}
      const letter=/^[A-F][.)]?$/i.test(key)?key[0].toUpperCase():null;
      const matches=letter?options.map((o,i)=>o.letter===letter?i:-1).filter(i=>i>=0):values.map((v,i)=>normalize(v)===normalize(key)?i:-1).filter(i=>i>=0);
      if(matches.length!==1)return null;
      if(booleanType)return values.findIndex(v=>normalize(v)===normalize(options[matches[0]]?.text??''));
      return matches[0];
    });
    const correctIndex=candidates.length&&candidates.every(i=>i!==null&&i>=0&&i===candidates[0])?candidates[0]:null;
    if(options.length>6||options.some(o=>!/[A-F]/.test(o.letter))||(!booleanType&&new Set(options.map(o=>o.letter)).size!==options.length))errors.push(`Question ${index+1}: use unique answer labels A–F, with up to six options.`);
    if(booleanType&&options.length&&(options.length!==2||new Set(options.map(o=>normalize(o.text))).size!==2||options.some(o=>!['true','false'].includes(normalize(o.text)))))errors.push(`Question ${index+1}: True / False cannot contain other answer options.`);
    const question:QuizQuestion={type:booleanType?'TRUE_FALSE':'MULTIPLE_CHOICE',text:text.join('\n'),options:values.length?values:['',''],correctIndex};
    if(question.text.length>4000||question.options.some(o=>o.length>1000))errors.push(`Question ${index+1}: shorten the question to 4,000 characters and each answer to 1,000.`);
    return question;
  });
  return {questions,errors:[...new Set(errors)]};
}
