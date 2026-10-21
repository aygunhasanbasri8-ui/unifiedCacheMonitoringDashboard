package com.nkolay.ucmd.monitor;

// ucmd.key-patterns desenlerinde yalnızca '*' joker sayılır; Redis glob'undan farklı
// olarak '?' ve '[' literal eşleşir.
final class KeyPatternMatcher {

    private KeyPatternMatcher() {}

    static boolean matches(String keyName, String pattern) {
        if (pattern == null || pattern.equals("*")) return true;
        StringBuilder regex = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append("$");
        return keyName.matches(regex.toString());
    }
}
