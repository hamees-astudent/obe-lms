import RichText from '@/components/ai/RichText';
import type { CitationResponse } from '@/types/api';

/**
 * An assistant answer: Markdown with headings, lists, tables, code blocks and
 * LaTeX mathematics, its [S1] markers turned into numbered buttons that show
 * the cited passage. Rendered as React nodes — never as raw HTML — so text
 * from the model or the material cannot inject markup.
 */
export default function AnswerText({ text, citations }: { text: string; citations: CitationResponse[] }) {
  return <RichText text={text} citations={citations} className="text-gray-800" />;
}
