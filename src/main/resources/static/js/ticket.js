import { $, api, params, dateText, show, authForm } from './api.js';

const code = params.get('code');
let loading = false;

async function refresh() {
  if (loading) return;
  if (!code) return show('Thiếu mã vé. Hãy mở liên kết vé được cấp sau khi đăng ký.', true);
  loading = true;
  $('#refresh-ticket').disabled = true;
  try {
    const ticket = await api(`/api/v1/tickets/${encodeURIComponent(code)}`);
    const eventCancelled = ticket.eventLifecycleStatus === 'CANCELLED';
    $('#title').textContent = ticket.eventTitle;
    $('#name').textContent = ticket.attendeeName;
    $('#venue').textContent = ticket.venueName;
    $('#join-online').hidden = !ticket.onlineUrl || ticket.status === 'CANCELLED' || eventCancelled;
    if (ticket.onlineUrl) $('#join-online').href = ticket.onlineUrl;
    $('#time').textContent = dateText(ticket.startsAt);
    $('#code').textContent = ticket.code;
    const cancelled = ticket.status === 'CANCELLED';
    $('#state').textContent = eventCancelled ? 'Sự kiện đã hủy'
      : cancelled ? 'Đã hủy' : ticket.status === 'CHECKED_IN' ? 'Đã check-in' : 'Chưa check-in';
    const url = `/api/v1/tickets/${encodeURIComponent(code)}/qr`;
    $('#qr').hidden = cancelled || eventCancelled;
    $('#download').hidden = cancelled || eventCancelled;
    if (!cancelled && !eventCancelled) {
      $('#qr').src = url;
      $('#download').href = url;
    } else {
      $('#qr').removeAttribute('src');
      $('#download').removeAttribute('href');
    }
    $('#feedback-link').hidden = cancelled || eventCancelled || ticket.status !== 'CHECKED_IN';
    $('#feedback-link').textContent = ticket.feedbackSent ? 'Xem đánh giá của tôi' : 'Đánh giá sự kiện';
    $('#feedback-link').href = `/pages/feedback.html?code=${encodeURIComponent(code)}`;
    $('#feedback-hint').textContent = eventCancelled
      ? `Sự kiện đã hủy. Lý do: ${ticket.eventCancelReason || 'Ban tổ chức chưa cập nhật.'} Mã QR không còn hiệu lực.`
      : cancelled
      ? `Vé đã hủy lúc ${dateText(ticket.cancelledAt)}. Mã QR không còn hiệu lực.`
      : ticket.feedbackSent
      ? 'Bạn đã gửi đánh giá. Cảm ơn bạn đã tham dự!'
      : ticket.status === 'CHECKED_IN'
        ? 'Check-in thành công. Hãy chia sẻ trải nghiệm của bạn qua phần đánh giá.'
        : 'Sau khi check-in, cập nhật trạng thái để gửi đánh giá.';
  } catch (err) {
    show(err.message, true);
  } finally {
    loading = false;
    $('#refresh-ticket').disabled = false;
  }
}

$('#refresh-ticket').addEventListener('click', refresh);
window.addEventListener('focus', refresh);
authForm(refresh);
