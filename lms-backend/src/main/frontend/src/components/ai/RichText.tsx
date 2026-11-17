import { useEffect, useRef, useState, type ReactNode } from 'react';
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import 'katex/dist/katex.min.css';
import { Check, Copy } from 'lucide-react';
import type { CitationResponse } from '@/types/api';
import CitationPassage from '@/components/ai/CitationPassage';

/**
 * Text from the study assistant, the question generator or course material,
 * rendered as Markdown: paragraphs, lists, tables, `code`, fenced code blocks
 * and LaTeX mathematics (`$…$`, `$$…$$`, `\(…\)`, `\[…\]`) via KaTeX.
 *
 * Raw HTML in the text is never rendered (react-markdown escapes it), so text
 * from the model or an uploaded file cannot inject markup. With `citations`,
 * source markers such as `[S1]` or `[S1, S3]` become numbered buttons that
 * open the cited passage.
 */
export default function RichText({
  text,
  citations,
  compact = false,
  className = '',
}: {
  text: string;
  citations?: CitationResponse[];
  /** Tighter spacing, for question stems and options. */
  compact?: boolean;
  className?: string;
}) {
  const source = prepare(text, citations ?? []);
  const components = markdownComponents(citations ?? [], compact);
  return (
    <div
      className={`rich-text min-w-0 break-words ${compact ? 'space-y-1' : 'space-y-2 text-sm leading-relaxed'} ${className}`}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm, [remarkMath, { singleDollarTextMath: true }]]}
        rehypePlugins={[[rehypeKatex, { throwOnError: false, strict: 'ignore', output: 'htmlAndMathml' }]]}
        components={components}
      >
        {source}
      </ReactMarkdown>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Pre-processing
// ---------------------------------------------------------------------------

const FENCE = /(```[\s\S]*?(?:```|$)|~~~[\s\S]*?(?:~~~|$))/g;
const INLINE_CODE = /(`[^`\n]+`)/g;
const MARKER = /\[(S\d+(?:\s*[,;]\s*S?\d+)*)\]/g;

/**
 * Normalises what models write into what the Markdown parser understands,
 * leaving code untouched: `\(…\)` and `\[…\]` become `$…$` and `$$…$$`, and
 * `[S1, S2]` becomes links the renderer turns into citation buttons.
 */
export function prepare(text: string, citations: CitationResponse[]): string {
  return text
    .split(FENCE)
    .map((part, i) => {
      if (i % 2 === 1) return part; // fenced code
      return part
        .split(INLINE_CODE)
        .map((piece, j) => {
          if (j % 2 === 1) return piece; // inline code
          let out = piece
            .replace(/\\\[([\s\S]+?)\\\]/g, (_, m: string) => `\n$$\n${m.trim()}\n$$\n`)
            .replace(/\\\(([\s\S]+?)\\\)/g, (_, m: string) => `$${m.trim()}$`);
          out = out.replace(MARKER, (_, group: string) => {
            const refs = group.split(/\s*[,;]\s*/).map((r) => (r.startsWith('S') ? r : `S${r}`));
            return refs
              .map((ref) => {
                const n = citations.findIndex((c) => c.ref === ref);
                return `[${n >= 0 ? n + 1 : ref.slice(1)}](#cite-${ref})`;
              })
              .join('');
          });
          return out;
        })
        .join('');
    })
    .join('');
}

// ---------------------------------------------------------------------------
// Element mapping
// ---------------------------------------------------------------------------

