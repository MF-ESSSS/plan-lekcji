const headers={'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'};
const json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers});
let schema;
export default {
 async fetch(request,env){
  const path=new URL(request.url).pathname;
  if(!['/register','/stats'].includes(path))return json({error:'not_found'},404);
  try{
   if(!env.DB)return json({error:'database_unavailable'},503);
   if(!schema)schema=env.DB.exec('CREATE TABLE IF NOT EXISTS installations (id TEXT PRIMARY KEY, created_at TEXT NOT NULL) WITHOUT ROWID').catch(error=>{schema=null;throw error;});
   await schema;
   if(path==='/register'&&request.method==='POST'){
    if(Number(request.headers.get('content-length')||0)>256)return json({error:'too_large'},413);
    const raw=await request.text();if(raw.length>256)return json({error:'too_large'},413);
    let body;try{body=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
    if(typeof body.id!=='string'||!/^\b[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b$/i.test(body.id))return json({error:'invalid_id'},400);
    await env.DB.prepare('INSERT OR IGNORE INTO installations (id, created_at) VALUES (?, ?)').bind(body.id.toLowerCase(),new Date().toISOString()).run();
    return json({ok:true});
   }
   if(path==='/stats'&&request.method==='GET'){
    const row=await env.DB.prepare('SELECT COUNT(*) AS installations FROM installations').first();
    return json({installations:Number(row.installations),updatedAt:new Date().toISOString()});
   }
   return json({error:'method_not_allowed'},405);
  }catch{return json({error:'temporarily_unavailable'},503);}
 }
};
