package com.vtmen.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${vtmen.mail.sender:no-reply@vtmen.local}")
    private String senderAddress;

    /**
     * Gửi email xác nhận đơn hàng + mã PIN bí mật cho khách hàng.
     * Chạy bất đồng bộ để không block request.
     *
     * @param toEmail     địa chỉ email người nhận
     * @param customerName tên khách hàng
     * @param orderCode   mã đơn hàng (VD: SK1A2B3C4D)
     * @param pinCode     mã PIN 6 chữ số
     */
    @Async
    public void sendOrderConfirmation(String toEmail, String customerName,
                                      String orderCode, String pinCode) {
        if (mailSender == null) {
            log.warn("[Email] JavaMailSender không được cấu hình. Bỏ qua gửi email tới {}", toEmail);
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderAddress);
            helper.setTo(toEmail);
            helper.setSubject("📦 Xác nhận đơn hàng " + orderCode + " – Mã nhận hàng của bạn");

            String html = buildEmailHtml(customerName, orderCode, pinCode);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("[Email] Đã gửi xác nhận đơn hàng {} tới {}", orderCode, toEmail);
        } catch (Exception e) {
            log.error("[Email] Không thể gửi email tới {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildEmailHtml(String customerName, String orderCode, String pinCode) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"/></head>
                <body style="margin:0;padding:0;background:#f4f4f7;font-family:'Helvetica Neue',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td align="center" style="padding:40px 0;">
                        <table width="480" cellpadding="0" cellspacing="0"
                               style="background:#ffffff;border-radius:12px;overflow:hidden;
                                      box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                          <!-- Header -->
                          <tr>
                            <td style="background:linear-gradient(135deg,#6366f1,#8b5cf6);
                                       padding:32px 40px;text-align:center;">
                              <h1 style="margin:0;color:#fff;font-size:22px;font-weight:700;">
                                📦 VTMen Delivery
                              </h1>
                              <p style="margin:6px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">
                                Robot giao hàng thông minh
                              </p>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:36px 40px;">
                              <p style="margin:0 0 12px;color:#374151;font-size:15px;">
                                Xin chào <strong>%s</strong>,
                              </p>
                              <p style="margin:0 0 24px;color:#6b7280;font-size:14px;line-height:1.6;">
                                Đơn hàng <strong>%s</strong> của bạn đã được tạo thành công.
                                Dưới đây là <strong>mã bí mật (PIN)</strong> để nhận hàng từ robot:
                              </p>

                              <!-- PIN box -->
                              <div style="background:#f0fdf4;border:2px dashed #16a34a;
                                          border-radius:12px;padding:24px;text-align:center;
                                          margin-bottom:24px;">
                                <p style="margin:0 0 6px;color:#15803d;font-size:12px;
                                          font-weight:600;letter-spacing:1px;text-transform:uppercase;">
                                  Mã PIN nhận hàng
                                </p>
                                <p style="margin:0;color:#166534;font-size:42px;font-weight:800;
                                          letter-spacing:12px;">
                                  %s
                                </p>
                              </div>

                              <p style="margin:0 0 8px;color:#6b7280;font-size:13px;line-height:1.6;">
                                ⚠️ <strong>Lưu ý bảo mật:</strong>
                              </p>
                              <ul style="margin:0 0 24px;padding-left:18px;
                                         color:#6b7280;font-size:13px;line-height:1.8;">
                                <li>Không chia sẻ mã PIN này với bất kỳ ai.</li>
                                <li>Sử dụng mã PIN này khi robot đến để mở ngăn nhận hàng.</li>
                                <li>Mã PIN chỉ có thể sử dụng một lần.</li>
                              </ul>

                              <p style="margin:0;color:#9ca3af;font-size:12px;">
                                Nếu bạn không tạo đơn hàng này, vui lòng liên hệ với chúng tôi ngay lập tức.
                              </p>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background:#f9fafb;padding:20px 40px;
                                       border-top:1px solid #e5e7eb;text-align:center;">
                              <p style="margin:0;color:#9ca3af;font-size:12px;">
                                VTMen Delivery System &copy; 2026
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(customerName, orderCode, pinCode);
    }
}
