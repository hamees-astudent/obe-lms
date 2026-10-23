import { Fragment } from 'react';
import type { CitationResponse } from '@/types/api';

/**
 * An assistant answer as light formatting: paragraphs, "- " bullets, **bold**,
 * and [S1] markers turned into numbered references to the citations below.
 * Rendered as React nodes — never as HTML — so text from the model or the
 * material cannot inject markup.
 */
export default function AnswerText({ text, citations }: { text: string; citations: CitationResponse[] }) {
  const lines = text.split('\n');
  const blocks: Array<{ kind: 'p' | 'ul'; lines: string[] }> = [];
  for (const raw of lines) {
    const line = raw.trimEnd();
    if (!line.trim()) {
      blocks.push({ kind: 'p', lines: [] });
      continue;
    }
    const bullet = /^\s*[-*•]\s+/.test(line);
    const last = blocks[blocks.length - 1];
    const kind = bullet ? 'ul' : 'p';
    const content = bullet ? line.replace(/^\s*[-*•]\s+/, '') : line;
    if (last && last.kind === kind && (kind === 'ul' || last.lines.length > 0)) last.lines.push(content);
    else blocks.push({ kind, lines: [content] });
  }
  return (
    <div className="space-y-2 text-sm leading-relaxed text-gray-800">
      {blocks
        .filter((b) => b.lines.length > 0)
        .map((b, i) =>
          b.kind === 'ul' ? (
            <ul key={i} className="list-disc space-y-1 pl-5">
              {b.lines.map((l, j) => (
                <li key={j}>
                  <Inline text={l} citations={citations} />
                </li>
              ))}
            </ul>
          ) : (
            <p key={i}>
              <Inline text={b.lines.join(' ')} citations={citations} />
            </p>
          ),
        )}
    </div>
  );
}

function Inline({ text, citations }: { text: string; citations: CitationResponse[] }) {
  const parts = text.split(/(\[S\d+(?:,\s*S\d+)*]|\*\*[^*]+\*\*)/g);
  return (
    <>
      {parts.map((part, i) => {
        if (/^\[S\d+/.test(part)) {
          const refs = part.match(/S\d+/g) ?? [];
          return (
            <Fragment key={i}>
              {refs.map((ref) => {
                const n = citations.findIndex((c) => c.ref === ref);
                const c = citations[n];
                return (
                  <sup
                    key={ref}
                    title={c ? `${c.materialTitle} · ${c.locationLabel}` : undefined}
                    className="ml-0.5 rounded bg-primary-100 px-1 text-[10px] font-semibold text-primary-700"
                  >
                    {n >= 0 ? n + 1 : ref.slice(1)}
                  </sup>
                );
              })}
            </Fragment>
          );
        }
        if (part.startsWith('**') && part.endsWith('**')) {
          return <strong key={i}>{part.slice(2, -2)}</strong>;
        }
        return <Fragment key={i}>{part}</Fragment>;
      })}
    </>
  );
}
