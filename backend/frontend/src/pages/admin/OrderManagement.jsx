import { useState, useEffect } from 'react';
import { orderManagementAPI } from '../../api/admin';
import './TablePage.css';

const STATUS_MAP = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已取消' };

export default function OrderManagement() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState(null);
  const [expanded, setExpanded] = useState(null);
  const [items, setItems] = useState([]);
  const [filters, setFilters] = useState({ userId: '', status: '', orderNo: '' });

  useEffect(() => { fetchData(); fetchStats(); }, [page]);

  const fetchData = async () => {
    try {
      setLoading(true);
      const params = { page, size: 20, ...Object.fromEntries(Object.entries(filters).filter(([,v]) => v)) };
      const res = await orderManagementAPI.list(params);
      setData(res.data.records || []);
      setTotal(res.data.total || 0);
    } catch (err) { alert(err.message); }
    finally { setLoading(false); }
  };

  const fetchStats = async () => {
    try { const res = await orderManagementAPI.stats(); setStats(res.data); } catch (_) {}
  };

  const toggleItems = async (id) => {
    if (expanded === id) { setExpanded(null); return; }
    try {
      const res = await orderManagementAPI.items(id);
      setItems(res.data || []);
      setExpanded(id);
    } catch (err) { alert(err.message); }
  };

  const pages = Math.ceil(total / 20);

  return (
    <div className="table-page">
      <h2>订单管理</h2>
      {stats && (
        <div className="stats-row">
          <span>总订单: <b>{stats.totalOrders}</b></span>
          <span>待付款: <b className="orange">{stats.pendingPayment}</b></span>
          <span>待发货: <b className="blue">{stats.pendingDelivery}</b></span>
          <span>已完成: <b className="green">{stats.completed}</b></span>
          <span>总销售额: <b className="red">¥{(stats.totalRevenue || 0).toFixed(2)}</b></span>
        </div>
      )}
      <div className="filter-bar">
        <input placeholder="用户ID" value={filters.userId} onChange={e => setFilters({...filters, userId: e.target.value})} style={{width:100}} />
        <select value={filters.status} onChange={e => setFilters({...filters, status: e.target.value})}>
          <option value="">全部状态</option>
          {Object.entries(STATUS_MAP).map(([k,v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <input placeholder="订单号" value={filters.orderNo} onChange={e => setFilters({...filters, orderNo: e.target.value})} style={{width:160}} />
        <button onClick={() => { setPage(1); fetchData(); }}>搜索</button>
      </div>
      <table className="data-table">
        <thead><tr><th>ID</th><th>订单号</th><th>用户</th><th>金额</th><th>状态</th><th>收货人</th><th>时间</th></tr></thead>
        <tbody>
          {loading ? <tr><td colSpan="7">加载中...</td></tr> :
           data.length === 0 ? <tr><td colSpan="7">暂无数据</td></tr> :
           data.map(o => [
             <tr key={o.id} className="clickable" onClick={() => toggleItems(o.id)}>
               <td>{o.id}</td><td>{o.orderNo}</td><td>{o.userId}</td>
               <td>¥{o.payAmount || 0}</td>
               <td><span className={`status-badge status-${o.status}`}>{STATUS_MAP[o.status] || '未知'}</span></td>
               <td>{o.receiverName || '-'}</td><td>{o.createTime}</td>
             </tr>,
             expanded === o.id && (
               <tr key={`item-${o.id}`}>
                 <td colSpan="7" className="msg-panel">
                   <div className="msg-list">
                     <div className="order-info">
                       <span>收货人: {o.receiverName} | {o.receiverPhone}</span>
                       <span>地址: {o.receiverAddress}</span>
                       {o.remark && <span>备注: {o.remark}</span>}
                     </div>
                     {items.map(i => (
                       <div key={i.id} className="msg-item">
                         <span>{i.productTitle}</span>
                         <span>¥{i.price} x {i.quantity} = ¥{i.totalAmount}</span>
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
        </div>
      )}
    </div>
  );
}
