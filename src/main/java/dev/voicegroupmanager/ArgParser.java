package dev.voicegroupmanager;

import java.util.ArrayList;
import java.util.List;

/** Splits command arguments, keeping double-quoted sections together. */
final class ArgParser {
    /** One argument. {@code quoted} is true when the player wrapped it in double quotes. */
    record Token(String text, boolean quoted) { }

    private ArgParser() { }

    static List<Token> tokenize(String value) {
        List<Token> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean sawQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                sawQuote = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    out.add(new Token(current.toString(), sawQuote));
                    current.setLength(0);
                }
                sawQuote = false;
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) out.add(new Token(current.toString(), sawQuote));
        return out;
    }

    static List<String> split(String value) {
        return tokenize(value).stream().map(Token::text).toList();
    }

    /** True unless {@code value} is made only of characters Brigadier accepts in an unquoted word. */
    static boolean needsQuotes(String value) {
        if (value.isEmpty()) return true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean plain = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || c == '_' || c == '-' || c == '.' || c == '+';
            if (!plain) return true;
        }
        return false;
    }

    static String unquote(String value) {
        return value.replaceAll("^\"|\"$", "");
    }

    /** Joins the text of the tokens from {@code from} onwards with single spaces. */
    static String join(List<Token> tokens, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < tokens.size(); i++) {
            if (i > from) sb.append(' ');
            sb.append(tokens.get(i).text());
        }
        return sb.toString();
    }
}
