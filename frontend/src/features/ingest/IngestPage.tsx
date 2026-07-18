import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { ingest, ingestReadme } from '../../api/client';
import type { IngestionResult } from '../../api/types';
import { useComponentId } from '../../App';
import { Badge, Spinner } from '../../components/ui';
import { useToast } from '../../lib/toast';

/** Ingestão: portal (API + README) → chunks → embeddings locais → pgvector. Idempotente. */
export function IngestPage() {
  const componentId = useComponentId();
  const { toast } = useToast();
  const [readme, setReadme] = useState('');
  const [last, setLast] = useState<IngestionResult | null>(null);

  const run = useMutation({
    mutationFn: () => (readme.trim() ? ingestReadme(componentId, readme) : ingest(componentId)),
    onSuccess: (res) => {
      setLast(res);
      toast(
        res.skipped
          ? 'Conteúdo inalterado — ingestão pulada (idempotência por hash)'
          : `Ingerido: ${res.totalDocuments} documentos indexados`,
        'success',
      );
    },
    onError: (err) => toast(`Falha na ingestão: ${err.message}`, 'error'),
  });

  return (
    <div className="page">
      <div className="page__head">
        <h1 className="page__title">Ingestão de componente</h1>
        <span className="topbar__spacer" />
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => run.mutate()}
          disabled={run.isPending}
        >
          {run.isPending ? <Spinner small /> : '📥'} Ingerir {componentId}
        </button>
      </div>
      <p className="page__desc">
        Busca os metadados estruturados do portal (overview + endpoints) e o README, gera
        embeddings <strong>locais</strong> (ONNX multilíngue) e indexa no pgvector. Reingerir
        sem mudança é <strong>idempotente</strong>; conhecimento aprovado (LLM_APPROVED) é
        sempre preservado.
      </p>

      <div className="card">
        <h2 className="card__title">README próprio (opcional)</h2>
        <p className="faint" style={{ marginTop: -6, marginBottom: 10 }}>
          Cole um markdown para usar no lugar do README do portal (substitui as fontes
          primárias, preserva o conhecimento aprovado). Vazio = usa o README do portal.
        </p>
        <textarea
          className="textarea mono"
          rows={8}
          style={{ width: '100%' }}
          placeholder="# Meu componente&#10;&#10;## Autenticação&#10;…"
          value={readme}
          onChange={(e) => setReadme(e.target.value)}
          spellCheck={false}
        />
      </div>

      {last && (
        <div className="card">
          <h2 className="card__title">Último resultado</h2>
          <div className="row">
            <Badge tone="neutral">{last.componentId}</Badge>
            <Badge tone="violet">{last.endpointsIndexed} endpoints</Badge>
            <Badge tone="cyan">{last.readmeChunks} chunks de README</Badge>
            <Badge tone={last.skipped ? 'amber' : 'green'}>
              {last.skipped ? 'pulada (sem mudança)' : `${last.totalDocuments} docs indexados`}
            </Badge>
          </div>
        </div>
      )}
    </div>
  );
}
