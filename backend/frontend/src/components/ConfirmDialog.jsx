import './ConfirmDialog.css';

export default function ConfirmDialog({ open, title, message, confirmText = '确认', cancelText = '取消', danger = false, onConfirm, onCancel }) {
  if (!open) return null;

  return (
    <div className="confirm-overlay" onClick={onCancel}>
      <div className="confirm-dialog" onClick={e => e.stopPropagation()}>
        <div className="confirm-header">
          <h3>{title || '确认操作'}</h3>
        </div>
        <div className="confirm-body">
          <p>{message}</p>
        </div>
        <div className="confirm-footer">
          <button className="btn btn-secondary" onClick={onCancel}>{cancelText}</button>
          <button className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} onClick={onConfirm}>{confirmText}</button>
        </div>
      </div>
    </div>
  );
}
