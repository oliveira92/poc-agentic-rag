/**
 * Parser mínimo do formato de exposição Prometheus (o que /actuator/prometheus devolve).
 * Suficiente para os meters `rag_*` do RagMetrics — counters (`_total`), summaries
 * (`_count`/`_sum`/`_max`) e os quantis de Timer (label `quantile`).
 */

export interface Sample {
  name: string;
  labels: Record<string, string>;
  value: number;
}

const LINE = /^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})?\s+([-+0-9.eENaN]+)\s*$/;
const LABEL = /([a-zA-Z_][a-zA-Z0-9_]*)="((?:[^"\\]|\\.)*)"/g;

export function parsePrometheus(text: string): Sample[] {
  const samples: Sample[] = [];
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const m = LINE.exec(line);
    if (!m) continue;
    const value = Number(m[3]);
    if (Number.isNaN(value) && m[3] !== 'NaN') continue;
    const labels: Record<string, string> = {};
    if (m[2]) {
      for (const lm of m[2].matchAll(LABEL)) {
        labels[lm[1]] = lm[2].replace(/\\"/g, '"').replace(/\\\\/g, '\\');
      }
    }
    samples.push({ name: m[1], labels, value });
  }
  return samples;
}

/** Soma os valores das amostras de `name` que casam com os labels informados. */
export function sumOf(
  samples: Sample[],
  name: string,
  match: Record<string, string> = {},
): number {
  return samples
    .filter((s) => s.name === name && Object.entries(match).every(([k, v]) => s.labels[k] === v))
    .reduce((acc, s) => acc + s.value, 0);
}

/** Valores distintos de um label entre as amostras de um meter. */
export function labelValues(samples: Sample[], name: string, label: string): string[] {
  const set = new Set<string>();
  for (const s of samples) {
    if (s.name === name && s.labels[label]) set.add(s.labels[label]);
  }
  return [...set].sort();
}
