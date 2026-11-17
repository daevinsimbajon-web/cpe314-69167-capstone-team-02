import {useCallback,useReducer} from 'react';
import type {QuizQuestion} from './quizzes';

export type Editable=QuizQuestion&{key:string};
type Snapshot={questions:Editable[];activeKey:string|null};
type History={past:Snapshot[];present:Snapshot;future:Snapshot[];group?:string};
type Action={type:'edit';update:(snapshot:Snapshot)=>Snapshot;group?:string}|{type:'select';key:string|null}|{type:'reset';questions:Editable[]}|{type:'undo'|'redo'|'endGroup'};
function reduce(state:History,action:Action):History{
  switch(action.type){
    case 'reset':return {past:[],present:{questions:action.questions,activeKey:null},future:[]};
    case 'select':return {...state,present:{...state.present,activeKey:action.key},group:undefined};
    case 'endGroup':return state.group?{...state,group:undefined}:state;
    case 'edit':{
      const next=action.update(state.present);
      if(JSON.stringify(next)===JSON.stringify(state.present))return state;
      return {past:action.group&&action.group===state.group?state.past:[...state.past,state.present].slice(-100),present:next,future:[],group:action.group};
    }
    case 'undo':return state.past.length?{past:state.past.slice(0,-1),present:state.past[state.past.length-1],future:[state.present,...state.future]}:state;
    case 'redo':return state.future.length?{past:[...state.past,state.present].slice(-100),present:state.future[0],future:state.future.slice(1)}:state;
  }
}
/** Immutable question snapshots include selection and answer indices; typing is grouped per field focus. */
export function useQuestionHistory(initial:()=>Editable[]){
  const [state,dispatch]=useReducer(reduce,initial,make=>({past:[],present:{questions:make(),activeKey:null},future:[]}));
  const edit=useCallback((update:(snapshot:Snapshot)=>Snapshot,group?:string)=>dispatch({type:'edit',update,group}),[]);
  const select=useCallback((key:string|null)=>dispatch({type:'select',key}),[]);
  const reset=useCallback((questions:Editable[])=>dispatch({type:'reset',questions}),[]);
  const undo=useCallback(()=>dispatch({type:'undo'}),[]),redo=useCallback(()=>dispatch({type:'redo'}),[]),endGroup=useCallback(()=>dispatch({type:'endGroup'}),[]);
  return {...state.present,edit,select,reset,undo,redo,endGroup,canUndo:state.past.length>0,canRedo:state.future.length>0};
}
