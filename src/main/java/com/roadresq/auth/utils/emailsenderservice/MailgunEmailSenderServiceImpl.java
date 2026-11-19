package com.roadresq.auth.utils.emailsenderservice;

import com.roadresq.auth.utils.exception.ErrorProcessingRequestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service("mailgunEmailSenderServiceImpl")
public class MailgunEmailSenderServiceImpl implements EmailSenderService {

    private static final Logger log =
            LoggerFactory.getLogger(MailgunEmailSenderServiceImpl.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String configuredSender;
    private final boolean mailEnabled;

    public MailgunEmailSenderServiceImpl(
            JavaMailSender mailSender,
            SpringTemplateEngine templateEngine,
            @Value("${app.mail-from:}") String configuredSender,
            @Value("${app.mail-enabled:false}") boolean mailEnabled
    ) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.configuredSender = configuredSender;
        this.mailEnabled = mailEnabled;
    }

    @Override
    public void sendHtmlEmail(
            String senderEmail, String recipientEmail, String subject, String body
    ) {
        deliver(senderEmail, recipientEmail, subject, body, true);
    }

    @Override
    public void sendEmail(
            String senderEmail, String recipientEmail, String subject, String body
    ) {
        deliver(senderEmail, recipientEmail, subject, body, false);
    }

    @Override
    public void sendAccountActivationEmail(
            String emailAddress, String id, String fullName, String otp
    ) {
        String body = templateEngine.process("account-activation", codeContext(fullName, otp));
        sendHtmlEmail(configuredSender, emailAddress, "Road-ResQ | Verify your email", body);
    }

    @Override
    public void sendPasswordChangeEmail(
            String emailAddress, String userId, String otp, String fullName
    ) {
        String body = templateEngine.process("password-change", codeContext(fullName, otp));
        sendHtmlEmail(configuredSender, emailAddress, "Road-ResQ | Confirm your password change", body);
    }

    @Override
    public void sendPasswordResetEmail(
            String emailAddress, String userId, String otp, String fullName
    ) {
        String body = templateEngine.process("password-reset", codeContext(fullName, otp));
        sendHtmlEmail(configuredSender, emailAddress, "Road-ResQ | Reset your password", body);
    }

    @Override
    public void sendAdminPasswordResetEmail(
            String emailAddress, String userId, String otp, String fullName
    ) {
        String body = templateEngine.process("admin-password-reset", codeContext(fullName, otp));
        sendHtmlEmail(configuredSender, emailAddress, "Road-ResQ | Reset your admin password", body);
    }

    @Override
    public void sendAccountSetupCompletionEmail(String emailAddress, String fullName) {
        String body = templateEngine.process("account-setup-completion", emailContext(fullName));
        sendHtmlEmail(configuredSender, emailAddress, "Welcome to Road-ResQ", body);
    }

    @Override
    public void sendAdminAccountActivationEmail(
            String emailAddress, String fullName, String password, String otp
    ) {
        // The password parameter is retained for interface compatibility.
        String body = templateEngine.process("admin-account-activation", codeContext(fullName, otp));
        sendHtmlEmail(configuredSender, emailAddress, "Road-ResQ | Verify your admin account", body);
    }

    private void deliver(
            String sender, String recipient, String subject, String body, boolean html
    ) {
        String from = isBlank(sender) ? configuredSender : sender;
        if (isBlank(from) || isBlank(recipient) || isBlank(subject) || body == null
                || hasLineBreak(from) || hasLineBreak(recipient) || hasLineBreak(subject)) {
            throw new ErrorProcessingRequestException("Invalid email configuration or message");
        }

        try {
            InternetAddress fromAddress = new InternetAddress(from.trim(), true);
            InternetAddress toAddress = new InternetAddress(recipient.trim(), true);
            fromAddress.validate();
            toAddress.validate();

            boolean brandedHtml = html && body.contains("cid:roadresq-tow-truck");
            ClassPathResource truck = new ClassPathResource("email/tow-truck.png");
            if (brandedHtml && !truck.exists()) {
                throw new ErrorProcessingRequestException("Missing email image: email/tow-truck.png");
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, brandedHtml, StandardCharsets.UTF_8.name());
            helper.setValidateAddresses(true);
            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(body, html);

            if (brandedHtml) {
                helper.addInline("roadresq-tow-truck", truck, "image/png");
            }

            mailSender.send(message);
        } catch (MessagingException | MailException | IllegalArgumentException exception) {
            // Do not log message bodies, OTPs, passwords or SMTP credentials.
            log.warn("Email delivery failed: {}", exception.getClass().getSimpleName());
            throw new ErrorProcessingRequestException(
                    "Unable to send email. Please try again later.");
        }
    }

    private Context emailContext(String fullName) {
        String name = isBlank(fullName) ? "there" : fullName.trim();
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("greeting", "Hi " + name + ",");
        return context;
    }

    private Context codeContext(String fullName, String otp) {
        if (isBlank(otp)) {
            throw new ErrorProcessingRequestException("Email verification code is missing");
        }
        Context context = emailContext(fullName);
        context.setVariable("otp", otp);
        return context;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}