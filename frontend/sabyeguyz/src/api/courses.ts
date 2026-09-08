export interface Course { id:number; code:string; name:string; price:number; sessions:number; bonusSessions:number; validityDays:number; active:boolean }
export type CoursePayload = Omit<Course,'id'|'active'>
const base=import.meta.env.VITE_API_BASE_URL??'http://localhost:8080'
import { authHeaders } from './auth'
async function request<T>(url:string,options?:RequestInit):Promise<T>{const response=await fetch(`${base}${url}`,{headers:{'Content-Type':'application/json',...authHeaders()},...options});if(!response.ok)throw new Error((await response.json().catch(()=>({}))).message||'ไม่สามารถดำเนินการกับข้อมูล Course ได้');return response.status===204?undefined as T:response.json()}
export const listCourses=()=>request<Course[]>('/api/courses'); export const createCourse=(p:CoursePayload)=>request<Course>('/api/courses',{method:'POST',body:JSON.stringify(p)}); export const updateCourse=(id:number,p:CoursePayload)=>request<Course>(`/api/courses/${id}`,{method:'PUT',body:JSON.stringify(p)}); export const deactivateCourse=(id:number)=>request<void>(`/api/courses/${id}`,{method:'DELETE'})
