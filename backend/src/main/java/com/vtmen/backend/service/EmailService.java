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
     *    /**
     * Gửi email thông báo hàng đã vào tủ + mã PIN bí mật cho khách hàng.
     * Chạy bất đồng bộ để không block request.
     *
     * @param toEmail       địa chỉ email người nhận
     * @param customerName  tên khách hàng
     * @param orderCode     mã đơn hàng (VD: SK1A2B3C4D)
     * @param pinCode       mã PIN 6 chữ số
     * @param compartmentId số ngăn tủ robot (1, 2, 3...)
     * @param address       địa chỉ nhận hàng
     */
    @Async
    public void sendLockerPlacedConfirmation(String toEmail, String customerName,
                                            String orderCode, String pinCode,
                                            Integer compartmentId, String address) {
        if (mailSender == null) {
            log.warn("[Email] JavaMailSender không được cấu hình. Bỏ qua gửi email tới {}", toEmail);
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.info("[Email] Đơn hàng {} không có email người nhận. Bỏ qua gửi email.", orderCode);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderAddress, "Hệ thống robot giao hàng");
            helper.setReplyTo(senderAddress);
            helper.setTo(toEmail.trim());
            helper.setSubject("Thông báo giao hàng: Đơn " + orderCode + " - Mã nhận hàng: " + pinCode);

            String compText = (compartmentId != null && compartmentId > 0) ? String.valueOf(compartmentId) : "1";
            String addrText = (address != null && !address.isBlank()) ? address : "Điểm nhận hàng";
            String nameText = (customerName != null && !customerName.isBlank()) ? customerName : "Quý khách";

            String html = buildLockerEmailHtml(nameText, orderCode, pinCode, compText, addrText);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("[Email] Đã gửi mã bí mật nhận hàng cho đơn {} (ngăn {}) tới {}", orderCode, compText, toEmail);
        } catch (Exception e) {
            log.error("[Email] Không thể gửi email tới {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Gửi email xác nhận đơn hàng khi mới tạo (tùy chọn).
     */
    @Async
    public void sendOrderConfirmation(String toEmail, String customerName,
                                      String orderCode, String pinCode) {
        sendLockerPlacedConfirmation(toEmail, customerName, orderCode, pinCode, null, null);
    }

    private String buildLockerEmailHtml(String customerName, String orderCode, String pinCode,
                                        String compartment, String address) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>Thông báo giao hàng</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f5f7;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;padding:32px 16px;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                    <tr>
                      <td align="center">
                        <table width="100%%" style="max-width:520px;background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.05);border:1px solid #e5e7eb;border-top:4px solid #ef4444;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                          <!-- Header styled like App -->
                          <tr>
                            <td style="padding:28px 28px 20px;border-bottom:1px solid #f3f4f6;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                              <h1 style="margin:0;color:#111827;font-size:22px;font-weight:700;letter-spacing:-0.5px;line-height:1.2;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                Hệ thống robot giao hàng
                              </h1>
                              <p style="margin:4px 0 0;color:#6b7280;font-size:13px;font-weight:400;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                Quản lý giao hàng
                              </p>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:24px 28px;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                              <p style="margin:0 0 16px;color:#374151;font-size:15px;line-height:1.6;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                Xin chào <strong>%s</strong>, kiện hàng của bạn đã được đưa vào robot giao hàng thành công.
                              </p>

                              <!-- App-styled Order Card -->
                              <div style="background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;padding:20px;margin-bottom:20px;box-shadow:0 2px 6px rgba(0,0,0,0.02);font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:12px;">
                                  <tr>
                                    <td style="vertical-align:top;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                      <div style="font-size:16px;font-weight:700;color:#111827;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                        %s <span style="font-weight:400;color:#6b7280;">- %s</span>
                                      </div>
                                      <div style="font-size:13px;color:#6b7280;margin-top:4px;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                        Địa chỉ: %s
                                      </div>
                                    </td>
                                    <td align="right" style="vertical-align:top;">
                                      <span style="display:inline-block;background-color:#ffedd5;color:#c2410c;font-size:12px;font-weight:600;padding:4px 12px;border-radius:9999px;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                        Placed
                                      </span>
                                    </td>
                                  </tr>
                                </table>

                                <!-- PIN Pill styled exactly like the app -->
                                <div style="margin:14px 0 8px;">
                                  <span style="display:inline-block;background-color:#fee2e2;color:#ef4444;font-family:Consolas,Monaco,monospace;font-size:22px;font-weight:700;padding:6px 16px;border-radius:8px;letter-spacing:1.5px;">
                                    PIN: %s
                                  </span>
                                </div>

                                <div style="font-size:13px;color:#6b7280;font-weight:500;margin-top:8px;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                  Ngăn tủ: %s
                                </div>
                              </div>

                              <!-- Instructions block -->
                              <div style="background:#f9fafb;border:1px solid #f3f4f6;border-radius:12px;padding:16px;margin-bottom:20px;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                <div style="font-size:13px;font-weight:600;color:#111827;margin-bottom:10px;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                  Hướng dẫn nhận hàng:
                                </div>
                                <ol style="margin:0;padding-left:18px;color:#4b5563;font-size:13px;line-height:1.7;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                  <li style="margin-bottom:6px;">Kiện hàng hiện đã được đặt an toàn trong ngăn tủ của robot.</li>
                                  <li style="margin-bottom:6px;">Khi quý khách gửi lệnh <strong>Gọi giao hàng</strong> trên ứng dụng, robot sẽ bắt đầu di chuyển đến điểm nhận hàng.</li>
                                  <li style="margin-bottom:6px;">Khi robot đến nơi, chọn mục <strong>Nhận hàng</strong> trên màn hình robot.</li>
                                  <li style="margin-bottom:6px;">Nhập mã <strong>PIN: %s</strong>.</li>
                                  <li style="margin-bottom:6px;">Cửa <strong>Ngăn tủ: %s</strong> sẽ tự động mở để quý khách lấy hàng.</li>
                                  <li>Sau khi lấy hàng, vui lòng <strong>đóng chặt cửa ngăn tủ</strong> để hoàn tất quá trình nhận hàng.</li>
                                </ol>
                                <div style="margin-top:12px;padding:8px 12px;background:#fef2f2;border:1px solid #fee2e2;border-radius:8px;color:#b91c1c;font-size:12px;line-height:1.5;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                  <strong>Lưu ý:</strong> Quý khách nhớ đóng kín cửa ngăn tủ sau khi lấy hàng để robot có thể hoàn tất đơn hàng và tiếp tục phục vụ.
                                </div>
                              </div>

                              <p style="margin:0;color:#9ca3af;font-size:12px;line-height:1.5;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                <em>Mã bí mật chỉ dùng một lần và chỉ mở đúng ngăn tủ chứa kiện hàng này. Vui lòng không chia sẻ mã này cho người khác.</em>
                              </p>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background:#f9fafb;padding:16px 28px;border-top:1px solid #f3f4f6;text-align:center;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                              <p style="margin:0;color:#9ca3af;font-size:12px;font-family:Arial,Tahoma,'Segoe UI',sans-serif;">
                                Hệ thống robot giao hàng tự hành &copy; 2026
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                customerName,
                orderCode,
                customerName,
                address,
                pinCode,
                compartment,
                pinCode,
                compartment
        );
    }
}
