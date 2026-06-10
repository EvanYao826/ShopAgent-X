import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminAuthAPI } from '../../api/admin';
import '../Auth.css';

export default function AdminLogin() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    username: '',
    password: ''
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await adminAuthAPI.login(formData.username, formData.password);
      localStorage.setItem('adminToken', response.data.accessToken);
      localStorage.setItem('adminInfo', JSON.stringify(response.data.admin));
      if (response.data.admin && response.data.admin.id) {
        localStorage.setItem('adminId', response.data.admin.id);
      }
      navigate('/admin', { replace: true });
      setTimeout(() => {
        window.history.replaceState(null, null, window.location.href);
      }, 100);
    } catch (err) {
      setError(err.message || '登录失败，请检查用户名和密码');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      {/* 两侧漂浮商品图标 */}
      <div className="auth-float-icons">
        <span className="float-icon">👜</span>
        <span className="float-icon">👟</span>
        <span className="float-icon">💄</span>
        <span className="float-icon">📱</span>
        <span className="float-icon">🧴</span>
        <span className="float-icon">👗</span>
        <span className="float-icon">🎧</span>
        <span className="float-icon">⌚</span>
        <span className="float-icon">🧣</span>
        <span className="float-icon">☕</span>
      </div>

      <div className="auth-card">
        {/* 品牌区域：图标 + 标题水平对齐 */}
        <div className="auth-brand">
          <span className="auth-brand-icon">🛒</span>
          <h1 className="auth-title">ShopAgent-X</h1>
        </div>
        <p className="auth-subtitle">AI 智能导购运营后台</p>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>用户名</label>
            <input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              placeholder="请输入管理员用户名"
              required
            />
          </div>

          <div className="form-group">
            <label>密码</label>
            <input
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              placeholder="请输入密码"
              required
            />
          </div>

          {error && <div className="error">{error}</div>}

          <button type="submit" className="auth-btn" disabled={loading}>
            {loading ? '登录中...' : '登录'}
          </button>
        </form>

        <div className="auth-footer">
          <a href="/login">返回普通用户登录</a>
        </div>
      </div>
    </div>
  );
}
