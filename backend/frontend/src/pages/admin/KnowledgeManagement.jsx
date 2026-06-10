import { useState, useEffect } from 'react';
import { knowledgeManagementAPI } from '../../api/admin';
import ConfirmDialog from '../../components/ConfirmDialog';
import './KnowledgeManagement.css';

export default function KnowledgeManagement() {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [confirmDialog, setConfirmDialog] = useState({ open: false, docId: null });
  const [showMessageModal, setShowMessageModal] = useState(false);
  const [messageModalContent, setMessageModalContent] = useState('');
  const [messageModalTitle, setMessageModalTitle] = useState('提示');

  useEffect(() => { loadDocuments(); }, []);

  const loadDocuments = async () => {
    setLoading(true);
    try {
      const response = await knowledgeManagementAPI.list();
      setDocuments(response.data || []);
    } catch (err) {
      console.error('加载文档列表失败:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file) setSelectedFile(file);
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      showMessage('提示', '请先选择文件');
      return;
    }
    setUploading(true);
    try {
      await knowledgeManagementAPI.upload(selectedFile);
      setSelectedFile(null);
      document.getElementById('admin-file-input').value = '';
      loadDocuments();
      showMessage('成功', '上传成功');
    } catch (err) {
      showMessage('错误', '上传失败: ' + (err.message || '未知错误'));
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = (id) => {
    setConfirmDialog({ open: true, docId: id });
  };

  const handleDeleteConfirm = async () => {
    if (!confirmDialog.docId) return;
    try {
      await knowledgeManagementAPI.delete(confirmDialog.docId);
      setDocuments(documents.filter(doc => doc.id !== confirmDialog.docId));
      showMessage('成功', '删除成功');
    } catch (err) {
      showMessage('错误', '删除失败');
    }
    setConfirmDialog({ open: false, docId: null });
  };

  const handleRetry = async (id, filePath) => {
    try {
      await knowledgeManagementAPI.retryParse(id, filePath);
      loadDocuments();
      showMessage('成功', '重试解析成功');
    } catch (err) {
      showMessage('错误', '重试失败: ' + (err.message || '未知错误'));
    }
  };

  const showMessage = (title, content) => {
    setMessageModalTitle(title);
    setMessageModalContent(content);
    setShowMessageModal(true);
  };

  const getStatusInfo = (status) => {
    const map = {
      'PENDING': { text: '待处理', cls: 'pending' },
      'PROCESSING': { text: '解析中', cls: 'processing' },
      'COMPLETED': { text: '已完成', cls: 'completed' },
      'parsed': { text: '已解析', cls: 'completed' },
      'FAILED': { text: '失败', cls: 'failed' },
    };
    return map[status] || { text: status || '未知', cls: 'default' };
  };

  // 统计数据
  const stats = {
    total: documents.length,
    parsed: documents.filter(d => d.status === 'COMPLETED' || d.status === 'parsed').length,
    pending: documents.filter(d => d.status === 'PENDING' || d.status === 'PROCESSING').length,
    failed: documents.filter(d => d.status === 'FAILED').length,
  };

  return (
    <div className="knowledge-page">
      <h2 className="km-title">知识库管理</h2>

      {/* 统计卡片 */}
      <div className="km-stats">
        <div className="km-stat-card">
          <div className="km-stat-num">{stats.total}</div>
          <div className="km-stat-label">总文档</div>
        </div>
        <div className="km-stat-card green">
          <div className="km-stat-num">{stats.parsed}</div>
          <div className="km-stat-label">已解析</div>
        </div>
        <div className="km-stat-card orange">
          <div className="km-stat-num">{stats.pending}</div>
          <div className="km-stat-label">处理中</div>
        </div>
        <div className="km-stat-card red">
          <div className="km-stat-num">{stats.failed}</div>
          <div className="km-stat-label">失败</div>
        </div>
      </div>

      {/* 上传区域 */}
      <div className="km-card">
        <h3>上传文档</h3>
        <div className="km-upload-row">
          <input
            id="admin-file-input"
            type="file"
            onChange={handleFileSelect}
            accept=".txt,.pdf,.doc,.docx,.md"
          />
          <button
            className="btn btn-primary"
            onClick={handleUpload}
            disabled={!selectedFile || uploading}
          >
            {uploading ? '上传中...' : '上传'}
          </button>
        </div>
        <p className="km-hint">支持格式: .txt, .pdf, .md（上传后自动解析并存入向量库）</p>
      </div>

      {/* 文档列表 */}
      <div className="km-card">
        <div className="km-card-header">
          <h3>文档列表</h3>
          <span className="km-total">共 {documents.length} 个文档</span>
        </div>

        {loading ? (
          <div className="km-empty">加载中...</div>
        ) : documents.length === 0 ? (
          <div className="km-empty">暂无文档，请先上传</div>
        ) : (
          <table className="km-table">
            <thead>
              <tr>
                <th style={{ width: 60 }}>ID</th>
                <th>文档名称</th>
                <th style={{ width: 100 }}>类型</th>
                <th style={{ width: 100 }}>状态</th>
                <th style={{ width: 170 }}>上传时间</th>
                <th style={{ width: 120 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => {
                const si = getStatusInfo(doc.status);
                return (
                  <tr key={doc.id}>
                    <td>{doc.id}</td>
                    <td className="km-doc-name" title={doc.docName}>{doc.docName}</td>
                    <td><span className="km-type-tag">{(doc.docType || 'md').toUpperCase()}</span></td>
                    <td>
                      <span className={`km-status ${si.cls}`}>{si.text}</span>
                      {doc.status === 'FAILED' && doc.errorMessage && (
                        <div className="km-error-hint" title={doc.errorMessage}>
                          {doc.errorMessage.length > 30 ? doc.errorMessage.slice(0, 30) + '...' : doc.errorMessage}
                        </div>
                      )}
                    </td>
                    <td>{doc.createTime ? new Date(doc.createTime).toLocaleString('zh-CN') : '-'}</td>
                    <td>
                      <button className="km-btn-delete" onClick={() => handleDelete(doc.id)}>删除</button>
                      {doc.status === 'FAILED' && (
                        <button className="km-btn-retry" onClick={() => handleRetry(doc.id, doc.filePath)}>重试</button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <ConfirmDialog
        open={confirmDialog.open}
        title="确认删除"
        message="确定删除此文档吗？删除后向量库中的对应数据也会被清除。"
        confirmText="删除"
        danger={true}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setConfirmDialog({ open: false, docId: null })}
      />

      {/* 提示弹窗 */}
      {showMessageModal && (
        <div className="modal-overlay" onClick={() => setShowMessageModal(false)}>
          <div className="modal-content" style={{ maxWidth: 400 }} onClick={e => e.stopPropagation()}>
            <div className="modal-header"><h3>{messageModalTitle}</h3></div>
            <div className="modal-body"><p>{messageModalContent}</p></div>
            <div className="modal-footer">
              <button className="modal-btn confirm" onClick={() => setShowMessageModal(false)}>确定</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
