import { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import './AdminDashboard.css';

export default function AdminDashboard() {
  const navigate = useNavigate();
  const location = useLocation();
  const [adminInfo, setAdminInfo] = useState(null);
  const [activeMenu, setActiveMenu] = useState('dashboard');

  useEffect(() => {
    document.title = 'ShopAgent-X 管理后台';
    return () => { document.title = 'ShopAgent-X'; };
  }, []);

  useEffect(() => {
    const info = localStorage.getItem('adminInfo');
    if (!info) { navigate('/admin/login'); return; }
    setAdminInfo(JSON.parse(info));

    const path = location.pathname;
    if (path.includes('/dashboard')) setActiveMenu('dashboard');
    else if (path.includes('/products')) setActiveMenu('products');
    else if (path.includes('/orders')) setActiveMenu('orders');
    else if (path.includes('/conversations')) setActiveMenu('conversations');
    else if (path.includes('/users')) setActiveMenu('users');
    else if (path.includes('/knowledge')) setActiveMenu('knowledge');
    else if (path.includes('/logs')) setActiveMenu('logs');
    else if (path.includes('/chat')) setActiveMenu('chat');
  }, [navigate, location.pathname]);

  const handleLogout = () => {
    localStorage.removeItem('adminToken');
    localStorage.removeItem('adminInfo');
    navigate('/admin/login');
  };

  const menuItems = [
    { id: 'dashboard', label: '仪表盘', icon: '📊', path: '/admin/dashboard' },
    { id: 'products', label: '商品管理', icon: '🛒', path: '/admin/products' },
    { id: 'orders', label: '订单管理', icon: '📦', path: '/admin/orders' },
    { id: 'conversations', label: '对话管理', icon: '💬', path: '/admin/conversations' },
    { id: 'users', label: '用户管理', icon: '👥', path: '/admin/users' },
    { id: 'knowledge', label: '知识库管理', icon: '📚', path: '/admin/knowledge' },
    { id: 'logs', label: '问答日志', icon: '📝', path: '/admin/logs' },
    { id: 'chat', label: '管理助手', icon: '⚙️', path: '/admin/chat' },
  ];

  return (
    <div className="admin-layout">
      <div className="admin-sidebar">
        <div className="sidebar-header">
          <h2>🛍️ ShopAgent-X</h2>
        </div>
        <nav className="sidebar-nav">
          {menuItems.map((item) => (
            <div
              key={item.id}
              className={`nav-item ${activeMenu === item.id ? 'active' : ''}`}
              onClick={() => navigate(item.path)}
            >
              <span className="nav-icon">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
            </div>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="admin-info">
            <div className="admin-name">{adminInfo?.username}</div>
            <div className="admin-role">{adminInfo?.role}</div>
          </div>
          <button className="btn btn-danger logout-btn" onClick={handleLogout}>退出登录</button>
        </div>
      </div>
      <div className="admin-main"><Outlet /></div>
    </div>
  );
}
