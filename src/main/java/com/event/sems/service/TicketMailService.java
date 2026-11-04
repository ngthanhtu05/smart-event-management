package com.event.sems.service;

import com.event.sems.common.ApiException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketMailService {
  private static final Logger log = LoggerFactory.getLogger(TicketMailService.class);
  private final MailSettingsService settings;

  @Value("${app.public-base-url}")
  private String baseUrl;

  public boolean isEnabled() {
    return settings.connection().enabled();
  }

  public void send(String address, String eventTitle, String ticketCode, byte[] qrPng) {
    if (!isEnabled()) return;
    try {
      var connection = settings.connection();
      JavaMailSenderImpl sender = sender(connection);
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(connection.fromAddress());
      helper.setTo(address);
      helper.setSubject("Vé tham dự: " + eventTitle);
      helper.setText(
          "Bạn đã đăng ký thành công sự kiện "
              + eventTitle
              + ".\nMã vé: "
              + ticketCode
              + "\nXem vé: "
              + baseUrl
              + "/pages/ticket.html?code="
              + ticketCode
              + "\nẢnh QR được đính kèm trong email này.");
      helper.addAttachment("ve-qr.png", new ByteArrayResource(qrPng), "image/png");
      sender.send(message);
    } catch (Exception ex) {
      log.warn("Không gửi được email vé {}. Khách vẫn có thể tải vé trên website.", ticketCode, ex);
    }
  }

  public void sendEventCancellation(String address, String eventTitle, String reason) {
    sendText(
        address,
        "Thông báo hủy sự kiện: " + eventTitle,
        "Sự kiện "
            + eventTitle
            + " đã bị hủy.\nLý do: "
            + reason
            + "\nVé QR không còn hiệu lực. Đăng nhập website để xem lịch sử vé.");
  }

  public void sendVerification(String address, String token) {
    sendText(
        address,
        "Xác minh email tài khoản SEMS",
        "Mở liên kết để xác minh email tài khoản: "
            + baseUrl
            + "/verify-email.html?token="
            + token
            + "\nLiên kết có hiệu lực trong 30 phút.");
  }

  public void sendConfirmationCode(String address, String code) {
    if (!isEnabled())
      throw new ApiException(HttpStatus.CONFLICT, "Hệ thống chưa bật gửi email.");
    try {
      var connection = settings.connection();
      JavaMailSenderImpl sender = sender(connection);
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(connection.fromAddress());
      helper.setTo(address);
      helper.setSubject("Mã xác minh email SEMS");
      helper.setText(
          "Mã xác minh của bạn: " + code
              + "\nMã có hiệu lực trong 10 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
      sender.send(message);
    } catch (Exception ex) {
      log.warn("Không gửi được mã xác minh tới {}.", address, ex);
      throw new ApiException(HttpStatus.BAD_GATEWAY,
          "Không gửi được mã xác minh. Hãy kiểm tra email hoặc liên hệ quản trị viên.");
    }
  }

  public void sendPasswordReset(String address, String token) {
    sendText(
        address,
        "Đặt lại mật khẩu SEMS",
        "Bạn đã yêu cầu đặt lại mật khẩu. Mở liên kết: "
            + baseUrl
            + "/reset-password.html?token="
            + token
            + "\nLiên kết có hiệu lực trong 20 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
  }

  public void sendPasswordChanged(String address) {
    sendText(
        address,
        "Mật khẩu SEMS đã được thay đổi",
        "Mật khẩu tài khoản SEMS của bạn đã được thay đổi. Nếu không phải bạn thực hiện, hãy liên"
            + " hệ quản trị viên.");
  }

  public void sendTest(String address) {
    if (!isEnabled())
      throw new ApiException(HttpStatus.CONFLICT, "Hãy bật gửi email và lưu cấu hình trước.");
    try {
      var connection = settings.connection();
      JavaMailSenderImpl sender = sender(connection);
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(connection.fromAddress());
      helper.setTo(address);
      helper.setSubject("Kiểm tra cấu hình email SEMS");
      helper.setText("SEMS đã kết nối và gửi email thử bằng cấu hình SMTP hiện tại.");
      sender.send(message);
    } catch (Exception ex) {
      log.warn("Không gửi được email thử tới {}.", address, ex);
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Không gửi được email thử. Kiểm tra máy chủ, tài khoản, mật khẩu ứng dụng và log"
              + " server.");
    }
  }

  private void sendText(String address, String subject, String content) {
    if (!isEnabled()) return;
    try {
      var connection = settings.connection();
      JavaMailSenderImpl sender = sender(connection);
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(connection.fromAddress());
      helper.setTo(address);
      helper.setSubject(subject);
      helper.setText(content);
      sender.send(message);
    } catch (Exception ex) {
      log.warn("Không gửi được email {} tới {}.", subject, address, ex);
    }
  }

  private JavaMailSenderImpl sender(MailSettingsService.Connection connection) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(connection.host());
    sender.setPort(connection.port());
    sender.setUsername(connection.username());
    sender.setPassword(connection.password());
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    Properties properties = sender.getJavaMailProperties();
    properties.put("mail.smtp.auth", "true");
    properties.put("mail.smtp.starttls.enable", "true");
    properties.put("mail.smtp.connectiontimeout", "5000");
    properties.put("mail.smtp.timeout", "5000");
    properties.put("mail.smtp.writetimeout", "5000");
    return sender;
  }
}
