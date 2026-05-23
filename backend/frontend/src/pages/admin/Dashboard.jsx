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

    const handleResize = () => {
      chartInstances.current.forEach(chart => chart.resize());
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chartInstances.current.forEach(chart => chart.dispose());
      chartInstances.current = [];
    };
  }, []);

  useEffect(() => {
    if (!stats) {
      return;
    }
    const timer = window.setTimeout(initCharts, 100);
    return () => window.clearTimeout(timer);
  }, [stats]);

  const fetchStats = async () => {
    try {
      setError('');
      const res = await getDashboardStats();
      setStats(res.data || {});
    } catch (err) {
      console.error('Fetch dashboard stats failed', err);
      setError(err.response?.data?.message || err.message || '仪表盘数据加载失败');
    } finally {
      setLoading(false);
    }
  };

  const initCharts = () => {
    chartInstances.current.forEach(chart => chart.dispose());
    chartInstances.current = [];

    if (pieChartRef.current) {
      const pieChart = echarts.init(pieChartRef.current);
      const intentData = Object.entries(stats.intentDistribution || {}).map(([key, value]) => ({
        name: getIntentLabel(key),
        value
      }));

      pieChart.setOption({
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
          bottom: 0,
          left: 'center'
        },
        color: ['#1890ff', '#52c41a', '#faad14', '#ff4d4f'],
        series: [
          {
            name: '意图',
            type: 'pie',
            radius: ['30%', '60%'],
            center: ['50%', '48%'],
            itemStyle: {
              borderRadius: 8,
              shadowBlur: 20,
              shadowColor: 'rgba(0, 0, 0, 0.15)'
            },
            data: intentData.length > 0 ? intentData : [{ value: 0, name: '暂无数据' }]
          }
        ]
      });
      chartInstances.current.push(pieChart);
    }

    if (trendChartRef.current) {
      const trendChart = echarts.init(trendChartRef.current);
      const trends = stats.dailyTrends || [];
      const dates = trends.map(item => item.date);
      const counts = trends.map(item => item.count);

      trendChart.setOption({
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
          data: dates.length > 0 ? dates : ['暂无数据']
        },
        yAxis: {
          type: 'value',
          minInterval: 1
        },
        series: [
          {
            name: '对话数',
            type: 'line',
            smooth: true,
            areaStyle: {
              opacity: 0.25,
              color: '#1890ff'
            },
            lineStyle: {
              color: '#1890ff'
            },
            itemStyle: {
              color: '#1890ff'
            },
            data: dates.length > 0 ? counts : [0]
          }
        ]
      });
      chartInstances.current.push(trendChart);
    }
  };

  const getIntentLabel = (intent) => {
    const map = {
      shopping: '商品导购',
      chitchat: '闲聊',
      knowledge_qa: '知识问答',
      product_search: '商品搜索',
      product_compare: '商品对比',
      unknown: '未知'
    };
    return map[intent] || intent;
  };

  const getIntentColor = (intent) => {
    const map = {
      shopping: '#1890ff',
      chitchat: '#52c41a',
      knowledge_qa: '#faad14',
      product_search: '#13c2c2',
      product_compare: '#722ed1',
      unknown: '#999'
    };
    return map[intent] || '#999';
  };

  if (loading) {
    return <div className="loading">加载中...</div>;
  }

  if (error) {
    return <div className="error">{error}</div>;
  }

  if (!stats) {
    return <div className="error">暂无数据</div>;
  }

  return (
    <div className="dashboard-container">
      <h2 className="page-title">电商导购数据大屏</h2>

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

      <div className="charts-row">
        <div className="chart-container">
          <div ref={pieChartRef} className="chart-canvas" />
        </div>
        <div className="chart-container">
          <div ref={trendChartRef} className="chart-canvas" />
        </div>
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-section">
          <h3>热门商品 TOP5</h3>
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
              {stats.hotProducts?.length > 0 ? (
                stats.hotProducts.map((product, index) => (
                  <tr key={`${product.title}-${index}`}>
                    <td className="text-truncate" title={product.title}>{product.title}</td>
                    <td>{product.brand || '-'}</td>
                    <td>¥{product.base_price || 0}</td>
                    <td>{product.sales_count || 0}</td>
                    <td>{product.rating || 0}</td>
                  </tr>
                ))
              ) : (
                <tr><td colSpan="5" className="empty-text">暂无数据</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="dashboard-section">
          <h3>最近推荐记录</h3>
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
              {stats.recentRecommendations?.length > 0 ? (
                stats.recentRecommendations.map((rec, index) => (
                  <tr key={rec.id || index}>
                    <td className="text-truncate" title={rec.query}>{rec.query || '-'}</td>
                    <td>
                      <span
                        className="intent-badge"
                        style={{
                          background: `${getIntentColor(rec.intent)}20`,
                          color: getIntentColor(rec.intent)
                        }}
                      >
                        {getIntentLabel(rec.intent)}
                      </span>
                    </td>
                    <td>{rec.userClicked ? '是' : '否'}</td>
                    <td>{rec.userFeedback === 1 ? '满意' : rec.userFeedback === 0 ? '不满意' : '未反馈'}</td>
                  </tr>
                ))
              ) : (
                <tr><td colSpan="4" className="empty-text">暂无推荐记录</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="dashboard-section">
          <h3>未命中问题</h3>
          <table className="data-table">
            <thead>
              <tr>
                <th>问题内容</th>
                <th>频次</th>
              </tr>
            </thead>
            <tbody>
              {stats.unansweredQuestions?.length > 0 ? (
                stats.unansweredQuestions.map((question, index) => (
                  <tr key={`${question.question}-${index}`}>
                    <td className="text-truncate warning-text" title={question.question}>{question.question}</td>
                    <td>{question.count}</td>
                  </tr>
                ))
              ) : (
                <tr><td colSpan="2" className="empty-text">暂无未命中问题</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
