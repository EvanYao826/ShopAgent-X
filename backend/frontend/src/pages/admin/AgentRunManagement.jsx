import { useState, useEffect } from 'react';
import './AgentRunManagement.css';

const statusColors = {
  PENDING: '#BDB0A5',
  RUNNING: '#FF6B35',
  COMPLETED: '#00B894',
  FAILED: '#E17055',
  INTERRUPTED: '#FDCB6E',
  WAITING: '#74B9FF',
  pending: '#BDB0A5',
  running: '#FF6B35',
  completed: '#00B894',
  failed: '#E17055',
  success: '#00B894',
};

const statusLabels = {
  PENDING: '待执行', RUNNING: '执行中', COMPLETED: '已完成',
  FAILED: '失败', INTERRUPTED: '已中断', WAITING: '等待中',
  pending: '待执行', running: '执行中', completed: '已完成',
  failed: '失败', success: '成功',
};

const stepTypeLabels = {
  intent_recognition: '意图识别',
  question_classification: '问题分类',
  clarification: '澄清判断',
  question_rewrite: '问题改写',
  knowledge_search: '知识检索',
  result_evaluation: '结果评估',
  answer_generation: '答案生成',
  memory_read: '记忆读取',
  memory_write: '记忆写入',
  memory_compress: '记忆压缩',
  tool_call: '工具调用',
  // 任务类型（简单链路）
  shopping: '商品导购',
  chitchat: '闲聊',
  knowledge_qa: '知识问答',
  admin_copilot: '管理助手',
  knowledge_inspection: '知识巡检',
  reasoning: '复杂推理',
  cart: '购物车',
  unknown: '未知',
};

