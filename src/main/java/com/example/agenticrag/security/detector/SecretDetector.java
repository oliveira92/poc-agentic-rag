package com.example.agenticrag.security.detector;

import com.example.agenticrag.security.GuardCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-03 — credenciais: chaves de API, tokens, senhas e chaves privadas.
 *
 * <p>Duas famílias de padrão, porque falham por motivos diferentes:
 * <ul>
 *   <li><b>formato conhecido</b> ({@code sk-ant-…}, {@code AKIA…}, JWT, {@code ghp_…}): precisão
 *       altíssima, recall limitado ao que já se conhece;</li>
 *   <li><b>atribuição genérica</b> ({@code senha: …}, {@code api_key = …}): pega o segredo de
 *       formato desconhecido — inclusive o interno — ao custo de algum falso positivo.</li>
 * </ul>
 *
 * <p>O mesmo detector é reusado no teste que varre o repositório atrás de segredo versionado:
 * a regra que protege o runtime é a que protege o commit.
 */
@Component
public class SecretDetector implements Detector {

    /** Rótulo → padrão. LinkedHashMap para o rótulo mais específico ser tentado primeiro. */
    private static final Map<String, Pattern> PATTERNS = new java.util.LinkedHashMap<>();

    static {
        PATTERNS.put("chave_privada", Pattern.compile(
                "-----BEGIN[A-Z ]*PRIVATE KEY-----"));
        PATTERNS.put("api_key_anthropic", Pattern.compile("sk-ant-[A-Za-z0-9_-]{16,}"));
        PATTERNS.put("api_key_openai", Pattern.compile("sk-(?:proj-)?[A-Za-z0-9_-]{20,}"));
        PATTERNS.put("aws_access_key", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"));
        PATTERNS.put("github_token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{20,}\\b"));
        PATTERNS.put("google_api_key", Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"));
        PATTERNS.put("slack_token", Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"));
        PATTERNS.put("jwt", Pattern.compile(
                "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"));
        PATTERNS.put("bearer_token", Pattern.compile(
                "(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{20,}"));
        // URI com credencial embutida: postgres://user:senha@host, mongodb://…  // NOSECRET: exemplo
        PATTERNS.put("uri_com_credencial", Pattern.compile(
                "\\b[a-z][a-z0-9+.-]{2,}://[^\\s:@/]+:[^\\s:@/]{3,}@[^\\s/]+"));
        // Atribuição genérica. Exige um valor "não-placeholder" (ver isPlaceholder).
        PATTERNS.put("credencial_atribuida", Pattern.compile(
                "(?i)\\b(?:senha|password|passwd|pwd|secret|client[_-]?secret|api[_-]?key|"
                        + "access[_-]?token|auth[_-]?token|private[_-]?key)\\b\\s*[:=]\\s*[\"']?([^\\s\"',;]{6,})"));
    }

    /**
     * Valores que aparecem em exemplo/documentação e não são segredo. Sem esta lista, o próprio
     * {@code .env.example} do repositório (que existe justamente para NÃO versionar segredo)
     * seria reportado como vazamento — e um controle que acusa o arquivo-exemplo perde a
     * credibilidade que precisa ter quando acusa de verdade.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)^(?:x{3,}|\\*{3,}|\\.{3,}|<[^>]*>|\\$\\{[^}]*}|os\\.environ/.*|"
                    + "(?:my|your|sua|seu|the)?[_-]?(?:secret|password|senha|token|key|chave)|"
                    + "changeme|placeholder|dummy|example|exemplo|redacted|troque[_-]?em[_-]?prod|"
                    + "sk-ant-x+|sk-x+|pk-lf-x+|sk-lf-x+|sk-local-dev|not-set)$");

    @Override
    public String controlId() {
        return "SEC-03";
    }

    @Override
    public GuardCategory category() {
        return GuardCategory.SECRET;
    }

    @Override
    public String description() {
        return "Credencial: chave de API de formato conhecido, JWT, chave privada, URI com senha "
                + "ou atribuição do tipo 'senha=...'.";
    }

    @Override
    public List<DetectorMatch> find(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<DetectorMatch> out = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : PATTERNS.entrySet()) {
            Matcher m = e.getValue().matcher(text);
            while (m.find()) {
                // Grupo 1 (quando existe) é só o VALOR: mascarar o par rótulo+valor inteiro
                // apagaria o rótulo e deixaria o texto incompreensível para quem revisa.
                int start = m.groupCount() >= 1 && m.start(1) >= 0 ? m.start(1) : m.start();
                int end = m.groupCount() >= 1 && m.end(1) >= 0 ? m.end(1) : m.end();
                String value = text.substring(start, end);
                if (!isPlaceholder(value) && !isCodeEcho(m.group(), value)) {
                    out.add(new DetectorMatch(e.getKey(), start, end));
                }
            }
        }
        return out;
    }

    /** Valor claramente ilustrativo (placeholder de exemplo/env) — não é segredo. */
    public static boolean isPlaceholder(String value) {
        String v = value.trim();
        return v.isEmpty() || PLACEHOLDER.matcher(v).matches();
    }

    /**
     * Descarta o eco de identificador que a regra genérica produz em <b>código</b>:
     * {@code this.apiKey = apiKey;} casa "api key" seguido de "=" seguido de um valor com 6+
     * caracteres, e é só uma atribuição de campo.
     *
     * <p>Encontrado pelo próprio teste que varre o repositório — a regra genérica acusava o
     * construtor de um cliente HTTP. Duas condições, ambas conservadoras: o valor <b>repete</b>
     * o nome da chave, ou o valor tem cara de expressão (parênteses). Um segredo de verdade não
     * se chama igual ao próprio rótulo.
     */
    static boolean isCodeEcho(String fullMatch, String value) {
        if (value.indexOf('(') >= 0 || value.indexOf(')') >= 0) {
            return true;
        }
        String key = fullMatch.length() > value.length()
                ? fullMatch.substring(0, fullMatch.length() - value.length())
                : "";
        return !key.isEmpty() && alphanumeric(key).equals(alphanumeric(value));
    }

    private static String alphanumeric(String s) {
        return s.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }
}
