import { useState, useEffect } from 'react';
import { conversationManagementAPI } from '../../api/admin';
import './TablePage.css';

export default function ConversationManagement() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState(null);
  const [expanded, setExpanded] = useState(null);
  const [messages, setMessages] = useState([]);
  const [filters, setFilters] = useState({ userId: '', keyword: '' });

  useEffect(() => { fetchData(); fetchStats(); }, [page]);

  const fetchData = async () => {
    try {
      setLoading(true);
      const params = { page, size: 10, ...Object.fromEntries(Object.entries(filters).filter(([,v]) => v)) };
      const res = await conversationManagementAPI.list(params);
      setData(res.data.records || []);
      setTotal(res.data.total || 0);
    } catch (err) { alert(err.message); }
    finally { setLoading(false); }
  };

  const fetchStats = async () => {
    try { const res = await conversationManagementAPI.stats(); setStats(res.data); } catch (_) {}
  };

  const toggleMessages = async (id) => {
    if (expanded === id) { setExpanded(null); return; }
    try {
      const res = await conversationManagementAPI.messages(id);
      setMessages(res.data || []);
      setExpanded(id);
    } catch (err) { alert(err.message); }
  };

  const handleDelete = async (id) => {
    if (!confirm('确定删除此对话？')) return;
    try { await conversationManagementAPI.delete(id); fetchData(); } catch (err) { alert(err.message); }
  };

  const pages = Math.ceil(total / 10);
  const [jumpPage, setJumpPage] = useState('');

  const handleJump = () => {
    const num = parseInt(jumpPage, 10);
    if (num >= 1 && num <= pages) { setPage(num); setJumpPage(''); }
  };

  return (
    <div className="table-page">
      <h2>对话管理</h2>
      {stats && (
        <div className="stats-row">
          <span>总对话: <b>{stats.totalConversations}</b></span>
          <span>总消息: <b>{stats.totalMessages}</b></span>
          <span>今日: <b>{stats.todayConversations}</b></span>
        </div>
      )}
      <div className="filter-bar">
        <input placeholder="用户ID" value={filters.userId} onChange={e => setFilters({...filters, userId: e.target.value})} style={{width:100}} />
        <input placeholder="搜索标题" value={filters.keyword} onChange={e => setFilters({...filters, keyword: e.target.value})} />
        <button onClick={() => { setPage(1); fetchData(); }}>搜索</button>
      </div>
      <table className="data-table">
        <thead><tr><th>ID</th><th>用户ID</th><th>标题</th><th>消息数</th><th>创建时间</th><th>操作</th></tr></thead>
        <tbody>
          {loading ? <tr><td colSpan="6">加载中...</td></tr> :
           data.length === 0 ? <tr><td colSpan="6">暂无数据</td></tr> :
           data.map(c => [
             <tr key={c.id} className="clickable" onClick={() => toggleMessages(c.id)}>
               <td>{c.id}</td><td>{c.userId}</td><td>{c.title || '-'}</td>
               <td>{c.messageCount || 0}</td><td>{c.createTime}</td>
               <td>
                 <button className="btn-sm danger" onClick={e => { e.stopPropagation(); handleDelete(c.id); }}>删除</button>
               </td>
             </tr>,
             expanded === c.id && (
               <tr key={`msg-${c.id}`}>
                 <td colSpan="6" className="msg-panel">
                   <div className="msg-list">
                     {messages.map(m => (
                       <div key={m.id} className={`msg-item ${m.role === 'user' ? 'msg-user' : 'msg-ai'}`}>
                         <span className="msg-role">{m.role === 'user' ? '用户' : 'AI'}</span>
                         <span className="msg-content">{m.content}</span>
                         <span className="msg-time">{m.createTime}</span>
                       </div>
                     ))}
                   </div>
                 </td>
               </tr>
             )
           ])}
        </tbody>
      </table>
      {pages > 1 && (
        <div className="pagination">
          <button disabled={page<=1} onClick={()=>setPage(page-1)}>上一页</button>
          <span>第{page}/{pages}页(共{total}条)</span>
          <button disabled={page>=pages} onClick={()=>setPage(page+1)}>下一页</button>
          <span className="jump-box">
            跳至<input type="number" min={1} max={pages} value={jumpPage} onChange={e => setJumpPage(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleJump()} placeholder="页码" />页
            <button onClick={handleJump}>GO</button>
          </span>
        </div>
      )}
    </div>
  );
}
