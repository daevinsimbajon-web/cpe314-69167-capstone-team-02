export interface QuizQuestion {
  type:'MULTIPLE_CHOICE'|'TRUE_FALSE'; text:string; options:string[];
  correctIndex:number|null;
}
export interface QuizCategory {id:string;name:string;parentId?:string|null}
export function categoryPath(category:QuizCategory,categories:QuizCategory[],visited=new Set<string>()):string{
  if(visited.has(category.id))return category.name;
  visited.add(category.id);
  const parent=categories.find(c=>c.id===category.parentId);
  return parent?`${categoryPath(parent,categories,visited)} / ${category.name}`:category.name;
}
export interface QuizSummary {
  id:string; title:string; description:string; categoryId:string|null; category:string;
  visibility:'PRIVATE'; status:'DRAFT'|'READY'; version:number; questionCount:number;
  multipleChoiceCount:number; trueFalseCount:number; createdAt:string; updatedAt:string;
}
export interface QuizDetail {quiz:QuizSummary;questions:QuizQuestion[]}
export interface LibraryQuestion {id:string;category:string;question:QuizQuestion}
export function composition(quiz:Pick<QuizSummary,'multipleChoiceCount'|'trueFalseCount'>){return quiz.multipleChoiceCount&&quiz.trueFalseCount?'Mixed':quiz.trueFalseCount?'True / False':quiz.multipleChoiceCount?'Multiple Choice':'No questions yet';}
export function blankQuestion(type:QuizQuestion['type']='MULTIPLE_CHOICE'):QuizQuestion{return {type,text:'',options:type==='TRUE_FALSE'?['True','False']:['','','',''],correctIndex:null};}
