import type { ReactNode } from "react";

// Renders the small markdown subset the AI prompts ask for ("## " headings,
// "- " bullets, **bold**, _italic_) as React elements. Model output is never
// injected as HTML.

function inline(text: string, key: string): ReactNode[] {
  const out: ReactNode[] = [];
  const re = /(\*\*[^*]+\*\*|_[^_]+_)/g;
  let last = 0;
  let i = 0;
  for (const m of text.matchAll(re)) {
    if (m.index > last) out.push(text.slice(last, m.index));
    const token = m[0];
    out.push(
      token.startsWith("**") ? (
        <strong key={`${key}-${i++}`}>{token.slice(2, -2)}</strong>
      ) : (
        <em key={`${key}-${i++}`}>{token.slice(1, -1)}</em>
      ),
    );
    last = m.index + token.length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

export function Markdown({ text, className = "" }: { text: string; className?: string }) {
  const blocks: ReactNode[] = [];
  let list: string[] = [];
  let para: string[] = [];

  const flushList = () => {
    if (list.length) {
      const k = `ul-${blocks.length}`;
      blocks.push(
        <ul key={k} className="my-2 list-disc space-y-1 pl-5">
          {list.map((item, i) => (
            <li key={i}>{inline(item, `${k}-${i}`)}</li>
          ))}
        </ul>,
      );
      list = [];
    }
  };
  const flushPara = () => {
    if (para.length) {
      const k = `p-${blocks.length}`;
      blocks.push(
        <p key={k} className="my-2">
          {inline(para.join(" "), k)}
        </p>,
      );
      para = [];
    }
  };

  for (const raw of text.split("\n")) {
    const line = raw.trim();
    const heading = /^#{1,4}\s+(.*)$/.exec(line);
    const bullet = /^(?:[-*•]|\d+[.)])\s+(.*)$/.exec(line);
    if (heading) {
      flushList();
      flushPara();
      const k = `h-${blocks.length}`;
      blocks.push(
        <h3 key={k} className="mt-4 mb-1 text-sm font-bold uppercase tracking-wide text-primary first:mt-0">
          {inline(heading[1], k)}
        </h3>,
      );
    } else if (bullet) {
      flushPara();
      list.push(bullet[1]);
    } else if (line === "") {
      flushList();
      flushPara();
    } else {
      flushList();
      para.push(line);
    }
  }
  flushList();
  flushPara();
  return <div className={`text-sm leading-relaxed ${className}`}>{blocks}</div>;
}
