import {ToastProvider} from '../context';
import {afterEach,expect,it,vi} from 'vitest';
import {act,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,Routes,Route} from 'react-router-dom';
import {QuizEditorPage} from '../QuizEditorPage';
import {MyQuizzesPage,QuizPreviewPage} from '../QuizPages';
import {CreateRoomPage} from '../CreateRoomPage';
import {blankQuestion,composition,type QuizDetail} from '../quizzes';

afterEach(()=>vi.unstubAllGlobals());
function fixture(saveGate?:Promise<void>){
 let saved:QuizDetail={quiz:{id:'saved',title:'Science Quiz Night',description:'',categoryId:'science',category:'Science',visibility:'PRIVATE',status:'READY',version:1,questionCount:2,multipleChoiceCount:1,trueFalseCount:1,createdAt:'2026-11-16T12:00:00Z',updatedAt:'2026-11-16T12:00:00Z'},questions:[{...blankQuestion(),text:'Which is a star?',options:['Sun','Moon'],correctIndex:0},{...blankQuestion('TRUE_FALSE'),text:'The Sun is a star.',correctIndex:0}]};
 const requests:{path:string;body:Record<string,unknown>}[]=[];
 vi.stubGlobal('fetch',vi.fn(async(path:string,options?:RequestInit)=>{
  if(path==='/api/auth/csrf')return Response.json({token:'test',headerName:'X-CSRF-TOKEN'});
  if(path==='/api/quizzes/categories'&&options?.method==='POST')return Response.json({id:'science',name:'Science'});
  if(path==='/api/quizzes/categories')return Response.json([{id:'science',name:'Science'}]);
  if((path==='/api/quizzes'||path==='/api/quizzes/saved')&&['PUT','POST'].includes(options?.method??'')){
   const body=JSON.parse(String(options?.body));requests.push({path,body});if(saveGate)await saveGate;
   if(body.status==='READY'&&body.questions.some((q:{text:string;correctIndex:number|null})=>!q.text.trim()||q.correctIndex===null))return Response.json({message:'Question 1: select one correct answer.'},{status:400});
   saved={quiz:{...saved.quiz,...body,id:'saved',version:saved.quiz.version+1,questionCount:body.questions.length,multipleChoiceCount:body.questions.filter((q:{type:string})=>q.type==='MULTIPLE_CHOICE').length,trueFalseCount:body.questions.filter((q:{type:string})=>q.type==='TRUE_FALSE').length},questions:body.questions};return Response.json(saved);
  }
  if(path==='/api/quizzes/saved'&&options?.method==='DELETE'){requests.push({path,body:{deleted:true}});return new Response(null,{status:204});}
  if(path==='/api/quizzes/saved/duplicate')return Response.json({...saved,quiz:{...saved.quiz,id:'copy',status:'DRAFT'}});
  if(path==='/api/quizzes/saved')return Response.json(saved);
  if(path==='/api/quizzes/library')return Response.json([{id:'q1',category:'Science',question:saved.questions[0]}]);
  if(path==='/api/quizzes')return Response.json([saved.quiz]);
  if(path==='/api/rooms'){requests.push({path,body:JSON.parse(String(options?.body))});return Response.json({code:'ABCD234X'});}
  throw Error(path);
 }));return requests;
}
function mount(path='/bank/new'){render(<MemoryRouter initialEntries={[path]}><ToastProvider><Routes><Route path="/bank" element={<MyQuizzesPage/>}/><Route path="/bank/new" element={<QuizEditorPage/>}/><Route path="/bank/:id/edit" element={<QuizEditorPage/>}/><Route path="/bank/:id/preview" element={<QuizPreviewPage/>}/><Route path="/create" element={<CreateRoomPage/>}/><Route path="/rooms/:code" element={<h1>Live lobby</h1>}/></Routes></ToastProvider></MemoryRouter>);}
it('creates a category and mixed draft, then edits answers, reorders, duplicates and previews in place',async()=>{
 const requests=fixture();mount();const u=userEvent.setup({delay:null});
 expect(screen.queryByText(/import.*json/i)).not.toBeInTheDocument();expect(document.querySelector('input[type="file"]')).toBeNull();
 await u.type(screen.getByLabelText('Title'),'Science Quiz Night');await u.click(screen.getByText('New category',{exact:true}));await u.type(screen.getByLabelText('New custom category'),'Science');await u.click(screen.getByRole('button',{name:'Add category'}));await waitFor(()=>expect(screen.getByLabelText('Category')).toHaveValue('science'));
 await u.type(screen.getByLabelText('Question 1 text'),'Which is a star?');for(const [letter,value]of [['A','Sun'],['B','Moon'],['C','Earth'],['D','Mars']])await u.type(screen.getByRole('textbox',{name:`Question 1 answer ${letter}`}),value);
 await u.click(screen.getByRole('radio',{name:'Question 1 correct answer A'}));await u.click(screen.getByRole('button',{name:'Add True / False'}));await u.type(screen.getByLabelText('Question 2 text'),'The Sun is a star.');await u.click(screen.getByRole('radio',{name:'Question 2 correct answer True'}));
 expect(screen.getByText('2 Questions · Mixed')).toBeInTheDocument();await u.click(screen.getByRole('button',{name:'Move question 2 up'}));expect(screen.getByLabelText('Question 1 type')).toHaveValue('TRUE_FALSE');expect(screen.queryByRole('textbox',{name:'Question 1 answer A'})).not.toBeInTheDocument();
 await u.click(screen.getByRole('button',{name:'Duplicate question 1'}));expect(screen.getByText('3 Questions · Mixed')).toBeInTheDocument();await u.click(screen.getByRole('button',{name:'Delete question 2'}));
 await u.click(screen.getByRole('button',{name:'Edit question 1'}));await u.click(screen.getByRole('button',{name:'Preview question 1'}));expect(screen.getByRole('region',{name:'Question 1 preview'})).toHaveTextContent('Correct answer');await u.click(screen.getByRole('button',{name:'Preview question 1'}));
 await u.click(screen.getByRole('button',{name:'Preview quiz'}));expect(screen.getByRole('region',{name:'Creator preview'})).toHaveTextContent('Correct answer');await u.click(screen.getByRole('button',{name:'Preview quiz'}));
 await u.click(screen.getByRole('button',{name:'Save Draft'}));await waitFor(()=>expect(requests).toHaveLength(1));expect(requests[0].body).toMatchObject({status:'DRAFT',categoryId:'science'});
 await screen.findByRole('heading',{name:'Edit quiz'});await waitFor(()=>expect(screen.getByRole('button',{name:'Save Quiz'})).toBeEnabled());await u.click(screen.getByRole('button',{name:'Save Quiz'}));await waitFor(()=>expect(requests).toHaveLength(2));expect(requests[1].body.status).toBe('READY');expect(requests[1].path).toBe('/api/quizzes/saved');expect(await screen.findByRole('heading',{name:'My Quizzes'})).toBeInTheDocument();expect(screen.getByText('Quiz saved successfully')).toBeInTheDocument();expect(requests.some(request=>request.path==='/api/rooms')).toBe(false);
},20000);
it('keeps unfinished content after ready validation errors and reuses personal library questions',async()=>{
 fixture();mount();const u=userEvent.setup({delay:null});await u.type(screen.getByLabelText('Title'),'Unfinished');await u.click(screen.getByRole('button',{name:'Save Quiz'}));expect(await screen.findByRole('alert')).toHaveTextContent('Add a question');expect(screen.getByLabelText('Title')).toHaveValue('Unfinished');
 await u.click(screen.getByRole('button',{name:'Reuse a question'}));await u.click(await screen.findByRole('button',{name:'Add to quiz'}));expect(screen.getByLabelText('Question 1 text')).toHaveValue('Which is a star?');
});
it('opens the collection, previews a mixed quiz, and confirms deletion',async()=>{
 const requests=fixture();mount('/bank');const u=userEvent.setup({delay:null});expect(await screen.findByText('2 questions · Mixed')).toBeInTheDocument();await u.click(screen.getByRole('link',{name:'Preview quiz'}));expect(await screen.findByRole('heading',{name:'Which is a star?'})).toBeInTheDocument();await u.click(screen.getByRole('link',{name:'My Quizzes'}));await u.click(await screen.findByRole('button',{name:'Delete quiz'}));await u.click(screen.getByRole('button',{name:'Keep quiz'}));expect(requests).toHaveLength(0);await u.click(screen.getByRole('button',{name:'Delete quiz'}));await u.click(screen.getByRole('button',{name:'Delete quiz permanently'}));await waitFor(()=>expect(requests[0].body.deleted).toBe(true));
});
it('preselects a hosted saved quiz without fetching instant categories or showing unrelated filters',async()=>{
 const requests=fixture();mount('/create?quiz=saved');const u=userEvent.setup({delay:null});await screen.findByRole('radio',{name:/Science Quiz Night/});expect(screen.getByRole('radio',{name:'My Quizzes'})).toBeChecked();expect(screen.queryByLabelText('Difficulty')).not.toBeInTheDocument();expect(screen.queryByLabelText('Question type')).not.toBeInTheDocument();
 const name=screen.getByLabelText('Room name');await u.clear(name);await u.type(name,'Saved quiz room');await u.click(screen.getByRole('button',{name:'Tune the round'}));await u.click(screen.getByRole('radio',{name:'3'}));await u.click(screen.getByRole('button',{name:'Create quiz room'}));await waitFor(()=>expect(requests[0].body).toMatchObject({quizId:'saved',questionSource:'LOCAL',questionCount:2,questionType:'any',retryAllowance:3}));expect(document.body).not.toHaveTextContent('OpenTDB');
});
it('computes Mixed from actual types rather than storing a third type',()=>{expect(composition({multipleChoiceCount:2,trueFalseCount:3})).toBe('Mixed');expect(blankQuestion('TRUE_FALSE').options).toEqual(['True','False']);});
it('arranges pasted mixed content for review, retains manual mode and never guesses missing answers',async()=>{
 const requests=fixture();mount();const u=userEvent.setup({delay:null});await u.type(screen.getByLabelText('Title'),'Pasted night');await u.click(screen.getByRole('radio',{name:'Paste Questions'}));
 await u.type(screen.getByLabelText('Paste questions'),'1. Which is a star?\nA. Sun\nB. Moon\nAnswer: A\n\n2. The Sun is a star.\nType: True / False');await u.click(screen.getByRole('button',{name:'Save Draft'}));expect(await screen.findByRole('alert')).toHaveTextContent('Arrange or clear');expect(requests).toHaveLength(0);
 await u.click(screen.getByRole('button',{name:'Arrange Questions'}));expect(screen.getByText('2 Questions · Mixed')).toBeInTheDocument();expect(screen.getByRole('radio',{name:'Question 1 correct answer A'})).toBeChecked();await u.click(screen.getByRole('button',{name:'Edit question 2'}));expect(screen.getByRole('radio',{name:'Question 2 correct answer True'})).not.toBeChecked();expect(screen.getByRole('note')).toHaveTextContent('Choose one correct answer');
 await u.click(screen.getByRole('radio',{name:'Question 2 correct answer False'}));await u.click(screen.getByRole('radio',{name:'Create Manually'}));expect(screen.getByLabelText('Question 2 text')).toHaveValue('The Sun is a star.');expect(screen.queryByText(/difficulty/i)).not.toBeInTheDocument();await u.click(screen.getByRole('button',{name:'Save Draft'}));await waitFor(()=>expect(requests).toHaveLength(1));expect(requests[0].body.status).toBe('DRAFT');expect(JSON.stringify(requests[0].body)).not.toContain('difficulty');
});
it('leaves manual work intact when pasted options are invalid',async()=>{fixture();mount();const u=userEvent.setup({delay:null});await u.type(screen.getByLabelText('Question 1 text'),'Keep my question');await u.click(screen.getByRole('radio',{name:'Paste Questions'}));await u.type(screen.getByLabelText('Paste questions'),'1. Bad question\nA. a\nB. b\nC. c\nD. d\nE. e\nF. f\nG. g');await u.click(screen.getByRole('button',{name:'Arrange Questions'}));expect(await screen.findByRole('alert')).toHaveTextContent('six options');await u.click(screen.getByRole('radio',{name:'Create Manually'}));expect(screen.getByLabelText('Question 1 text')).toHaveValue('Keep my question');});
it('confirms Clear All, restores the entire quiz with undo/redo and writes nothing until Save Draft',async()=>{
 const requests=fixture();mount('/bank/saved/edit');const u=userEvent.setup({delay:null});await screen.findByLabelText('Question 1 text');
 expect(screen.getByRole('button',{name:'Undo'})).toBeDisabled();expect(screen.getByRole('button',{name:'Redo'})).toBeDisabled();
 await u.click(screen.getByRole('button',{name:'Clear All'}));await u.click(screen.getByRole('button',{name:'Cancel'}));expect(screen.getByLabelText('Question 1 text')).toHaveValue('Which is a star?');
 await u.click(screen.getByRole('button',{name:'Clear All'}));await u.click(screen.getByRole('button',{name:'Clear all questions'}));expect(screen.getByRole('heading',{name:'Add your first question'})).toBeInTheDocument();expect(requests).toHaveLength(0);
 await u.click(screen.getByRole('button',{name:'Undo'}));expect(screen.getByRole('radio',{name:'Question 1 correct answer A'})).toBeChecked();await u.click(screen.getByRole('button',{name:'Edit question 2'}));expect(screen.getByRole('radio',{name:'Question 2 correct answer True'})).toBeChecked();
 fireEvent.keyDown(window,{key:'y',ctrlKey:true});expect(screen.getByRole('heading',{name:'Add your first question'})).toBeInTheDocument();fireEvent.keyDown(window,{key:'z',ctrlKey:true});expect(screen.getByLabelText('Question 2 text')).toHaveValue('The Sun is a star.');fireEvent.keyDown(window,{key:'Z',ctrlKey:true,shiftKey:true});expect(screen.getByRole('heading',{name:'Add your first question'})).toBeInTheDocument();
 expect(requests).toHaveLength(0);await u.click(screen.getByRole('button',{name:'Save Draft'}));await waitFor(()=>expect(requests).toHaveLength(1));expect(requests[0]).toMatchObject({path:'/api/quizzes/saved',body:{questions:[],status:'DRAFT'}});
});
it('undoes answer edits, additions, reordering, deletion and clearing with correct selections intact',async()=>{
 fixture();mount('/bank/saved/edit');const u=userEvent.setup({delay:null});await screen.findByLabelText('Question 1 text');
 fireEvent.change(screen.getByLabelText('Question 1 text'),{target:{value:'Edited question'}});fireEvent.blur(screen.getByLabelText('Question 1 text'));await u.click(screen.getByRole('button',{name:'Undo'}));expect(screen.getByLabelText('Question 1 text')).toHaveValue('Which is a star?');
 await u.click(screen.getByRole('button',{name:'Add answer option'}));fireEvent.change(screen.getByRole('textbox',{name:'Question 1 answer C'}),{target:{value:'Earth'}});fireEvent.blur(screen.getByRole('textbox',{name:'Question 1 answer C'}));await u.click(screen.getByRole('radio',{name:'Question 1 correct answer C'}));
 await u.click(screen.getByRole('button',{name:'Move question 1 answer C up'}));expect(screen.getByRole('textbox',{name:'Question 1 answer B'})).toHaveValue('Earth');expect(screen.getByRole('radio',{name:'Question 1 correct answer B'})).toBeChecked();
 await u.click(screen.getByRole('button',{name:'Remove question 1 answer B'}));expect(screen.getByRole('radio',{name:'Question 1 correct answer A'})).not.toBeChecked();await u.click(screen.getByRole('button',{name:'Undo'}));expect(screen.getByRole('radio',{name:'Question 1 correct answer B'})).toBeChecked();
 await u.click(screen.getByRole('button',{name:'Undo'}));expect(screen.getByRole('radio',{name:'Question 1 correct answer C'})).toBeChecked();await u.click(screen.getByRole('button',{name:'Clear answer choices'}));expect(screen.getByRole('textbox',{name:'Question 1 answer C'})).toHaveValue('');await u.click(screen.getByRole('button',{name:'Undo'}));expect(screen.getByRole('textbox',{name:'Question 1 answer C'})).toHaveValue('Earth');expect(screen.getByRole('radio',{name:'Question 1 correct answer C'})).toBeChecked();
 const input=screen.getByLabelText('Question 1 text'),event=new KeyboardEvent('keydown',{key:'z',ctrlKey:true,bubbles:true,cancelable:true});input.dispatchEvent(event);expect(event.defaultPrevented).toBe(false);
 await u.click(screen.getByRole('button',{name:'Edit question 2'}));await u.click(screen.getByRole('button',{name:'Move question 2 up'}));expect(screen.getByLabelText('Question 1 type')).toHaveValue('TRUE_FALSE');await u.click(screen.getByRole('button',{name:'Undo'}));expect(screen.getByLabelText('Question 2 type')).toHaveValue('TRUE_FALSE');
});
it('undoes and redoes adding and deleting questions without retaining stale redo branches',async()=>{
 fixture();mount('/bank/saved/edit');const u=userEvent.setup({delay:null});await screen.findByLabelText('Question 1 text');await u.click(screen.getByRole('button',{name:'Add True / False'}));expect(screen.getByLabelText('Question 3 text')).toBeInTheDocument();await u.click(screen.getByRole('button',{name:'Undo'}));expect(screen.queryByRole('button',{name:'Edit question 3'})).not.toBeInTheDocument();await u.click(screen.getByRole('button',{name:'Redo'}));await u.click(screen.getByRole('button',{name:'Delete question 3'}));await u.click(screen.getByRole('button',{name:'Undo'}));expect(screen.getByLabelText('Question 3 text')).toBeInTheDocument();fireEvent.change(screen.getByLabelText('Question 3 text'),{target:{value:'A new branch'}});expect(screen.getByRole('button',{name:'Redo'})).toBeDisabled();
});
it('saves a new quiz once, notifies and returns to My Quizzes without creating a room',async()=>{
 let release=()=>{};const gate=new Promise<void>(resolve=>{release=resolve;}),requests=fixture(gate);mount();const u=userEvent.setup({delay:null});fireEvent.change(screen.getByLabelText('Title'),{target:{value:'One saved quiz'}});await u.click(screen.getByRole('button',{name:'Reuse a question'}));await u.click(await screen.findByRole('button',{name:'Add to quiz'}));const save=screen.getByRole('button',{name:'Save Quiz'});fireEvent.click(save);fireEvent.click(save);await waitFor(()=>expect(requests).toHaveLength(1));expect(save).toBeDisabled();await act(async()=>release());expect(await screen.findByRole('heading',{name:'My Quizzes'})).toBeInTheDocument();expect(screen.getByText('Quiz saved successfully')).toBeInTheDocument();expect(requests).toHaveLength(1);expect(requests[0]).toMatchObject({path:'/api/quizzes',body:{status:'READY'}});expect(document.querySelectorAll('.quiz-collection-card')).toHaveLength(1);
});
