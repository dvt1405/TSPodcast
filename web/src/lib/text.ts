import "server-only";
import * as cheerio from "cheerio";

/**
 * Converts feed HTML (descriptions, show notes) to plain paragraphs. Feeds are
 * untrusted, so nothing is rendered as HTML: text is extracted and links are
 * re-created by the renderer from bare URLs.
 */
export function htmlToParagraphs(html: string | null | undefined): string[] {
  if (!html) return [];
  if (!/[<&]/.test(html)) {
    return splitParagraphs(html);
  }
  const $ = cheerio.load(`<div id="root">${html}</div>`);
  $("script, style, iframe, noscript").remove();
  $("br").replaceWith("\n");
  $("p, div, li, h1, h2, h3, h4, h5, h6, blockquote, tr").each((_, el) => {
    const node = $(el);
    if (el.tagName === "li") node.prepend("• ");
    node.append("\n\n");
  });
  // Keep link targets visible when the anchor text hides them.
  $("a[href]").each((_, el) => {
    const a = $(el);
    const href = a.attr("href") ?? "";
    const text = a.text().trim();
    if (/^https?:\/\//i.test(href) && !text.includes(href) && !/^https?:\/\//i.test(text)) {
      a.append(` (${href})`);
    }
  });
  return splitParagraphs($("#root").text());
}

function splitParagraphs(text: string): string[] {
  return text
    .replace(/\r/g, "")
    .replace(/[ \t ]+/g, " ")
    .split(/\n\s*\n+/)
    .map((p) => p.replace(/ *\n */g, "\n").trim())
    .filter(Boolean);
}

export function htmlToText(html: string | null | undefined): string {
  return htmlToParagraphs(html).join("\n\n");
}
