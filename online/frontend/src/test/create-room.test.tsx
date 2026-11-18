import { afterEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { StrictMode } from 'react';
import { MotionProvider } from '../components/ui/MotionProvider';
import { CreateRoomPage } from '../RoomPages';

// Keep keyboard events, without synthetic inter-key setTimeout delays in these flow tests.
afterEach(()=>vi.unstubAllGlobals());
it('advances and returns between setup steps without submitting, then saves the chosen settings once',async()=>{
  const requests:Record<string,unknown>[]=[];
  vi.stubGlobal('fetch',vi.fn(async(path:string,options?:RequestInit)=>{
    if(path==='/api/bank/opentdb/categories')return Response.json({categories:[{id:18,name:'Science: Computers'},{id:23,name:'History'}],cached:false});
    if(path==='/api/auth/csrf')return Response.json({token:'test',headerName:'X-CSRF-TOKEN'});
    if(path==='/api/rooms'&&options?.method==='POST'){requests.push(JSON.parse(String(options.body)));return Response.json({code:'ABC234XY'},{status:201});}
    throw new Error(`Unexpected request: ${path}`);
  }));
  render(<StrictMode><MotionProvider><MemoryRouter initialEntries={['/create']}><Routes><Route path="/create" element={<CreateRoomPage/>}/><Route path="/rooms/:code" element={<h1>Confirmed room lobby</h1>}/></Routes></MemoryRouter></MotionProvider></StrictMode>);const user=userEvent.setup({delay:null});
  await user.type(screen.getByLabelText('Room name'),'Curious crew');
  const category=await screen.findByRole('combobox',{name:'Category'});await waitFor(()=>expect(category).toBeEnabled());await user.click(category);await user.click(screen.getByRole('option',{name:'Science: Computers'}));expect(category).toHaveTextContent('Science: Computers');expect(document.querySelector('.category-chips')).toBeNull();
  await user.selectOptions(screen.getByLabelText('Difficulty'),'medium');await user.selectOptions(screen.getByLabelText('Question type'),'multiple');await user.click(screen.getByRole('button',{name:'Tune the round'}));
  expect(requests).toHaveLength(0);const count=screen.getByLabelText('Number of questions'),seconds=screen.getByLabelText('Seconds per question');
  expect(document.body).not.toHaveTextContent('10 / 7 / 4 / 2');
  await user.clear(count);await user.type(count,'10');await user.clear(seconds);await user.type(seconds,'10');
  await user.click(screen.getByRole('button',{name:'Back'}));expect(requests).toHaveLength(0);
  await user.click(screen.getByRole('button',{name:'Tune the round'}));expect(count).toHaveValue(10);expect(seconds).toHaveValue(10);expect(requests).toHaveLength(0);
  expect(screen.getByRole('radio',{name:'Off'})).toBeChecked();expect(screen.getAllByRole('radio')).toHaveLength(4);await user.click(screen.getByRole('radio',{name:'3'}));
  const reveal=screen.getByRole('switch',{name:'Reveal Answers'});expect(reveal).toBeChecked();reveal.focus();await user.keyboard(' ');expect(reveal).not.toBeChecked();
  await user.click(screen.getByRole('button',{name:'Create quiz room'}));await waitFor(()=>expect(requests).toHaveLength(1));
  expect(requests[0]).toMatchObject({title:'Curious crew',questionCount:10,secondsPerQuestion:10,maxPlayers:8,questionSource:'OPENTDB',openTdbCategoryId:18,categoryId:null,difficulty:'medium',questionType:'multiple',retryAllowance:3,revealAnswers:false});
  expect(await screen.findByRole('heading',{name:'Confirmed room lobby'},{timeout:5000})).toBeInTheDocument();
},10000);
it('supports keyboard selection, Escape and outside dismissal without changing sources',async()=>{
  const {CategorySelect}=await import('../components/ui/CategorySelect');const changes=vi.fn();
  render(<CategorySelect value="" onChange={changes} options={[{id:18,name:'Science: Computers'},{id:23,name:'History'}]}/>);
  const user=userEvent.setup({delay:null}),trigger=screen.getByRole('combobox',{name:'Category'});
  await user.click(trigger);expect(screen.getByRole('listbox')).toBeInTheDocument();await user.keyboard('{End}{Enter}');expect(changes).toHaveBeenCalledWith('23');expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  await user.click(trigger);await user.keyboard('{Escape}');expect(trigger).toHaveFocus();expect(trigger).toHaveAttribute('aria-expanded','false');await user.click(trigger);await user.click(document.body);expect(trigger).toHaveAttribute('aria-expanded','false');
});
it('retries categories and keeps My Quizzes separate from Instant Quiz',async()=>{
  let requests=0;vi.stubGlobal('fetch',vi.fn(async(path:string)=>{
    if(path==='/api/bank/opentdb/categories'){requests++;return requests===1?Response.json({message:'Official categories unavailable'},{status:503}):Response.json({categories:[{id:18,name:'Science: Computers'}]});}
    if(path==='/api/quizzes')return Response.json([{id:'saved-quiz',title:'Science Quiz Night',category:'My science',questionCount:2,status:'READY',multipleChoiceCount:1,trueFalseCount:1}]);throw Error(path);
  }));
  render(<MemoryRouter><CreateRoomPage/></MemoryRouter>);const user=userEvent.setup({delay:null});
  expect(await screen.findByRole('alert')).toHaveTextContent('Official categories unavailable');expect(screen.getByRole('button',{name:'Tune the round'})).toBeDisabled();
  await user.click(screen.getByRole('button',{name:'Retry category loading'}));const category=screen.getByRole('combobox',{name:'Category'});await waitFor(()=>expect(category).toBeEnabled());await user.click(category);expect(screen.queryByRole('option',{name:/Retry browser/})).not.toBeInTheDocument();await user.keyboard('{Escape}');
  await user.click(screen.getByRole('radio',{name:'My Quizzes'}));expect(await screen.findByText('Science Quiz Night')).toBeInTheDocument();expect(screen.queryByRole('combobox',{name:'Category'})).not.toBeInTheDocument();expect(screen.queryByLabelText('Difficulty')).not.toBeInTheDocument();expect(document.body).not.toHaveTextContent('OpenTDB');
  await user.click(screen.getByRole('radio',{name:'Instant Quiz'}));await waitFor(()=>expect(screen.getByRole('combobox',{name:'Category'})).toBeEnabled());expect(screen.getByRole('combobox',{name:'Category'})).toHaveTextContent('Any Category');
});
