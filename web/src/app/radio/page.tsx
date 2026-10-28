import type { Metadata } from "next";
import { Artwork } from "@/components/Artwork";
import { JsonLd } from "@/components/JsonLd";
import { ErrorNotice } from "@/components/notices";
import { PlayButton } from "@/components/player/PlayButton";
import { t } from "@/lib/i18n";
import { getRadioChannels } from "@/lib/radio";
import { breadcrumbJsonLd } from "@/lib/seo";
import { OG_IMAGES } from "@/lib/site";
import { radioToTrack } from "@/lib/tracks";
import type { RadioChannel } from "@/lib/types";

export const revalidate = 21600;

export const metadata: Metadata = {
  title: t.radio.title,
  description: t.radio.description,
  alternates: { canonical: "/radio" },
  openGraph: { title: t.radio.title, description: t.radio.description, url: "/radio", images: OG_IMAGES },
};

function ChannelGrid({ channels }: { channels: RadioChannel[] }) {
  return (
    <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
      {channels.map((c) => (
        <li key={c.id} className="flex items-center gap-3 rounded-xl border border-border bg-surface p-3">
          <Artwork src={c.logo} alt="" size={56} className="rounded-lg bg-white object-contain" />
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-sm font-semibold">{c.name}</h3>
            {c.subtitle && <p className="truncate text-xs text-subtle">{c.subtitle}</p>}
          </div>
          <PlayButton track={radioToTrack(c)} />
        </li>
      ))}
    </ul>
  );
}

async function loadChannels() {
  try {
    return await getRadioChannels();
  } catch {
    return null;
  }
}

export default async function RadioPage() {
  const data = await loadChannels();
  const sources = data
    ? [
        { name: "VOV", ok: data.vov.ok, channels: data.vov.channels },
        { name: "VOH", ok: data.voh.ok, channels: data.voh.channels },
      ]
    : [];

  return (
    <div className="space-y-8">
      <JsonLd
        data={breadcrumbJsonLd([
          { name: t.nav.home, path: "/" },
          { name: t.radio.title, path: "/radio" },
        ])}
      />
      <header>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{t.radio.title}</h1>
        <p className="mt-1 text-sm text-subtle">{t.radio.description}</p>
      </header>
      {!data && <ErrorNotice message={t.radio.unavailable} />}
      {sources.map((s) => (
        <section key={s.name} aria-labelledby={`radio-${s.name}`}>
          <h2 id={`radio-${s.name}`} className="mb-3 text-lg font-bold">
            {s.name}
          </h2>
          {s.ok && s.channels.length ? (
            <ChannelGrid channels={s.channels} />
          ) : (
            <ErrorNotice message={t.radio.unavailable} />
          )}
        </section>
      ))}
      <p className="text-xs text-subtle">{t.radio.httpsNote}</p>
    </div>
  );
}
