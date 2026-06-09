import { useState, useEffect } from 'react';
import { productManagementAPI } from '../../api/admin';
import './ProductManagement.css';

const STATUS_MAP = { 0: '已下架', 1: '在售' };

// 构建商品图片URL，处理中文路径编码
const buildImageUrl = (imageUrl) => {
  if (!imageUrl) return null;
  if (imageUrl.startsWith('http')) return imageUrl;
  const relPath = imageUrl.startsWith('/product-images/') ? imageUrl.slice(16) : imageUrl;
  const encoded = relPath.split('/').map(seg => {
    try { return encodeURIComponent(seg); } catch { return seg; }
  }).join('/');
  return `/product-images/${encoded}`;
};

// 提取简洁商品名称（去掉营销描述后缀）
const getShortName = (title) => {
  if (!title) return '-';
  // 截取前15个字符作为简洁名称
  return title.length > 15 ? title.slice(0, 15) + '...' : title;
};

export default function ProductManagement() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState(null);
  const [brands, setBrands] = useState([]);

  // 筛选条件
  const [filters, setFilters] = useState({
    keyword: '', categoryId: '', brand: '', status: '', minPrice: '', maxPrice: '', sortBy: 'id', sortOrder: 'desc'
  });

  useEffect(() => { fetchProducts(); fetchStats(); fetchBrands(); }, [page]);

  const fetchProducts = async () => {
    try {
      setLoading(true);
      const params = { page, size: 10, ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '')) };
      const res = await productManagementAPI.list(params);
      setProducts(res.data.records || []);
      setTotal(res.data.total || 0);
    } catch (err) {
      setError(err.message);
    } finally { setLoading(false); }
  };

  const fetchStats = async () => {
    try { const res = await productManagementAPI.stats(); setStats(res.data); } catch (_) {}
  };

  const fetchBrands = async () => {
    try { const res = await productManagementAPI.brands(); setBrands(res.data || []); } catch (_) {}
  };

  const handleSearch = () => { setPage(1); fetchProducts(); };

  const handleStatusToggle = async (id, currentStatus) => {
    const newStatus = currentStatus === 1 ? 0 : 1;
    try {
      await productManagementAPI.updateStatus(id, newStatus);
      fetchProducts();
      fetchStats();
    } catch (err) { alert(err.message); }
  };

  const handleFilterChange = (key, value) => {
    setFilters(prev => ({ ...prev, [key]: value }));
  };

  const pages = Math.ceil(total / 10);
  const [jumpPage, setJumpPage] = useState('');

  const handleJump = () => {
    const num = parseInt(jumpPage, 10);
    if (num >= 1 && num <= pages) { setPage(num); setJumpPage(''); }
  };

  return (
    <div className="product-management">
      <h2>商品管理</h2>

      {stats && (
        <div className="product-stats">
          <div className="stat-item"><span className="stat-num">{stats.total}</span><span>总商品</span></div>
          <div className="stat-item"><span className="stat-num green">{stats.online}</span><span>在售</span></div>
          <div className="stat-item"><span className="stat-num gray">{stats.offline}</span><span>已下架</span></div>
          <div className="stat-item"><span className="stat-num orange">{stats.lowStock || 0}</span><span>低库存</span></div>
          <div className="stat-item"><span className="stat-num red">{stats.outOfStock || 0}</span><span>售罄</span></div>
        </div>
      )}

      <div className="filter-bar">
        <input placeholder="搜索商品名/品牌/标签..." value={filters.keyword} onChange={e => handleFilterChange('keyword', e.target.value)} />
        <select value={filters.brand} onChange={e => handleFilterChange('brand', e.target.value)}>
          <option value="">全部品牌</option>
          {brands.map(b => <option key={b} value={b}>{b}</option>)}
        </select>
        <select value={filters.status} onChange={e => handleFilterChange('status', e.target.value)}>
          <option value="">全部状态</option>
          <option value="1">在售</option>
          <option value="0">已下架</option>
        </select>
        <input placeholder="最低价" type="number" value={filters.minPrice} onChange={e => handleFilterChange('minPrice', e.target.value)} style={{width:80}} />
        <span>-</span>
        <input placeholder="最高价" type="number" value={filters.maxPrice} onChange={e => handleFilterChange('maxPrice', e.target.value)} style={{width:80}} />
        <button onClick={handleSearch}>搜索</button>
      </div>

      {error && <div className="error-msg">{error}</div>}

      <table className="data-table">
        <thead>
          <tr>
            <th>ID</th><th>图片</th><th>商品名称</th><th>品牌</th><th>价格</th><th>库存</th><th>销量</th><th>评分</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr><td colSpan="10" className="loading-text">加载中...</td></tr>
          ) : products.length === 0 ? (
            <tr><td colSpan="10" className="empty-text">暂无商品</td></tr>
          ) : products.map(p => (
            <tr key={p.id}>
              <td>{p.id}</td>
              <td>{buildImageUrl(p.imageUrl) ? <img src={buildImageUrl(p.imageUrl)} alt={p.title} className="product-thumb" onError={e => { e.target.style.display='none'; }} /> : <span className="no-image">无图</span>}</td>
              <td className="text-truncate" title={p.title}>{getShortName(p.title)}</td>
              <td>{p.brand || '-'}</td>
              <td>¥{p.basePrice || 0}</td>
              <td>{p.stock ?? '-'}</td>
              <td>{p.salesCount || 0}</td>
              <td>{p.rating || 0}</td>
              <td><span className={`status-badge status-${p.status}`}>{STATUS_MAP[p.status] || '未知'}</span></td>
              <td>
                <button className="btn-sm" onClick={() => handleStatusToggle(p.id, p.status)}>
                  {p.status === 1 ? '下架' : '上架'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {pages > 1 && (
        <div className="pagination">
          <button disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
          <span>第 {page} / {pages} 页 (共 {total} 条)</span>
          <button disabled={page >= pages} onClick={() => setPage(page + 1)}>下一页</button>
          <span className="jump-box">
            跳至<input type="number" min={1} max={pages} value={jumpPage} onChange={e => setJumpPage(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleJump()} placeholder="页码" />页
            <button onClick={handleJump}>GO</button>
          </span>
        </div>
      )}
    </div>
  );
}
