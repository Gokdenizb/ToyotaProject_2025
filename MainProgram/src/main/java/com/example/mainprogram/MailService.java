package com.example.mainprogram;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class MailService {
    @Autowired
    private JavaMailSender mailSender;

    public void sendAlert(String provider, Duration duration) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("bayikgokdeniz@gmail.com");
            message.setSubject("ALERT: Veri Kesintisi");
            message.setText("Sağlayıcı: " + provider + " son " + duration.getSeconds() + " saniyedir veri göndermiyor.");

            mailSender.send(message);
            System.out.println("✅ Mail sent successfully");
        } catch (Exception e) {
            System.err.println("❌ Mail send failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
