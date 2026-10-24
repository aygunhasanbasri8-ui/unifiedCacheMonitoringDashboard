package com.ucmd.config;

import com.ucmd.enums.AlertMetric;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Alarm kuralları ve bildirim kanalları (ucmd.alerts.*).
 * <p>
 * Kurallar şimdilik yapılandırmada: yönetim ekranı ihtiyacı doğunca DB'ye
 * taşınabilir, değerlendirme tarafı kuralın nereden geldiğini bilmiyor.
 */
@Configuration
@ConfigurationProperties(prefix = "ucmd.alerts")
public class AlertConfig {

    private boolean enabled = true;
    /** Alarm sürdükçe bildirimin en sık kaç dakikada bir tekrarlanacağı. */
    private int repeatIntervalMinutes = 60;
    private int retentionDays = 90;
    private List<Rule> rules = new ArrayList<>();
    private Channels channels = new Channels();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRepeatIntervalMinutes() { return repeatIntervalMinutes; }
    public void setRepeatIntervalMinutes(int repeatIntervalMinutes) { this.repeatIntervalMinutes = repeatIntervalMinutes; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }
    public Channels getChannels() { return channels; }
    public void setChannels(Channels channels) { this.channels = channels; }

    public enum Operator {
        GT, LT;

        public boolean breached(double value, double threshold) {
            return this == GT ? value > threshold : value < threshold;
        }
    }

    public static class Rule {
        private String name;
        private AlertMetric metric;
        private Operator operator = Operator.GT;
        private double threshold;
        /** Koşul bu kadar dakika kesintisiz sürmeden alarm başlamaz (gürültüyü keser). */
        private int forMinutes = 0;
        private String severity = "WARNING";
        /** Boşsa tüm instance'lar; doluysa yalnızca bu adlar. */
        private List<String> instances = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public AlertMetric getMetric() { return metric; }
        public void setMetric(AlertMetric metric) { this.metric = metric; }
        public Operator getOperator() { return operator; }
        public void setOperator(Operator operator) { this.operator = operator; }
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        public int getForMinutes() { return forMinutes; }
        public void setForMinutes(int forMinutes) { this.forMinutes = forMinutes; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public List<String> getInstances() { return instances; }
        public void setInstances(List<String> instances) { this.instances = instances; }

        public boolean appliesTo(String instanceName) {
            return instances == null || instances.isEmpty() || instances.contains(instanceName);
        }
    }

    public static class Channels {
        private Telegram telegram = new Telegram();
        private Webhook slack = new Webhook();
        private Webhook webhook = new Webhook();
        private Email email = new Email();

        public Telegram getTelegram() { return telegram; }
        public void setTelegram(Telegram telegram) { this.telegram = telegram; }
        public Webhook getSlack() { return slack; }
        public void setSlack(Webhook slack) { this.slack = slack; }
        public Webhook getWebhook() { return webhook; }
        public void setWebhook(Webhook webhook) { this.webhook = webhook; }
        public Email getEmail() { return email; }
        public void setEmail(Email email) { this.email = email; }
    }

    public static class Telegram {
        private String botToken = "";
        private String chatId = "";

        public String getBotToken() { return botToken; }
        public void setBotToken(String botToken) { this.botToken = botToken; }
        public String getChatId() { return chatId; }
        public void setChatId(String chatId) { this.chatId = chatId; }
    }

    public static class Webhook {
        private String url = "";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class Email {
        private List<String> to = new ArrayList<>();
        private String from = "ucmd@localhost";

        public List<String> getTo() { return to; }
        public void setTo(List<String> to) { this.to = to; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
    }
}
