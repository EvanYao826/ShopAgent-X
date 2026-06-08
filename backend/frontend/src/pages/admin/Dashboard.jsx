import React, { useEffect, useRef, useState } from 'react';
import * as echarts from 'echarts';
import { getDashboardStats } from '../../api/dashboard';
import './Dashboard.css';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const pieChartRef = useRef(null);
  const trendChartRef = useRef(null);
  const chartInstances = useRef([]);

  useEffect(() => {
    fetchStats();
    const handleResize = () => chartInstances.current.forEach(c => c.resize());
    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
      chartInstances.current.forEach(c => c.dispose());
      chartInstances.current = [];
    };
  }, []);

  useEffect(() => {
    if (!stats) return;
    const timer = setTimeout(initCharts, 150);
    return () => clearTimeout(timer);
  }, [stats]);

  const fetchStats = async () => {
    try {
      setError('');
      const res = await getDashboardStats();
      setStats(res.data || {});
    } catch (err) {
      setError(err.response?.data?.message || err.message || '仪表盘数据加载失败');
    } finally {
      setLoading(false);
    }
  };

  const initCharts = () => {
    chartInstances.current.forEach(c => c.dispose());
    chartInstances.current = [];

    // ---- 意图分布饼图 ----
    if (pieChartRef.current) {
      const pie = echarts.init(pieChartRef.current);
      const intentData = Object.entries(stats.intentDistribution || {}).map(([k, v]) => ({
        name: getIntentLabel(k), value: v
      }));
      pie.setOption({
        title: { text: '意图分布', left: 'center', top: 0, textStyle: { fontSize: 15, fontWeight: 600, color: '#1a1a2e' } },
        tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)', backgroundColor: 'rgba(0,0,0,0.75)', borderColor: 'transparent', textStyle: { color: '#fff', fontSize: 13 } },
        legend: { bottom: 0, left: 'center', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: '#666' } },
        color: ['#1890ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#eb2f96', '#13c2c2', '#f59e0b', '#2f54eb', '#8c8c8c'],
        series: [{
          name: '意图', type: 'pie', radius: ['45%', '72%'], center: ['50%', '46%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
          label: { show: false },
          emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' }, scaleSize: 10 },
          data: intentData.length > 0 ? intentData : [{ value: 0, name: '暂无数据', itemStyle: { color: '#f0f0f0' } }]
        }]
      });
      chartInstances.current.push(pie);
    }

    // ---- 7日趋势折线图 ----
    if (trendChartRef.current) {
      const line = echarts.init(trendChartRef.current);
      const trends = stats.dailyTrends || [];
      const dates = trends.map(i => i.date?.slice(5) || i.date); // MM-DD
      const counts = trends.map(i => i.count);
      line.setOption({
        title: { text: '近7日对话趋势', left: 'center', top: 0, textStyle: { fontSize: 15, fontWeight: 600, color: '#1a1a2e' } },
        tooltip: { trigger: 'axis', backgroundColor: 'rgba(0,0,0,0.75)', borderColor: 'transparent', textStyle: { color: '#fff', fontSize: 13 } },
        grid: { left: '3%', right: '6%', top: '40px', bottom: '8%', containLabel: true },
        xAxis: {
          type: 'category', boundaryGap: false,
          data: dates.length > 0 ? dates : ['暂无'],
          axisLine: { lineStyle: { color: '#e8e8e8' } },
          axisLabel: { color: '#8c8c8c', fontSize: 11 }
        },
        yAxis: {
          type: 'value', minInterval: 1,
          splitLine: { lineStyle: { color: '#f5f5f5', type: 'dashed' } },
          axisLabel: { color: '#8c8c8c', fontSize: 11 }
        },
        series: [{
          name: '对话数', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
          lineStyle: { color: '#1890ff', width: 3 },
          itemStyle: { color: '#1890ff', borderColor: '#fff', borderWidth: 2 },
          areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [
            { offset: 0, color: 'rgba(24,144,255,0.35)' },
            { offset: 1, color: 'rgba(24,144,255,0.02)' }
          ])},
          data: dates.length > 0 ? counts : [0]
        }]
      });
      chartInstances.current.push(line);
    }
  };

  const getIntentLabel = (intent) => {
    const m = { shopping: '商品导购', chitchat: '闲聊', knowledge_qa: '知识问答', product_search: '商品搜索', product_compare: '商品对比', cart: '购物车', admin_copilot: '管理助手', knowledge_inspection: '知识巡检', reasoning: '复杂推理', unknown: '未知' };
    return m[intent] || intent;
  };
  const getIntentColor = (intent) => {
    const m = { shopping: '#1890ff', chitchat: '#52c41a', knowledge_qa: '#faad14', product_search: '#13c2c2', product_compare: '#722ed1', cart: '#eb2f96', admin_copilot: '#8b5cf6', knowledge_inspection: '#f59e0b', reasoning: '#2f54eb', unknown: '#8c8c8c' };
    return m[intent] || '#8c8c8c';
  };

  if (loading) return <div className="loading" style={{textAlign:'center',padding:60,color:'#999'}}>加载中...</div>;
  if (error) return <div className="error" style={{textAlign:'center',padding:60,color:'#ff4d4f'}}>{error}</div>;
  if (!stats) return <div className="error" style={{textAlign:'center',padding:60,color:'#999'}}>暂无数据</div>;

  return (
    <div className="dashboard-container">
      <h2 className="page-title">📊 电商导购数据大屏</h2>

      {/* ─── 核心指标 ─── */}
      <div className="stats-cards">
        <div className="stat-card">
          <div className="stat-icon">💬</div>
          <div className="stat-value">{stats.todayConversations || 0}</div>
          <div className="stat-label">今日对话数</div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">🎯</div>
          <div className="stat-value">{stats.todayRecommendations || 0}</div>
          <div className="stat-label">今日推荐次数</div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">👆</div>
          <div className="stat-value">{(stats.clickRate || 0).toFixed(1)}<span style={{fontSize:18,color:'#8c8c8c'}}>%</span></div>
          <div className="stat-label">推荐点击率</div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">😊</div>
          <div className="stat-value">{(stats.satisfactionRate || 0).toFixed(1)}<span style={{fontSize:18,color:'#8c8c8c'}}>%</span></div>
          <div className="stat-label">用户满意度</div>
        </div>
      </div>

      {/* ─── 图表 ─── */}
      <div className="charts-row">
        <div className="chart-container"><div ref={pieChartRef} className="chart-canvas" /></div>
        <div className="chart-container"><div ref={trendChartRef} className="chart-canvas" /></div>
      </div>

      {/* ─── 表格 ─── */}
      <div className="dashboard-grid">
        <div className="dashboard-section">
          <h3>🔥 热门商品 TOP5</h3>
          <table className="data-table">
            <thead><tr><th>商品名称</th><th>品牌</th><th>价格</th><th>销量</th><th>评分</th></tr></thead>
            <tbody>
              {stats.hotProducts?.length > 0 ? stats.hotProducts.map((p, i) => (
                <tr key={i}>
                  <td>
                    <span className="product-name-cell">
                      <span className={`rank-badge ${i < 3 ? `rank-${i+1}` : 'rank-other'}`}>{i+1}</span>
                      <span className="text-truncate" title={p.title}>{p.title}</span>
                    </span>
                  </td>
                  <td style={{color:'#666'}}>{p.brand || '-'}</td>
                  <td style={{color:'#ff4d4f',fontWeight:600}}>¥{p.base_price || 0}</td>
                  <td><span className="sales-count">{p.sales_count || 0}</span></td>
                  <td><span className="rating-stars">{'★'.repeat(Math.round(p.rating || 0))}{'☆'.repeat(5 - Math.round(p.rating || 0))}</span></td>
                </tr>
              )) : <tr><td colSpan={5} className="empty-text">暂无热门商品</td></tr>}
            </tbody>
          </table>
        </div>

        <div className="dashboard-section">
          <h3>📋 最近推荐记录</h3>
          <table className="data-table">
            <thead><tr><th>查询内容</th><th>意图</th><th>点击</th><th>反馈</th></tr></thead>
            <tbody>
              {stats.recentRecommendations?.length > 0 ? stats.recentRecommendations.map((rec, i) => (
                <tr key={rec.id || i}>
                  <td className="text-truncate" title={rec.query}>{rec.query || '-'}</td>
                  <td><span className="intent-badge" style={{background:`${getIntentColor(rec.intent)}18`,color:getIntentColor(rec.intent)}}>{getIntentLabel(rec.intent)}</span></td>
                  <td><span className={`click-status ${rec.userClicked ? 'click-yes' : 'click-no'}`}>{rec.userClicked ? '✅ 是' : '— 否'}</span></td>
                  <td>
                    {rec.userFeedback === 1 ? <span className="feedback-tag feedback-good">👍 满意</span>
                    : rec.userFeedback === 0 ? <span className="feedback-tag feedback-bad">👎 不满</span>
                    : <span className="feedback-none">未反馈</span>}
                  </td>
                </tr>
              )) : <tr><td colSpan={4} className="empty-text">暂无推荐记录</td></tr>}
            </tbody>
          </table>
        </div>

        <div className="dashboard-section">
          <h3>❓ 未命中问题</h3>
          <table className="data-table">
            <thead><tr><th>问题内容</th><th>频次</th></tr></thead>
            <tbody>
              {stats.unansweredQuestions?.length > 0 ? stats.unansweredQuestions.map((q, i) => (
                <tr key={i}>
                  <td className="text-truncate" title={q.question} style={{color:'#333'}}>{q.question}</td>
                  <td><span style={{display:'inline-block',background:'#fff1f0',color:'#ff4d4f',padding:'2px 10px',borderRadius:10,fontSize:12,fontWeight:600}}>{q.count} 次</span></td>
                </tr>
              )) : <tr><td colSpan={2} className="empty-text">🎉 暂无未命中问题</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
