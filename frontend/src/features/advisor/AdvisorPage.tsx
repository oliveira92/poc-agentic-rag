import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { advise, listModels, sendFeedback } from '../../api/client';
import type { AdviceResult } from '../../api/types';
import { useComponentId } from '../../App';
import {
  Badge,
  CopyButton,
  RouteBadge,
  ScoreBar,
  SourceBadge,
  Spinner,
} from '../../components/ui';
import { useToast } from '../../lib/toast';

interface ChatEntry {
  id: number;
  question: string;
  result?: AdviceResult;
  latencyMs?: number;
  error?: string;
  feedback?: 0 | 1;
}

let entryId = 1;

/**
 * Chat do Advisor: pergunta → resposta ancorada com citações. Torna visíveis as 5 camadas
 * do M03: rota/risco e modelo (governança), latência (técnica), guardrail lowConfidence
 * (qualidade), 👍/👎 ligado ao traceId (negócio) e o traceId em si (observabilidade).
 */
export function AdvisorPage() {
  const componentId = useComponentId();
  const { toast } = useToast();

  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [question, setQuestion] = useState('');
  const [model, setModel] = useState(''); // '' = automático (roteamento por risco)
  const [conversationId, setConversationId] = useState<string | undefined>();
  const scrollRef = useRef<HTMLDivElement>(null);

  const models = useQuery({ queryKey: ['models'], queryFn: listModels });

  const ask = useMutation({
    mutationFn: async (q: string) => {
      const t0 = performance.now();
      const result = await advise(componentId, {
        question: q,
        conversationId,
        model: model || undefined,
      });
      return { result, latencyMs: performance.now() - t0 };
    },
    onSuccess: ({ result, latencyMs }, q) => {
      setConversationId(result.conversationId);
      setEntries((es) =>
        es.map((e) =>
          e.question === q && !e.result && !e.error ? { ...e, result, latencyMs } : e,
        ),
      );
    },
    onError: (err, q) => {
      setEntries((es) =>
        es.map((e) =>
          e.question === q && !e.result && !e.error ? { ...e, error: String(err.message) } : e,
        ),
      );
    },
  });

  const feedback = useMutation({
    mutationFn: ({ entry, value }: { entry: ChatEntry; value: 0 | 1 }) =>
      sendFeedback(componentId, {
        value,
        route: entry.result?.route,
        traceId: entry.result?.traceId ?? undefined,
      }),
    onSuccess: (_data, { entry, value }) => {
      setEntries((es) => es.map((e) => (e.id === entry.id ? { ...e, feedback: value } : e)));
      toast(value === 1 ? 'Feedback 👍 registrado no trace' : 'Feedback 👎 registrado no trace', 'success');
    },
    onError: (err) => toast(`Falha ao registrar feedback: ${err.message}`, 'error'),
  });

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [entries]);

  const send = () => {
    const q = question.trim();
    if (!q || ask.isPending) return;
    setEntries((es) => [...es, { id: entryId++, question: q }]);
    setQuestion('');
    ask.mutate(q);
  };

  const reset = () => {
    setEntries([]);
    setConversationId(undefined);
  };

  return (
    <div className="content content--chat">
      <div className="chat">
        <div className="chat__scroll" ref={scrollRef}>
          <div className="chat__inner">
            {entries.length === 0 && (
              <div className="empty" style={{ paddingTop: 80 }}>
                <div className="empty__icon">💬</div>
                <div className="empty__title">Pergunte como consumir o {componentId}</div>
                <div className="empty__hint">
                  Ex.: "Como faço uma cobrança e trato idempotência?" — a resposta vem
                  ancorada nas fontes, com citações [n].
                </div>
              </div>
            )}

            {entries.map((e) => (
              <div key={e.id} style={{ display: 'contents' }}>
                <div className="msg-user">{e.question}</div>
                {e.result ? (
                  <AssistantMessage
                    entry={e}
                    onFeedback={(value) => feedback.mutate({ entry: e, value })}
                    feedbackPending={feedback.isPending}
                  />
                ) : e.error ? (
                  <div className="msg-ai">
                    <div className="alert alert--danger" style={{ margin: 0 }}>
                      <span>⚠</span>
                      <span>{e.error}</span>
                    </div>
                  </div>
                ) : (
                  <div className="msg-ai">
                    <span className="thinking">
                      <Spinner small /> consultando a base e gerando resposta ancorada…
                    </span>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        <div className="composer">
          <div className="composer__inner">
            <div className="composer__opts">
              <label className="faint" htmlFor="model-select">modelo</label>
              <select
                id="model-select"
                className="select input--sm"
                value={model}
                onChange={(e) => setModel(e.target.value)}
              >
                <option value="">Automático — roteamento por risco</option>
                {models.data?.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.label} {m.tier ? `(${m.tier})` : ''}
                  </option>
                ))}
              </select>
              {conversationId && (
                <>
                  <Badge tone="neutral" title={conversationId}>
                    conversa: {conversationId.slice(0, 8)}…
                  </Badge>
                  <button type="button" className="btn btn--ghost btn--xs" onClick={reset}>
                    ✦ nova conversa
                  </button>
                </>
              )}
            </div>
            <div className="composer__row">
              <textarea
                className="textarea"
                rows={2}
                placeholder={`Pergunte sobre o ${componentId}…  (Enter envia · Shift+Enter quebra linha)`}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    send();
                  }
                }}
              />
              <button
                type="button"
                className="btn btn--primary"
                onClick={send}
                disabled={ask.isPending || !question.trim()}
              >
                {ask.isPending ? <Spinner small /> : '➤'} Perguntar
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function AssistantMessage({
  entry,
  onFeedback,
  feedbackPending,
}: {
  entry: ChatEntry;
  onFeedback: (value: 0 | 1) => void;
  feedbackPending: boolean;
}) {
  const r = entry.result!;
  return (
    <div className="msg-ai">
      <div className="msg-ai__meta">
        <RouteBadge route={r.route} />
        <Badge tone="violet" title="modelo que respondeu">
          {r.model}
        </Badge>
        {r.grounded ? (
          <Badge tone="green" title="há base recuperada fundamentando a resposta">
            ancorada
          </Badge>
        ) : (
          <Badge tone="rose">sem base</Badge>
        )}
        {entry.latencyMs != null && (
          <Badge tone="neutral">{(entry.latencyMs / 1000).toFixed(1)}s</Badge>
        )}
        {r.traceId && <CopyButton text={r.traceId} label={`trace ${r.traceId.slice(0, 8)}…`} />}
      </div>

      {!r.grounded && (
        <div className="alert alert--danger">
          <span>∅</span>
          <span>
            Nada foi recuperado da base para este componente — a resposta avisa o que falta
            ingerir (evento <code>rag.ungrounded</code> no trace).
          </span>
        </div>
      )}

      {r.lowConfidence && (
        <div className="alert alert--warn">
          <span>⚠</span>
          <span>
            <strong>Guardrail anti-alucinação:</strong> a resposta cita endpoints fora das
            fontes: <code>{r.unsupportedEndpoints.join(', ')}</code>
          </span>
        </div>
      )}

      <div className="md">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{r.answer}</ReactMarkdown>
      </div>

      {r.citations.length > 0 && (
        <details className="citations">
          <summary>
            {r.citations.length} fonte{r.citations.length > 1 ? 's' : ''} citada
            {r.citations.length > 1 ? 's' : ''}
          </summary>
          {r.citations.map((c) => (
            <div key={c.index} className="citation">
              <span className="citation__idx">[{c.index}]</span>
              <div className="citation__body">
                <div className="citation__head">
                  <SourceBadge source={c.source} />
                  <span className="citation__ref">{c.ref}</span>
                  <ScoreBar score={c.score} />
                </div>
                <div className="citation__snippet">{truncate(c.snippet, 260)}</div>
              </div>
            </div>
          ))}
        </details>
      )}

      <div className="msg-ai__foot">
        <span className="faint">Esta resposta ajudou?</span>
        <button
          type="button"
          className={`btn btn--ghost btn--xs thumb ${entry.feedback === 1 ? 'selected--up' : ''}`}
          disabled={feedbackPending || entry.feedback !== undefined}
          onClick={() => onFeedback(1)}
          title="CSAT positivo — vira score no trace"
        >
          👍
        </button>
        <button
          type="button"
          className={`btn btn--ghost btn--xs thumb ${entry.feedback === 0 ? 'selected--down' : ''}`}
          disabled={feedbackPending || entry.feedback !== undefined}
          onClick={() => onFeedback(0)}
          title="CSAT negativo — vira score no trace"
        >
          👎
        </button>
        <span className="topbar__spacer" />
        <span className="faint mono" title="rascunho para curadoria humana">
          rascunho HITL: {r.approvalId.slice(0, 8)}…
        </span>
      </div>
    </div>
  );
}

function truncate(text: string, max: number): string {
  return text.length <= max ? text : `${text.slice(0, max)} …`;
}