export default function AgentRunManagement() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [visible, setVisible] = useState(false);
  const [selectedRun, setSelectedRun] = useState(null);
  const [steps, setSteps] = useState([]);

  const [filters, setFilters] = useState({
    userId: '', status: '', dateStart: '', dateEnd: ''
  });

  useEffect(() => { fetchData(); }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      let url = '/api/admin/agent/runs?page=1&size=50';
      if (filters.userId) url += `&userId=${filters.userId}`;
      if (filters.status) url += `&status=${filters.status}`;
      if (filters.dateStart) url += `&startTime=${filters.dateStart}T00:00:00`;
      if (filters.dateEnd) url += `&endTime=${filters.dateEnd}T23:59:59`;

      const token = localStorage.getItem('adminToken');
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : ''
        }
      });
      const result = await response.json();

      if (result.code === 200) {
        setData(result.data || []);
      }
    } catch (error) {
      console.error('获取Agent运行记录失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchRunDetail = async (runId) => {
    try {
      const token = localStorage.getItem('adminToken');
      const headers = {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      };

      const [runResponse, stepsResponse] = await Promise.all([
        fetch(`/api/admin/agent/runs/${runId}`, { headers }),
        fetch(`/api/admin/agent/runs/${runId}/steps`, { headers })
      ]);
      const runResult = await runResponse.json();
      const stepsResult = await stepsResponse.json();
      if (runResult.code === 200) {
        setSelectedRun(runResult.data);
        setSteps(stepsResult.code === 200 ? (stepsResult.data || []) : []);
        setVisible(true);
      }
    } catch (error) {
      console.error('获取Agent运行详情失败:', error);
    }
  };

  const handleSearch = () => { fetchData(); };

  const handleReset = () => {
    setFilters({ userId: '', status: '', dateStart: '', dateEnd: '' });
    fetchData();
  };

  const formatTime = (time) => {
    if (!time) return '-';
    return new Date(time).toLocaleString('zh-CN');
  };

  const formatDuration = (ms) => {
    if (!ms && ms !== 0) return '-';
    if (ms < 1000) return `${Math.round(ms)}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  return (
    <div className="admin-panel">
      <div className="panel-header">
        <h2>Agent 执行记录</h2>
      </div>

      <div className="search-bar">
        <div className="search-row">
          <div className="search-item">
            <label>用户ID:</label>
            <input
              type="text"
              value={filters.userId}
              onChange={(e) => setFilters({ ...filters, userId: e.target.value })}
              placeholder="输入用户ID"
            />
          </div>
          <div className="search-item">
            <label>状态:</label>
            <select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
              <option value="">全部</option>
              {Object.entries(statusLabels).filter(([k]) => k === k.toLowerCase()).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          <div className="search-item">
            <label>日期范围:</label>
            <input type="date" value={filters.dateStart} onChange={(e) => setFilters({ ...filters, dateStart: e.target.value })} />
            <span>~</span>
            <input type="date" value={filters.dateEnd} onChange={(e) => setFilters({ ...filters, dateEnd: e.target.value })} />
          </div>
          <div className="search-actions">
            <button className="btn btn-primary" onClick={handleSearch}>搜索</button>
            <button className="btn btn-ghost" onClick={handleReset}>重置</button>
          </div>
        </div>
      </div>

      <div className="table-container">
        <table className="admin-table">
          <thead>
            <tr>
              <th>运行ID</th>
              <th>用户ID</th>
              <th>会话ID</th>
              <th>状态</th>
              <th>目标</th>
              <th>开始时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan="7" className="loading">加载中...</td></tr>
            ) : data.length === 0 ? (
              <tr><td colSpan="7" className="empty">暂无数据</td></tr>
            ) : (
              data.map((item) => (
                <tr key={item.runId}>
                  <td className="text-primary">{item.runId}</td>
                  <td>{item.userId}</td>
                  <td className="ellipsis">{item.conversationId}</td>
                  <td>
                    <span className="status-badge" style={{ backgroundColor: statusColors[item.status] + '20', color: statusColors[item.status] }}>
                      {statusLabels[item.status]}
                    </span>
                  </td>
                  <td className="ellipsis" title={item.goal}>{item.goal}</td>
                  <td>{formatTime(item.startTime)}</td>
                  <td>
                    <button className="action-btn edit" onClick={() => fetchRunDetail(item.runId)}>
                      查看详情
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {visible && (
        <div className="modal-overlay" onClick={() => setVisible(false)}>
          <div className="modal-content modal-wide" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Agent Run 详情</h3>
              <button className="modal-close" onClick={() => setVisible(false)}>×</button>
            </div>
            <div className="modal-body">
              {selectedRun && (
                <div className="detail-content">
                  <div className="detail-grid">
                    <div className="detail-row">
                      <span className="detail-label">Run ID:</span>
                      <span className="detail-value">{selectedRun.runId}</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">Trace ID:</span>
                      <span className="detail-value">{selectedRun.traceId}</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">意图:</span>
                      <span className="detail-value">
                        <span className="intent-tag">{stepTypeLabels[selectedRun.intent] || selectedRun.intent}</span>
                      </span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">状态:</span>
                      <span className="detail-value">
                        <span className="status-badge" style={{ backgroundColor: statusColors[selectedRun.status] + '20', color: statusColors[selectedRun.status] }}>
                          {statusLabels[selectedRun.status]}
                        </span>
                      </span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">耗时:</span>
                      <span className="detail-value">
                        {selectedRun.startTime && selectedRun.endTime
                          ? formatDuration(new Date(selectedRun.endTime) - new Date(selectedRun.startTime))
                          : '-'}
                      </span>
                    </div>
                    {selectedRun.errorCode && (
                      <div className="detail-row">
                        <span className="detail-label">错误码:</span>
                        <span className="detail-value text-error">{selectedRun.errorCode}</span>
                      </div>
                    )}
                  </div>

                  <div className="detail-section">
                    <h4>输入</h4>
                    <div className="detail-box">{selectedRun.input || '-'}</div>
                  </div>
                  <div className="detail-section">
                    <h4>输出</h4>
                    <div className="detail-box">{selectedRun.output || '-'}</div>
                  </div>

                  <div className="detail-section">
                    <h4>执行步骤轨迹</h4>
                    {steps.length === 0 ? (
                      <div className="empty-steps">暂无步骤数据</div>
                    ) : (
                      <div className="timeline">
                        {steps.map((step, idx) => (
                          <div key={step.id || idx} className={`timeline-item timeline-${step.status || 'completed'}`}>
                            <div className="timeline-dot" style={{ backgroundColor: statusColors[step.status] || '#52c41a' }} />
                            {idx < steps.length - 1 && <div className="timeline-line" />}
                            <div className="timeline-content">
                              <div className="timeline-header">
                                <span className="timeline-name">{stepTypeLabels[step.stepName] || stepTypeLabels[step.stepType] || step.stepName || step.stepType}</span>
                                <span className="status-badge status-sm" style={{ backgroundColor: statusColors[step.status] + '20', color: statusColors[step.status] }}>
                                  {statusLabels[step.status] || step.status}
                                </span>
                                <span className="timeline-duration">{formatDuration(step.durationMs)}</span>
                              </div>
                              {step.errorMessage && (
                                <div className="timeline-error">{step.errorMessage}</div>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
