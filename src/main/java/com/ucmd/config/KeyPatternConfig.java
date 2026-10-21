package com.ucmd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ucmd")
public class KeyPatternConfig {

    private List<PatternDef> keyPatterns = new ArrayList<>();

    public List<PatternDef> getKeyPatterns() { return keyPatterns; }
    public void setKeyPatterns(List<PatternDef> keyPatterns) { this.keyPatterns = keyPatterns; }

    public static class PatternDef {
        private String pattern;
        private String label;

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }
}