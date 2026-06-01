package com.bluesteel.application.service.email;

import com.bluesteel.application.model.email.EmailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds branded invitation emails (plain-text fallback + Blue Steel HTML) for both platform and
 * campaign invitations. The "Log in" call-to-action points at the configured frontend origin
 * ({@code cors.allowed-origins}); when several origins are configured the first is used.
 */
@Component
public class InvitationEmailFactory {

  private static final String PLATFORM_SUBJECT = "Your Blue Steel invitation";
  private static final String CAMPAIGN_SUBJECT = "Your Blue Steel campaign invitation";
  private static final String INTRO =
      "An account has been created for you on Blue Steel. Use the temporary credentials below to "
          + "sign in.";

  private final String loginUrl;

  public InvitationEmailFactory(@Value("${cors.allowed-origins:}") String allowedOrigins) {
    this.loginUrl = allowedOrigins.split(",")[0].strip();
  }

  /** Invitation to the platform (admin-invited user, D-051/D-070). */
  public EmailMessage platformInvitation(String email, String tempPassword) {
    return build(email, PLATFORM_SUBJECT, "You've been invited to Blue Steel", INTRO, tempPassword);
  }

  /** Invitation to a specific campaign (GM-invited member, D-064). */
  public EmailMessage campaignInvitation(String email, String tempPassword) {
    return build(
        email,
        CAMPAIGN_SUBJECT,
        "You've been invited to a Blue Steel campaign",
        INTRO,
        tempPassword);
  }

  private EmailMessage build(
      String email, String subject, String heading, String intro, String tempPassword) {
    String text = plainBody(heading, intro, email, tempPassword);
    String html = htmlBody(heading, intro, email, tempPassword);
    return new EmailMessage(email, subject, text, html);
  }

  private String plainBody(String heading, String intro, String email, String tempPassword) {
    return """
        %s

        %s

        Email: %s
        Temporary password: %s

        Log in: %s

        For your security, please log in and change this temporary password immediately.
        """
        .formatted(heading, intro, email, tempPassword, loginUrl);
  }

  private String htmlBody(String heading, String intro, String email, String tempPassword) {
    return HTML_TEMPLATE
        .replace("{{heading}}", escape(heading))
        .replace("{{intro}}", escape(intro))
        .replace("{{email}}", escape(email))
        .replace("{{password}}", escape(tempPassword))
        .replace("{{loginUrl}}", escape(loginUrl));
  }

  /** Escapes the five characters that can break HTML when interpolating dynamic values. */
  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  // Email clients ignore <style>/external CSS and flexbox — inline styles + tables only.
  private static final String HTML_TEMPLATE =
      """
      <!DOCTYPE html>
      <html lang="en">
        <body style="margin:0;padding:0;background-color:#f8fafc;">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0"\
       style="background-color:#f8fafc;padding:32px 16px;">
            <tr>
              <td align="center">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0"\
       style="max-width:480px;background-color:#ffffff;border:1px solid #e2e8f0;\
      border-radius:16px;padding:32px;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                  <tr>
                    <td style="padding-bottom:24px;font-size:20px;font-weight:700;\
      color:#0f172a;letter-spacing:-0.01em;">Blue&nbsp;Steel</td>
                  </tr>
                  <tr>
                    <td style="font-size:18px;font-weight:600;color:#0f172a;padding-bottom:8px;">{{heading}}</td>
                  </tr>
                  <tr>
                    <td style="font-size:14px;line-height:22px;color:#475569;padding-bottom:24px;">{{intro}}</td>
                  </tr>
                  <tr>
                    <td style="padding-bottom:24px;">
                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"\
       style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;">
                        <tr>
                          <td style="padding:16px;font-size:13px;color:#475569;">
                            <div style="margin-bottom:4px;">Email</div>
                            <div style="font-size:14px;color:#0f172a;font-weight:600;padding-bottom:16px;">{{email}}</div>
                            <div style="margin-bottom:4px;">Temporary password</div>
                            <div style="font-family:Consolas,Menlo,monospace;font-size:15px;\
      color:#0f172a;font-weight:700;">{{password}}</div>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding-bottom:24px;">
                      <a href="{{loginUrl}}" style="display:inline-block;background-color:#3b82f6;\
      color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;\
      padding:12px 24px;border-radius:8px;">Log in to Blue Steel</a>
                    </td>
                  </tr>
                  <tr>
                    <td style="font-size:13px;line-height:20px;color:#64748b;">
                      For your security, please log in and change this temporary password immediately.
                    </td>
                  </tr>
                </table>
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;padding:16px;">
                  <tr>
                    <td style="font-size:12px;color:#94a3b8;text-align:center;\
      font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">\
      Blue Steel — AI-assisted narrative memory for tabletop RPGs</td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
      </html>
      """;
}
