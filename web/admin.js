'use strict';
(()=>{
 const $=id=>document.getElementById(id), state={csrf:'',offset:0,rows:[]};
 const info=s=>{$('message').textContent=s||'';};
 const mb=n=>(Number(n||0)/1048576).toFixed(2)+' MB';
 const enc=values=>new URLSearchParams(values);
 async function request(path,data){
   const opts={credentials:'same-origin',headers:{Accept:'application/json'}};
   if(data){opts.method='POST';opts.headers['Content-Type']='application/x-www-form-urlencoded';if(state.csrf)opts.headers['X-MiniSocial-CSRF']=state.csrf;opts.body=enc(data).toString();}
   const response=await fetch('/admin/api/'+path,opts);let r={};try{r=await response.json();}catch{}
   if(!response.ok)throw new Error(r.error||'HTTP '+response.status);
   return r;
 }
 function visible(logged){$('login-panel').classList.toggle('hidden',logged);$('dashboard').classList.toggle('hidden',!logged);$('logout').classList.toggle('hidden',!logged);}
 async function startup(){try{const s=await request('session');state.csrf=s.csrf;visible(true);await refresh();}catch{state.csrf='';visible(false);}}
 async function refresh(){
   info('');let q=$('query').value.trim().toLowerCase();let res=await request('data?'+enc({q,offset:state.offset}));
   state.rows=res.users||[];let l=res.limits||{};
   $('used').textContent=mb(l.used_bytes);$('limit').textContent=mb(l.total_limit_bytes);
   $('percent').textContent=(Number(l.total_limit_bytes)>0?100*Number(l.used_bytes)/Number(l.total_limit_bytes):0).toFixed(1)+'%';
   $('total-limit').value=Math.round(Number(l.total_limit_bytes||0)/1048576);
   $('registration-enabled').checked=!!l.registration_enabled;
   $('list-caption').textContent='Hiển thị '+state.rows.length+' tài khoản; trang '+(Math.floor(state.offset/50)+1);
   $('page').textContent=String(Math.floor(state.offset/50)+1);
   $('previous').disabled=state.offset===0;$('next').disabled=state.rows.length<50;
   const tbody=$('user-list');tbody.replaceChildren();
   for(const u of state.rows){
     let tr=document.createElement('tr');
     function cell(t){let td=document.createElement('td');td.textContent=t;tr.appendChild(td);return td;}
     cell('@'+u.handle);cell(u.display_name||'Chưa thiết lập');
     let status=cell(u.is_suspended?'Đã khóa':'Hoạt động');status.className=u.is_suspended?'pill off':'pill';
     cell(mb(u.storage_used_bytes));cell(mb(u.storage_limit_bytes));
     let td=cell('');td.className='actions';
     function btn(title,cls,fn){let b=document.createElement('button');b.type='button';b.className=cls;b.textContent=title;b.addEventListener('click',()=>action(fn));td.appendChild(b);}
     btn(u.is_suspended?'Mở khóa':'Khóa',u.is_suspended?'secondary':'warning',async()=>{
       await request('suspend',{id:u.id,suspended:String(!u.is_suspended)});info('Đã cập nhật trạng thái '+u.handle);
     });
     btn('Đặt lại MK','secondary',async()=>{
       const pw=prompt('Mật khẩu mới cho '+u.handle+' (ít nhất 8 ký tự)');if(pw===null)return;
       if(pw.length<8||pw.length>128)throw new Error('Mật khẩu phải dài 8-128 ký tự');
       if(!confirm('Đặt lại mật khẩu @'+u.handle+'? Các phiên đăng nhập trước sẽ bị vô hiệu hóa.'))return;
       await request('reset-password',{id:u.id,password:pw});info('Đã đặt lại mật khẩu '+u.handle);
     });
     btn('Hạn mức','secondary',async()=>{
       let val=prompt('Hạn mức lưu trữ (MB) của '+u.handle,Math.round(Number(u.storage_limit_bytes)/1048576));if(val===null)return;
       if(!/^\d{1,7}$/.test(val))throw new Error('Giới hạn không hợp lệ');
       await request('account-quota',{id:u.id,bytes:String(Number(val)*1048576)});info('Đã cập nhật hạn mức '+u.handle);
     });
     btn('Xóa','danger',async()=>{
       if(prompt('XÓA VĨNH VIỄN @'+u.handle+' và dữ liệu. Gõ DELETE để xác nhận:')!=='DELETE')return;
       await request('delete',{id:u.id,confirm:'DELETE'});info('Đã xóa '+u.handle);
     });
     tbody.appendChild(tr);
   }
 }
 async function action(fn){try{await fn();await refresh();}catch(e){info(e.message||String(e));}}
 $('login-form').addEventListener('submit',async e=>{e.preventDefault();try{await request('login',{username:$('username').value,password:$('password').value});$('password').value='';await startup();}catch(err){info(err.message);}});
 $('logout').addEventListener('click',async()=>{try{await request('logout',{});}catch{}state.csrf='';visible(false);info('Đã đăng xuất');});
 $('total-form').addEventListener('submit',e=>{e.preventDefault();action(async()=>{let n=Number($('total-limit').value);if(!Number.isInteger(n)||n<1||n>953674)throw Error('Hạn mức không hợp lệ');await request('global-quota',{bytes:String(n*1048576)});info('Đã lưu giới hạn toàn hệ thống');});});
 $('save-registration').addEventListener('click',()=>action(async()=>{await request('registration',{enabled:String($('registration-enabled').checked)});info('Đã cập nhật chế độ đăng ký');}));
 $('search-form').addEventListener('submit',e=>{e.preventDefault();state.offset=0;action(async()=>{});});
 $('reload').addEventListener('click',()=>action(async()=>{}));
 $('previous').addEventListener('click',()=>{state.offset=Math.max(0,state.offset-50);action(async()=>{});});
 $('next').addEventListener('click',()=>{state.offset+=50;action(async()=>{});});
 startup();
})();
