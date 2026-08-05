package com.example.agenticrag.security.detector;

import com.example.agenticrag.security.GuardCategory;

import java.util.List;

/**
 * Um controle determinístico: acha ocorrências, e só. <b>Não decide o que fazer</b> — quem
 * decide é o guard, consultando a ação configurada para (estágio, categoria).
 *
 * <p>Essa separação é o que permite o mesmo detector de PII mascarar na pergunta e bloquear na
 * ingestão sem código duplicado, e é o que deixa a política auditável em um arquivo de config
 * em vez de espalhada em {@code if}s.
 *
 * <p>Todos os detectores são <b>fora do system prompt</b>: rodam em Java, antes/depois do
 * modelo. Um prompt bem escrito é conselho; isto é controle — não dá para "convencer" um regex.
 */
public interface Detector {

    /** Id estável (SEC-xx) usado na matriz de evidências e nas métricas. */
    String controlId();

    GuardCategory category();

    /** Uma linha explicando o que o controle procura (aparece em {@code GET /security/controls}). */
    String description();

    /** Ocorrências no texto. Lista vazia = nada encontrado. Nunca lança. */
    List<DetectorMatch> find(String text);
}
