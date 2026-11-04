import { $, api, authForm, show } from './api.js';
import './password-toggle.js';

function render(settings, password = '') {
  $('#mail-enabled').checked = settings.enabled;
  $('#mail-host').value = settings.host || 'smtp.gmail.com';
  $('#mail-port').value = settings.port || 587;
  $('#mail-user').value = settings.username || '';
  $('#mail-from').value = settings.fromAddress || '';
  $('#mail-password').value = password;
  $('#test-address').value ||= settings.fromAddress || '';
}

async function savedPassword(settings) {
  if (!settings.passwordConfigured) return '';
  const result = await api('/api/v1/admin/mail-settings/password');
  return result.password;
}

authForm(async () => {
  const settings = await api('/api/v1/admin/mail-settings');
  render(settings, await savedPassword(settings));
});

$('#settings-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    const typedPassword = $('#mail-password').value;
    const settings = await api('/api/v1/admin/mail-settings', {
      method: 'PUT',
      body: JSON.stringify({
        enabled: $('#mail-enabled').checked,
        host: $('#mail-host').value.trim(),
        port: Number($('#mail-port').value),
        username: $('#mail-user').value.trim(),
        fromAddress: $('#mail-from').value.trim(),
        password: typedPassword,
      }),
    });
    render(settings, typedPassword || await savedPassword(settings));
    show('Đã lưu cấu hình email.');
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$('#test-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    const result = await api('/api/v1/admin/mail-settings/test', {
      method: 'POST', body: JSON.stringify({ address: $('#test-address').value.trim() }),
    });
    show(result.message);
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
});