function markdownComponents(citations: CitationResponse[], compact: boolean): Components {
  return {
    p: ({ children }) => <p className={compact ? '' : 'my-0'}>{children}</p>,
    h1: ({ children }) => <h3 className="mt-3 text-base font-semibold text-gray-900">{children}</h3>,
    h2: ({ children }) => <h3 className="mt-3 text-base font-semibold text-gray-900">{children}</h3>,
    h3: ({ children }) => <h4 className="mt-3 text-sm font-semibold text-gray-900">{children}</h4>,
    h4: ({ children }) => <h5 className="mt-2 text-sm font-semibold text-gray-800">{children}</h5>,
    ul: ({ children }) => <ul className="list-disc space-y-1 pl-5">{children}</ul>,
    ol: ({ children }) => <ol className="list-decimal space-y-1 pl-5">{children}</ol>,
    blockquote: ({ children }) => (
      <blockquote className="border-l-2 border-gray-300 pl-3 text-gray-600">{children}</blockquote>
    ),
    table: ({ children }) => (
      <div className="max-w-full overflow-x-auto">
        <table className="min-w-full border-collapse text-left text-xs">{children}</table>
      </div>
    ),
    th: ({ children }) => (
      <th className="border border-gray-200 bg-gray-50 px-2 py-1 font-semibold">{children}</th>
    ),
    td: ({ children }) => <td className="border border-gray-200 px-2 py-1 align-top">{children}</td>,
    pre: ({ children }) => <CodeBlock>{children}</CodeBlock>,
    code: ({ className, children }) => {
      // Inside <pre> (a fenced block) the CodeBlock styles it; inline code is styled here.
      if (className?.startsWith('language-') || String(children).includes('\n')) {
        return <code className={`${className ?? ''} font-mono`}>{children}</code>;
      }
      return (
        <code className="rounded bg-gray-100 px-1 py-0.5 font-mono text-[0.85em] text-gray-800">{children}</code>
      );
    },
    a: ({ href, children }) => {
      if (href?.startsWith('#cite-')) {
        const ref = href.slice('#cite-'.length);
        const n = citations.findIndex((c) => c.ref === ref);
        return <CitationMarker label={String(children)} citation={citations[n]} />;
      }
      return (
        <a href={href} target="_blank" rel="noopener noreferrer" className="text-primary-700 underline">
          {children}
        </a>
      );
    },
    img: ({ alt }) => <span className="text-gray-400">[{alt || 'image'}]</span>,
  };
}

function CodeBlock({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLPreElement>(null);
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(ref.current?.innerText ?? '');
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard unavailable (insecure context); the text can still be selected
    }
  }
  return (
    <div className="group relative">
      <pre
        ref={ref}
        className="max-w-full overflow-x-auto rounded-lg bg-gray-900 p-3 font-mono text-xs leading-relaxed text-gray-100"
      >
        {children}
      </pre>
      <button
        type="button"
        onClick={copy}
        aria-label="Copy code"
        className="absolute right-2 top-2 rounded bg-gray-700/80 p-1 text-gray-200 opacity-0 transition hover:bg-gray-600 focus:opacity-100 group-hover:opacity-100"
      >
        {copied ? <Check size={12} /> : <Copy size={12} />}
      </button>
    </div>
  );
}

/** A numbered source marker; click to read the cited passage beside the answer. */
function CitationMarker({ label, citation }: { label: string; citation?: CitationResponse }) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!open) return;
    function onDown(e: MouseEvent) {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!citation) {
    return <sup className="ml-0.5 text-[10px] text-gray-400">[{label}]</sup>;
  }
  return (
    <span ref={box} className="relative inline-block align-baseline">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        title={`${citation.materialTitle} · ${citation.locationLabel} — click to read the passage`}
        className="ml-0.5 rounded bg-primary-100 px-1 align-super text-[10px] font-semibold leading-none text-primary-700 hover:bg-primary-200"
      >
        {label}
      </button>
      {open && (
        <span className="absolute left-1/2 top-full z-30 mt-1 block w-[min(22rem,80vw)] -translate-x-1/2 rounded-lg border border-gray-200 bg-white p-3 text-left text-xs font-normal leading-relaxed text-gray-700 shadow-lg">
          <CitationPassage citation={citation} index={Number(label)} />
        </span>
      )}
    </span>
  );
}
