package com.api.generator.auth;

import com.api.generator.config.EmailVerificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationProperties properties;
    private final JavaMailSender mailSender;

    public EmailVerificationService(EmailVerificationProperties properties,
                                    ObjectProvider<JavaMailSender> mailSender) {
        this.properties = properties;
        this.mailSender = mailSender.getIfAvailable();
    }

    public boolean sendVerification(String email, String token) {
        if (mailSender == null) {
            log.warn("Email verification requested for {}, but no mail sender is configured.", email);
            return false;
        }

        String verifyUrl = properties.getFrontendVerifyUrl()
                + "?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(email);
        message.setSubject("Verify your Api Generator account");
        message.setText("""
                Verify your Api Generator account:

                Open this link to verify your email:

                %s

                If the link does not open, go to %s and paste this verification code:

                %s

                This code expires in %d minutes.
                """.formatted(verifyUrl, properties.getFrontendVerifyUrl(), token, properties.getExpirationMinutes()));

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.error("Could not send verification email to {}", email, e);
            return false;
        }
    }

    public boolean sendPasswordReset(String email, String token) {
        if (mailSender == null) {
            log.warn("Password reset requested for {}, but no mail sender is configured.", email);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(email);
        message.setSubject("Reset your Api Generator password");
        message.setText("""
                Reset your Api Generator password:

                Open %s and paste this reset code:

                %s

                This code expires in %d minutes.
                """.formatted(properties.getResetPasswordFrontendUrl(), token, properties.getExpirationMinutes()));

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.error("Could not send password reset email to {}", email, e);
            return false;
        }
    }
}
