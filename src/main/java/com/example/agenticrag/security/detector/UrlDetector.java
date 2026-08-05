package com.example.agenticrag.security.detector;

import com.example.agenticrag.security.GuardCategory;
import com.example.agenticrag.security.SecurityProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-05 — URL na entrada.
 *
 * <p>Uma URL numa pergunta é um convite a duas coisas ruins: <b>injeção indireta</b> ("consulte
 * este link e siga as instruções de lá") e <b>exfiltração</b> (o modelo induzido a "consultar"
 * um endereço com o dado sensível na query string).
 *
 * <p>Mas este é um agente para <b>desenvolvedores</b>, e desenvolvedor cola link o tempo todo —
 * a doc do portal, o repositório, a issue. Bloquear toda URL aqui teria recall perfeito e um
 * custo que ninguém aceitaria na segunda semana. Por isso existe a <b>allow-list de domínios</b>
 * ({@code app.security.url-allowlist}): os endereços internos que já são fonte da base passam;
 * o resto, não. Sem allow-list configurada o comportamento é o estrito — bloquear tudo.
 *
 * <p>Escopo deliberado: <b>não</b> roda na ingestão. README tem link por natureza e a base não é
 * buscada na web — bloquear ali quebraria o caso de uso sem reduzir risco real.
 */
@Component
public class UrlDetector implements Detector {

    /**
     * O lookbehind de {@code @} e afins é o que separa "domínio" de "sufixo de e-mail":
     * {@code maria@empresa.com.br} tem {@code empresa.com.br} dentro, e sem esse cuidado toda
     * pergunta com e-mail seria bloqueada como se trouxesse um link.
     */
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:[a-z][a-z0-9+.-]{1,10}://[^\\s<>\"]+"          // esquema://…  (http, ftp, file…)
                    + "|\\bwww\\.[^\\s<>\"]+"                              // www.…
                    + "|data:[a-z]+/[a-z0-9.+-]+;base64,[A-Za-z0-9+/=]{16,}"  // data URI embutida
                    + "|(?<![@\\w.-])[a-z0-9-]+(?:\\.[a-z0-9-]+)*"          // domínio nu, nunca depois de @
                    + "\\.(?:com|net|org|io|dev|br|gov|edu|ai|co)(?![a-z])"
                    + "(?::\\d{2,5})?(?:/[^\\s<>\"]*)?)");

    /** Extrai o host de um casamento, para conferir contra a allow-list. */
    private static final Pattern HOST = Pattern.compile(
            "(?i)^(?:[a-z][a-z0-9+.-]{1,10}://)?([a-z0-9.-]+)");

    private final List<String> allowedHosts;

    public UrlDetector(SecurityProperties props) {
        this.allowedHosts = props.urlAllowlist() == null
                ? List.of()
                : props.urlAllowlist().stream()
                        .map(h -> h.toLowerCase(Locale.ROOT).strip())
                        .filter(h -> !h.isBlank())
                        .toList();
    }

    @Override
    public String controlId() {
        return "SEC-05";
    }

    @Override
    public GuardCategory category() {
        return GuardCategory.URL;
    }

    @Override
    public String description() {
        return "URL, domínio nu ou data URI fora da allow-list de domínios internos — vetor de "
                + "injeção indireta e exfiltração.";
    }

    @Override
    public List<DetectorMatch> find(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<DetectorMatch> out = new ArrayList<>();
        Matcher m = URL.matcher(text);
        while (m.find()) {
            if (!isAllowed(m.group())) {
                out.add(new DetectorMatch("url", m.start(), m.end()));
            }
        }
        return out;
    }

    /**
     * Casa o host contra a allow-list por sufixo de rótulo — {@code portal.example.com} libera
     * {@code docs.portal.example.com}, mas não {@code portal.example.com.evil.io}, porque a
     * comparação exige que o sufixo comece em um ponto. É a diferença entre uma allow-list e um
     * {@code contains()} que o atacante contorna registrando um domínio.
     */
    private boolean isAllowed(String match) {
        if (allowedHosts.isEmpty()) {
            return false;
        }
        Matcher h = HOST.matcher(match);
        if (!h.find()) {
            return false;
        }
        String host = h.group(1).toLowerCase(Locale.ROOT);
        for (String allowed : allowedHosts) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }
}
