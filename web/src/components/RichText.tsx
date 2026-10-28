import type { ReactNode } from "react";

const URL_RE = /(https?:\/\/[^\s<>()"']+[^\s<>()"'.,;:!?])/g;

function linkify(text: string, keyPrefix: string): ReactNode[] {
  const parts = text.split(URL_RE);
  return parts.map((part, i) =>
    i % 2 === 1 ? (
      <a key={`${keyPrefix}-${i}`} href={part} rel="nofollow ugc noopener noreferrer" target="_blank">
        {part.length > 60 ? `${part.slice(0, 57)}…` : part}
      </a>
    ) : (
      part
    ),
  );
}

/** Feed text as paragraphs; URLs become nofollow links. Never renders HTML. */
export function RichText({ paragraphs, className = "" }: { paragraphs: string[]; className?: string }) {
  return (
    <div className={`prose-notes whitespace-pre-line break-words text-sm leading-relaxed text-muted ${className}`}>
      {paragraphs.map((p, i) => (
        <p key={i}>{linkify(p, String(i))}</p>
      ))}
    </div>
  );
}
