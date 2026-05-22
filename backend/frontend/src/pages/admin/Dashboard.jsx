import React, { useEffect, useState, useRef } from 'react';
import { getDashboardStats } from '../../api/dashboard';
import * as echarts from 'echarts';
import './Dashboard.css';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const pieChartRef = useRef(null);
  const trendChartRef = useRef(null);
  const chartInstances = useRef([]);

  useEffect(() => {
    fetchStats();
    
    const handleResize = () => {
      chartInstances.current.forEach(chart => chart.resize());
    };
    window.addEventListener('resize', handleResize);
    
    return () => {
      window.removeEventListener('resize', handleResize);
      chartInstances.current.forEach(chart => chart.dispose());
    };
  }, []);

  useEffect(() => {
    if (stats) {
      setTimeout(initCharts, 100);
    }
  }, [stats]);

  const fetchStats = async () => {
    try {
      const res = await getDashboardStats();
      if (res.code === 200) {
        setStats(res.data);
      }
    } catch (error) {
      console.error('Fetch stats failed', error);
    } finally {
      setLoading(false);
    }
  };

  const initCharts = () => {
    // Clean up old instances
    chartInstances.current.forEach(chart => chart.dispose());
    chartInstances.current = [];

    // 1. 意图分布饼图
    if (pieChartRef.current && stats.intentDistribution) {
        const pieChart = echarts.init(pieChartRef.current);
        const intentData = Object.entries(stats.intentDistribution || {}).map(([key, value]) => ({
            name: getIntentLabel(key),
            value: value
        }));
        
        const optionPie = {
            title: {
                text: '意图分布',
                left: 'center',
                textStyle: { fontSize: 16 }
            },
            tooltip: {
                trigger: 'item',
                formatter: '{b}: {c} ({d}%)'
            },
            legend: {
                bottom: '0%',
                left: 'center'
            },
            color: ['#1890ff', '#52c41a', '#faad14', '#ff4d4f'],
            series: [
                {
                    name: '意图',
                    type: 'pie',
                    radius: ['30%', '60%'],
                    center: ['50%', '50%'],
                    itemStyle: {
                        borderRadius: 8,
                        shadowBlur: 20,
                        shadowColor: 'rgba(0, 0, 0, 0.3)'
                    },
                    data: intentData.length > 0 ? intentData : [{ value: 0, name: '暂无数据' }]
                }
            ]
        };
        pieChart.setOption(optionPie);
        chartInstances.current.push(pieChart);
    }

    // 2. 7日对话趋势图
    if (trendChartRef.current) {
        const trendChart = echarts.init(trendChartRef.current);
        const trends = stats.dailyTrends || [];
        const dates = trends.map(item => item.date);
        const counts = trends.map(item => item.count);

        const optionTrend = {
            title: {
                text: '近7日对话趋势',
                left: 'center',
                textStyle: { fontSize: 16 }
            },
            tooltip: {
                trigger: 'axis'
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: '10%',
                containLabel: true
            },
            xAxis: {
                type: 'category',
                boundaryGap: false,
                data: dates.length > 0 ? dates : ['无数据']
            },
            yAxis: {
                type: 'value'
            },
            series: [
                {
                    name: '对话数',
                    type: 'line',
                    stack: 'Total',
                    smooth: true,
                    areaStyle: {
                        opacity: 0.3,
                        color: '#1890ff'
                    },
                    lineStyle: {
                        color: '#1890ff'
                    },
                    itemStyle: {
                        color: '#1890ff'
                    },
                    emphasis: {
                        focus: 'series'
                    },
                    data: dates.length > 0 ? counts : [0]
                }
            ]
        };
        trendChart.setOption(optionTrend);
        chartInstances.current.push(trendChart);
    }
  };

  // 意图类型中文映射
  const getIntentLabel = (intent) => {
    const map = {
      'shopping': '商品导购',
      'chitchat': '闲聊',
      'knowledge_qa': '知识问答',
      'unknown': '未知'
    };
    return map[intent] || intent;
  };

  // 意图类型颜色映射
  const getIntentColor = (intent) => {
    const map = {
      'shopping': '#1890ff',
      'chitchat': '#52c41a',
      'knowledge_qa': '#faad14',
      'unknown': '#999'
    };
    return map[intent] || '#999';
  };

  if (loading) return <div className="loading">加载中...</div>;
  if (!stats) return <div className="error">暂无数据</div>;

  return (
    <div className="dashboard-container">
      <h2 className="page-title">电商导购数据大屏</h2>
      
      {/* 核心指标卡片 */}
      <div className="stats-cards">
        <div className="stat-card">
          <div className="stat-value">{stats.todayConversations || 0}</div>
          <div className="stat-label">今日对话数</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{stats.todayRecommendations || 0}</div>
          <div className="stat-label">今日推荐次数</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{(stats.clickRate || 0).toFixed(1)}%</div>
          <div className="stat-label">推荐点击率</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{(stats.satisfactionRate || 0).toFixed(1)}%</div>
          <div className="stat-label">用户满意度</div>
        </div>
      </div>

      {/* 图表区域 */}
      <div className="charts-row" style={{ display: 'flex', gap: '20px', margin: '20px 0', height: '350px' }}>
          <div className="chart-container" style={{ flex: 1, background: '#fff', padding: '15px', borderRadius: '8px', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}>
              <div ref={pieChartRef} style={{ width: '100%', height: '100%' }}></div>
          </div>
          <div className="chart-container" style={{ flex: 1, background: '#fff', padding: '15px', borderRadius: '8px', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}>
              <div ref={trendChartRef} style={{ width: '100%', height: '100%' }}></div>
          </div>
      </div>

      <div className="dashboard-grid">
        {/* 热门商品 TOP5 */}
        <div className="dashboard-section">
          <h3>🔥 热门商品 TOP5</h3>
          <table className="data-table">
            <thead>
              <tr>
                <th>商品名称</th>
                <th>品牌</th>
                <th>价格</th>
                <th>销量</th>
                <th>评分</th>
              </tr>
            </thead>
            <tbody>
              {stats.hotProducts && stats.hotProducts.length > 0 ? (
                stats.hotProducts.map((product, index) => (
                  <tr key={index}>
                    <td className="text-truncate" title={product.title} style={{maxWidth: '150px'}}>{product.title}</td>
                    <td>{product.brand}</td>
                    <td>¥{product.base_price}</td>
                    <td>{product.sales_count}</td>
                    <td>⭐ {product.rating}</td>
                  </tr>
                ))
              ) : (
                <tr><td colSpan="5" className="empty-text">暂无数据</td></tr>
              )}
            </tbody>
          </table>
        </div>

        {/* 最近推荐记录 */}
        <div className="dashboard-section">
          <h3>💡 最近推荐记录</h3>
          <table className="data-table">
             <thead>
                <tr>
                    <th>查询内容</th>
                    <th>意图</th>
                    <th>是否点击</th>
                    <th>反馈</th>
                </tr>
             </thead>
             <tbody>
                {stats.recentRecommendations && stats.recentRecommendations.length > 0 ? (
                  stats.recentRecommendations.map((rec, index) => (
                    <tr key={index}>
                      <td className="text-truncate" title={rec.query} style={{maxWidth: '150px'}}>{rec.query}</td>
                      <td>
                        <span style={{
                          padding: '2px 8px',
                          borderRadius: '4px',
                          background: getIntentColor(rec.intent) + '20',
                          color: getIntentColor(rec.intent)
                        }}>
                          {getIntentLabel(rec.intent)}
                        </span>
                      </td>
                      <td>{rec.userClicked ? '✅ 是' : '❌ 否'}</td>
                      <td>{rec.userFeedback === 1 ? '👍 满意' : rec.userFeedback === 0 ? '👎 不满意' : '未反馈'}</td>
                    </tr>
                  ))
                ) : (
                  <tr><td colSpan="4" className="empty-text">暂无推荐记录</td></tr>
                )}
             </tbody>
          </table>
        </div>

        {/* 未命中问题（保留，用于监控） */}
        <div className="dashboard-section">
          <h3>❓ 未命中问题 (需优化)</h3>
          <table className="data-table">
             <thead>
                <tr>
                    <th>问题内容</th>
                    <th>频次</th>
                </tr>
             </thead>
             <tbody>
                {stats.unansweredQuestions && stats.unansweredQuestions.length > 0 ? (
                  stats.unansweredQuestions.map((q, index) => (
                    <tr key={index}>
                      <td className="text-truncate" title={q.question} style={{maxWidth: '200px', color: '#ff4d4f'}}>{q.question}</td>
                      <td>{q.count}</td>
                    </tr>
                  ))
                ) : (
                  <tr><td colSpan="2" className="empty-text">表现良好，暂无未命中</td></tr>
                )}
             </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
