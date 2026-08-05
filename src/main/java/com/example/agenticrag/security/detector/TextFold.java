package com.example.agenticrag.security.detector;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalização <b>que preserva índices</b>: minúsculas e sem acentos, com o mesmo comprimento
 * do original.
 *
 * <p>Existe porque a normalização óbvia — {@code NFD} + remover {@code \p{M}} — muda o tamanho
 * da string, e aí os offsets do casamento não servem mais para mascarar o texto original
 * ("Endereço" tem 8 chars, a versão NFD tem 9). Aqui cada caractere é decomposto
 * individualmente e fica só a base, garantindo o mapeamento 1:1.
 *
 * <p>Ligaduras (ﬁ, œ) decompõem em mais de uma base: mantemos a primeira. É perda aceitável —
 * nenhum padrão dos detectores depende delas — e o contrato de comprimento é verificado.
 */
public final class TextFold {

    private TextFold() {
    }

    /** Minúsculo, sem diacríticos, mesmo {@code length()} da entrada. */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            char base = decomposed.isEmpty() ? c : decomposed.charAt(0);
            sb.append(Character.getType(base) == Character.NON_SPACING_MARK ? c : base);
        }
        // toLowerCase pode mudar o comprimento em alguns idiomas (ex.: 'İ' turco). Nesse caso
        // devolvemos o original: melhor perder a normalização do que corromper os offsets.
        return sb.length() == text.length() ? sb.toString() : text;
    }
}
