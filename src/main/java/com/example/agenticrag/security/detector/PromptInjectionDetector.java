package com.example.agenticrag.security.detector;

import com.example.agenticrag.security.GuardCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-04 — injeção de prompt: sobrescrita de instrução, extração do prompt de sistema e troca
 * de papel.
 *
 * <p>É o controle que mais depende de <b>estar fora do system prompt</b>. Instruir o modelo a
 * "não revelar o prompt de sistema" é pedir para o alvo do ataque se defender sozinho — e ele
 * cede, porque a mesma capacidade que o faz seguir a instrução do desenvolvedor o faz seguir a
 * do atacante. Aqui a pergunta é barrada antes de existir chamada ao modelo.
 *
 * <p>Padrões casam sobre o texto <b>dobrado</b> ({@link TextFold}): "IGNORE as Instruções" e
 * "ignore as instrucoes" são a mesma tentativa, e trocar acento não pode ser um bypass.
 * Ainda assim isto é uma lista de padrões — reformulações inéditas passam (risco residual
 * documentado; a mitigação é o juiz LLM em série, não este detector sozinho).
 */
@Component
public class PromptInjectionDetector implements Detector {

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        // 1) Sobrescrita explícita de instruções anteriores.
        PATTERNS.put("override_instrucoes", Pattern.compile(
                "(?:ignore|ignora|desconsidere|desconsidera|esqueca|esqueça|disregard|forget|"
                        + "override)\\s+(?:\\w+\\s+){0,4}"
                        + "(?:instru(?:coes|cao)|regras|orienta(?:coes|cao)|comandos|prompts?|"
                        + "instructions?|rules?|guidelines?)"));

        // 2) Extração do prompt de sistema / das instruções internas.
        PATTERNS.put("exfil_system_prompt", Pattern.compile(
                "(?:imprim|mostr|exib|revel|repit|list|envi|print|show|reveal|repeat|output|display|"
                        + "dump)\\w*\\s+(?:\\w+\\s+){0,6}"
                        + "(?:prompt\\s+de\\s+sistema|system\\s*prompt|prompt\\s+do\\s+sistema|"
                        + "suas\\s+instru(?:coes|cao)|instru(?:coes|cao)\\s+(?:de\\s+sistema|internas|"
                        + "iniciais|anteriores)|seu\\s+prompt|initial\\s+instructions)"));

        // 3) Pedido de reprodução literal do contexto/histórico (A-03 do dataset).
        PATTERNS.put("repeticao_literal", Pattern.compile(
                "(?:repit|reproduz|transcrev|copi|repeat|reproduce|transcribe)\\w*\\s+"
                        + "(?:\\w+\\s+){0,4}(?:literalmente|na\\s+integra|palavra\\s+por\\s+palavra|"
                        + "verbatim|exactly|em\\s+json|as\\s+json)"
                        + "|(?:tudo|todo\\s+o\\s+conteudo|everything|all\\s+content)\\s+"
                        + "(?:\\w+\\s+){0,4}(?:recebeu|recebido|received|antes\\s+desta|"
                        + "before\\s+this|em\\s+memoria|in\\s+memory|no\\s+contexto)"));

        // 4) Troca de papel / jailbreak nominal.
        PATTERNS.put("role_override", Pattern.compile(
                "(?:voce\\s+(?:agora\\s+)?e\\s+o?\\s*|you\\s+are\\s+now\\s+|aja\\s+como\\s+|"
                        + "atue\\s+como\\s+|act\\s+as\\s+|finja\\s+(?:que\\s+)?)"
                        + "(?:\\w+\\s+){0,3}(?:devmode|dev\\s*mode|dan\\b|jailbreak|sem\\s+restri|"
                        + "sem\\s+limites|unrestricted|no\\s+restrictions|developer\\s+mode|"
                        + "modo\\s+desenvolvedor|root|admin|sudo)"
                        + "|\\b(?:devmode|dev\\s*mode|developer\\s+mode|modo\\s+desenvolvedor|"
                        + "jailbreak|do\\s+anything\\s+now)\\b"));

        // 5) Delimitador falso: fingir ser turno de sistema/desenvolvedor.
        PATTERNS.put("delimitador_falso", Pattern.compile(
                "(?:^|\\n)\\s*(?:\\[|<|#{1,3}\\s*)?(?:system|assistant|developer|sistema)\\s*"
                        + "(?:\\]|>|:)\\s*|<\\|im_(?:start|end)\\|>|\\[/?inst\\]"));
    }

    @Override
    public String controlId() {
        return "SEC-04";
    }

    @Override
    public GuardCategory category() {
        return GuardCategory.PROMPT_INJECTION;
    }

    @Override
    public String description() {
        return "Sobrescrita de instruções, extração do prompt de sistema, pedido de repetição "
                + "literal do contexto, troca de papel (DevMode/DAN) e delimitador de turno falso.";
    }

    @Override
    public List<DetectorMatch> find(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String folded = TextFold.fold(text);
        List<DetectorMatch> out = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : PATTERNS.entrySet()) {
            Matcher m = e.getValue().matcher(folded);
            while (m.find()) {
                out.add(new DetectorMatch(e.getKey(), m.start(), m.end()));
            }
        }
        return out;
    }
}
