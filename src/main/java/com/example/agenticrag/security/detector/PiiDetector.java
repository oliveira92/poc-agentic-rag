package com.example.agenticrag.security.detector;

import com.example.agenticrag.security.GuardCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-02 — dado pessoal: CPF, CNPJ, telefone, e-mail e cartão.
 *
 * <p>Os detectores numéricos <b>validam o dígito verificador</b> (CPF/CNPJ) ou o Luhn (cartão)
 * em vez de confiar só no formato. Não é preciosismo: no domínio da PoC circulam números de
 * apólice, protocolo e sinistro com 11 dígitos, e um controle que berra a cada um deles é
 * desligado na primeira semana. Validar o dígito derruba o falso positivo praticamente a zero
 * — e é o que permite a política "mascara e segue" no lugar de "bloqueia tudo".
 */
@Component
public class PiiDetector implements Detector {

    /**
     * CPF com ou sem pontuação.
     *
     * <p>As bordas evitam casar o meio de um número maior, mas precisam distinguir o ponto que
     * <b>continua</b> o número do ponto que <b>encerra a frase</b>: com um simples
     * {@code (?![\d.-])}, "o CPF é 529.982.247-25." não era detectado — o ponto final matava o
     * casamento e o dado passava reto. Daí o par de lookarounds: nada de dígito ou hífen colado,
     * e ponto só conta como parte do número se vier seguido de dígito.
     */
    private static final Pattern CPF = Pattern.compile(
            "(?<![\\d-])(?<!\\d\\.)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?![\\d-])(?!\\.\\d)");

    private static final Pattern CNPJ = Pattern.compile(
            "(?<![\\d/-])(?<!\\d\\.)\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}(?![\\d/-])(?!\\.\\d)");

    /** Telefone BR: DDD opcionalmente entre parênteses, nono dígito opcional. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\d-])(?:\\+55[\\s-]?)?\\(?\\d{2}\\)?[\\s.-]?9?\\d{4}[\\s.-]?\\d{4}(?![\\d-])");

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** Cartão: 13–19 dígitos com separador opcional; confirmado pelo Luhn. */
    private static final Pattern CARD = Pattern.compile(
            "(?<![\\d-])(?:\\d[ -]?){12,18}\\d(?![\\d-])");

    @Override
    public String controlId() {
        return "SEC-02";
    }

    @Override
    public GuardCategory category() {
        return GuardCategory.PII;
    }

    @Override
    public String description() {
        return "Dado pessoal (CPF/CNPJ com dígito verificador, telefone, e-mail, cartão com Luhn).";
    }

    @Override
    public List<DetectorMatch> find(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<DetectorMatch> out = new ArrayList<>();
        collect(out, text, EMAIL, "email", s -> true);
        collect(out, text, CPF, "cpf", PiiDetector::validCpf);
        collect(out, text, CNPJ, "cnpj", PiiDetector::validCnpj);
        collect(out, text, CARD, "cartao", PiiDetector::validCard);
        collect(out, text, PHONE, "telefone", PiiDetector::plausiblePhone);
        return out;
    }

    private static void collect(List<DetectorMatch> out, String text, Pattern p, String label,
                                java.util.function.Predicate<String> valid) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            if (valid.test(m.group())) {
                out.add(new DetectorMatch(label, m.start(), m.end()));
            }
        }
    }

    // ---------- validações ----------

    private static String digits(String s) {
        return s.replaceAll("\\D", "");
    }

    /** CPF: dois dígitos verificadores módulo 11. Rejeita as sequências repetidas (111...). */
    static boolean validCpf(String raw) {
        String d = digits(raw);
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        for (int check = 9; check < 11; check++) {
            int sum = 0;
            for (int i = 0; i < check; i++) {
                sum += (d.charAt(i) - '0') * (check + 1 - i);
            }
            int digit = (sum * 10) % 11 % 10;
            if (digit != d.charAt(check) - '0') {
                return false;
            }
        }
        return true;
    }

    /** CNPJ: módulo 11 com os pesos 2..9 cíclicos. */
    static boolean validCnpj(String raw) {
        String d = digits(raw);
        if (d.length() != 14 || d.chars().distinct().count() == 1) {
            return false;
        }
        int[] weights = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        for (int check = 12; check < 14; check++) {
            int sum = 0;
            int offset = check == 12 ? 1 : 0;   // o 1º dígito usa 12 pesos, o 2º usa 13
            for (int i = 0; i < check; i++) {
                sum += (d.charAt(i) - '0') * weights[i + offset];
            }
            int rest = sum % 11;
            int digit = rest < 2 ? 0 : 11 - rest;
            if (digit != d.charAt(check) - '0') {
                return false;
            }
        }
        return true;
    }

    /** Luhn — e no mínimo 13 dígitos, para não pegar sequências curtas. */
    static boolean validCard(String raw) {
        String d = digits(raw);
        if (d.length() < 13 || d.length() > 19 || d.chars().distinct().count() == 1) {
            return false;
        }
        int sum = 0;
        boolean doubling = false;
        for (int i = d.length() - 1; i >= 0; i--) {
            int n = d.charAt(i) - '0';
            if (doubling) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            doubling = !doubling;
        }
        return sum % 10 == 0;
    }

    /**
     * Telefone não tem dígito verificador, então o filtro é estrutural: 10 ou 11 dígitos e DDD
     * brasileiro válido (11–99, sem os inexistentes começados em 0/1). Sem isso, qualquer
     * número de protocolo de 10 dígitos viraria "telefone".
     */
    static boolean plausiblePhone(String raw) {
        String d = digits(raw);
        if (d.startsWith("55") && d.length() > 11) {
            d = d.substring(2);
        }
        if (d.length() != 10 && d.length() != 11) {
            return false;
        }
        int ddd = Integer.parseInt(d.substring(0, 2));
        if (ddd < 11) {
            return false;
        }
        // celular (11 dígitos) começa com 9; fixo (10) começa em 2..5.
        char first = d.charAt(2);
        return d.length() == 11 ? first == '9' : (first >= '2' && first <= '5');
    }
}
