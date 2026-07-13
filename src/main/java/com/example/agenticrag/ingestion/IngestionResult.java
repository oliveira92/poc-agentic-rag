package com.example.agenticrag.ingestion;

/**
 * Resumo do resultado de uma ingestão.
 *
 * @param componentId      componente ingerido
 * @param endpointsIndexed nº de endpoints virados documento
 * @param readmeChunks     nº de chunks de README indexados
 * @param totalDocuments   total de documentos gravados no vector store
 * @param skipped          true se nada mudou (mesmo hash) e a ingestão foi pulada
 */
public record IngestionResult(
        String componentId,
        int endpointsIndexed,
        int readmeChunks,
        int totalDocuments,
        boolean skipped) {
}
