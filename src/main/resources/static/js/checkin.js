import { $, api, esc, show, authForm, dateText, eventTime } from './api.js';

let scanner = null;
let busy = false;
let cameraBusy = false;
let events = [];
let lastScan = '';
let lastScanAt = 0;
let searchPage = 0;
let searchTerm = '';

function windowHint() {
  const event = events.find((item) => item.id === Number($('#event').value));
  if (!event) return;
  const start = eventTime(event.startsAt).getTime() - 60 * 60000;
  const end = eventTime(event.endsAt).getTime() - 15 * 60000;
  const format = (time) =>
    new Date(time).toLocaleString('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  $('#checkin-window').textContent =
    `Nhận khách từ ${format(start)} đến trước ${format(end)}. Mỗi vé chỉ dùng một lần.`;
}

async function login() {
  await api('/api/v1/me');
  events = (await api('/api/v1/events'))
    .filter((event) => event.lifecycleStatus !== 'CANCELLED');
  $('#event').innerHTML = events
    .map((e) => `<option value="${e.id}">${esc(e.title)}</option>`)
    .join('');
  $('#workspace').hidden = false;
  $('#stop-scan').disabled = true;
  windowHint();
  if (!events.length) show('Chưa có sự kiện. Ban tổ chức cần tạo sự kiện trước.');
}

async function submit(body) {
  if (busy) return;
  busy = true;
  $('#event').disabled = true;
  const button = $('#manual-form button');
  button.disabled = true;
  try {
    const id = $('#event').value;
    if (!id) throw new Error('Hãy chọn sự kiện.');
    const result = await api(`/api/v1/events/${id}/check-ins`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
    show(`${result.message} ${result.attendeeName}`);
    $('#last-result').className = 'card success';
    $('#last-result').innerHTML = `
      <strong>${esc(result.attendeeName)}</strong>
      <p class="small">Check-in thành công · ${dateText(result.checkedInAt)}</p>
      <p class="small">Mã vé: ${esc(result.ticketCode)}</p>
    `;
    $('#ticket-code').value = '';
    $('#search-results').replaceChildren();
  } catch (err) {
    show(err.message, true);
    $('#last-result').className = 'card error';
    $('#last-result').textContent = err.message;
  } finally {
    $('#event').disabled = false;
    button.disabled = false;
    busy = false;
  }
}

authForm(login);
$('#event').addEventListener('change', () => {
  lastScan = '';
  windowHint();
  $('#last-result').className = 'card muted';
  $('#last-result').textContent = 'Chưa có lượt check-in cho lần chọn sự kiện này.';
  $('#search-results').replaceChildren();
  $('#search-pages').hidden = true;
});
$('#manual-form').addEventListener('submit', (e) => {
  e.preventDefault();
  submit({ ticketCode: $('#ticket-code').value.trim(), method: 'MANUAL' });
});

async function searchGuests(page) {
  const eventId = $('#event').value;
  const query = searchTerm;
  if (!eventId) return show('Hãy chọn sự kiện trước khi tìm khách.', true);
  if (query.length < 2) return show('Nhập ít nhất 2 ký tự để tìm khách.', true);
  const button = $('#search-form button');
  button.disabled = true;
  try {
    const result = await api(
      `/api/v1/events/${eventId}/check-ins/search?query=${encodeURIComponent(query)}&page=${page}`,
    );
    if (eventId !== $('#event').value || query !== searchTerm) return;
    searchPage = page;
    $('#search-results').innerHTML = result.items.length
      ? `<div class="table-wrap"><table><thead><tr><th>Khách</th><th>Email</th><th>Trạng thái</th><th></th></tr></thead><tbody>${result.items
          .map(
            (item) => `<tr>
              <td>${esc(item.attendeeName)}</td>
              <td>${esc(item.attendeeEmail)}</td>
              <td>${item.status === 'CHECKED_IN' ? 'Đã đến' : 'Chưa đến'}</td>
              <td><button type="button" class="btn secondary select-ticket" data-code="${esc(item.ticketCode)}" ${item.status === 'CHECKED_IN' ? 'disabled' : ''}>Chọn vé</button></td>
            </tr>`,
          )
          .join('')}</tbody></table></div>`
      : '<p class="muted small">Không tìm thấy khách đăng ký trong sự kiện này.</p>';
    $('#search-count').textContent = result.total
      ? `Hiển thị ${page * result.pageSize + 1}–${page * result.pageSize + result.items.length} / ${result.total} khách`
      : 'Không tìm thấy khách';
    $('#search-prev').disabled = page === 0;
    $('#search-next').disabled = (page + 1) * result.pageSize >= result.total;
    $('#search-pages').hidden = result.total <= result.pageSize;
    $('#search-results').querySelectorAll('.select-ticket').forEach((item) => {
      item.addEventListener('click', () => {
        $('#ticket-code').value = item.dataset.code;
        $('#ticket-code').focus();
        show('Đã chọn mã vé. Kiểm tra thông tin khách và bấm Xác nhận check-in.');
      });
    });
  } catch (err) {
    show(err.message, true);
  } finally {
    button.disabled = false;
  }
}

$('#search-form').addEventListener('submit', (e) => {
  e.preventDefault();
  searchTerm = $('#attendee-query').value.trim();
  searchGuests(0);
});
$('#search-prev').addEventListener('click', () => searchGuests(searchPage - 1));
$('#search-next').addEventListener('click', () => searchGuests(searchPage + 1));

$('#start-scan').addEventListener('click', async () => {
  if (scanner || cameraBusy) return;
  if (!$('#event').value) return show('Hãy chọn sự kiện trước khi bật camera.', true);
  if (!window.isSecureContext)
    return show('Camera cần kết nối HTTPS. Trên laptop, hãy mở http://localhost:8080.', true);
  if (!window.Html5Qrcode) return show('Không tải được bộ quét QR. Hãy tải lại trang.', true);
  cameraBusy = true;
  $('#start-scan').disabled = true;
  try {
    scanner = new Html5Qrcode('scanner');
    await scanner.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 230, height: 230 } },
      (decoded) => {
        if (busy || (lastScan === decoded && Date.now() - lastScanAt < 5000)) return;
        lastScan = decoded;
        lastScanAt = Date.now();
        submit({ qrToken: decoded, method: 'QR' });
      },
    );
    $('#stop-scan').disabled = false;
    show('Camera đã bật. Đưa mã QR vào khung quét.');
  } catch (err) {
    scanner?.clear();
    scanner = null;
    $('#start-scan').disabled = false;
    show(
      'Không mở được camera. Hãy cấp quyền camera, đóng ứng dụng đang dùng camera hoặc nhập mã vé bên cạnh.',
      true,
    );
  } finally {
    cameraBusy = false;
  }
});

$('#stop-scan').addEventListener('click', async () => {
  if (!scanner || cameraBusy) return;
  cameraBusy = true;
  try {
    await scanner.stop();
    scanner.clear();
    scanner = null;
    $('#start-scan').disabled = false;
    $('#stop-scan').disabled = true;
    show('Đã tắt camera.');
  } catch (err) {
    show('Không tắt được camera. Hãy tải lại trang.', true);
  } finally {
    cameraBusy = false;
  }
});
