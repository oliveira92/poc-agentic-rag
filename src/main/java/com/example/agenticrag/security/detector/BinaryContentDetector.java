package com.example.agenticrag.security.detector;

import com.example.agenticrag.security.GuardCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-01 — payload binário/documento onde só se espera texto (o "não pode enviar .pdf/.png").
 *
 * <p>A checagem é por <b>assinatura de conteúdo</b>, não por extensão nem por
 * {@code Content-Type}: os dois são declarados por quem envia, e renomear {@code laudo.pdf}
 * para {@code readme.md} é o bypass de dez segundos. Os magic bytes estão no arquivo.
 *
 * <p>Como o corpo chega aqui já decodificado como texto, os bytes altos das assinaturas viram
 * {@code U+FFFD}. Por isso a busca é pelo pedaço que <b>sobrevive à decodificação</b> — o ASCII
 * de {@code %PDF-}, {@code PNG}, {@code GIF8}, {@code PK} — e a <b>densidade de caracteres
 * não-textuais</b> cobre o resto, inclusive formatos fora desta lista.
 */
@Component
public class BinaryContentDetector implements Detector {

    /** Fração de caracteres de controle/substituição a partir da qual o conteúdo não é texto. */
    private static final double NON_TEXT_RATIO_FLOOR = 0.02;

    /** Amostra examinada pela heurística — o suficiente para decidir sem varrer 200 KB. */
    private static final int SAMPLE_CHARS = 4096;

    /** Onde uma assinatura ainda conta como "início do arquivo". */
    private static final int SIGNATURE_WINDOW = 16;

    /**
     * Assinatura procurada: o marcador ASCII e o deslocamento máximo em que ele ainda conta.
     *
     * @param label    rótulo reportado
     * @param marker   trecho que sobrevive à decodificação em texto
     * @param maxIndex maior posição aceita ({@code 0} = tem de ser o primeiro caractere)
     */
    private record Signature(String label, String marker, int maxIndex) {
    }

    /**
     * Marcadores curtos ({@code PK}, {@code ELF}) exigem posição exata: soltos numa janela de 16
     * caracteres, casariam com texto comum ("PK do projeto…"). Os longos podem flutuar, porque em
     * JPEG o {@code JFIF}/{@code Exif} aparece alguns bytes depois do início do arquivo.
     */
    private static final List<Signature> SIGNATURES = List.of(
            new Signature("pdf", "%PDF-", 0),
            new Signature("png", "PNG", 1),
            new Signature("gif", "GIF8", 0),
            new Signature("jfif", "JFIF", SIGNATURE_WINDOW),
            new Signature("exif", "Exif", SIGNATURE_WINDOW),
            new Signature("zip_ou_office", "PK", 0),          // .zip, .docx, .xlsx, .jar
            new Signature("executavel_elf", "ELF", 1),
            new Signature("rar", "Rar!", 0),
            new Signature("rtf", "{\\rtf", 0),
            new Signature("postscript", "%!PS", 0),
            new Signature("ogg", "OggS", 0),
            new Signature("riff", "RIFF", 0));                // .wav, .avi, .webp

    /** Anexo em base64 embutido no corpo (o mesmo arquivo, só que codificado). */
    private static final Pattern DATA_URI = Pattern.compile(
            "(?i)data:(?:application|image|video|audio)/[a-z0-9.+-]+;base64,[A-Za-z0-9+/=]{32,}");

    @Override
    public String controlId() {
        return "SEC-01";
    }

    @Override
    public GuardCategory category() {
        return GuardCategory.BINARY;
    }

    @Override
    public String description() {
        return "Documento/binário (PDF, PNG, JPEG, ZIP/Office, executável) detectado por magic "
                + "bytes, data URI base64 ou densidade de bytes não-textuais.";
    }

    @Override
    public List<DetectorMatch> find(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<DetectorMatch> out = new ArrayList<>();

        String head = text.substring(0, Math.min(text.length(), SIGNATURE_WINDOW));
        for (Signature s : SIGNATURES) {
            int at = head.indexOf(s.marker());
            if (at >= 0 && at <= s.maxIndex()) {
                out.add(new DetectorMatch(s.label(), at, at + s.marker().length()));
            }
        }
        Matcher data = DATA_URI.matcher(text);
        while (data.find()) {
            out.add(new DetectorMatch("anexo_base64", data.start(), data.end()));
        }
        if (out.isEmpty() && nonTextRatio(text) > NON_TEXT_RATIO_FLOOR) {
            out.add(new DetectorMatch("binario_nao_identificado", 0, Math.min(text.length(), 1)));
        }
        return out;
    }

    /**
     * Fração de caracteres que texto legítimo não tem: controles (exceto {@code \n \r \t}) e o
     * caractere de substituição, que é o que sobra quando bytes binários passam por um decoder.
     */
    static double nonTextRatio(String text) {
        int limit = Math.min(text.length(), SAMPLE_CHARS);
        if (limit == 0) {
            return 0;
        }
        int bad = 0;
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (c == '�' || Character.isISOControl(c)) {
                bad++;
            }
        }
        return (double) bad / limit;
    }
}
