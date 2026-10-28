/* eslint-disable @next/next/no-img-element -- Artwork comes from thousands of
   arbitrary feed hosts; proxying it through next/image would make this server
   an open image optimizer and multiply bandwidth. Plain lazy <img> instead. */

interface ArtworkProps {
  src: string | null | undefined;
  alt: string;
  size?: number;
  className?: string;
  priority?: boolean;
  /** Fill the parent's width (square) instead of a fixed size. */
  fluid?: boolean;
}

export function Artwork({ src, alt, size = 160, className = "", priority = false, fluid = false }: ArtworkProps) {
  const style = fluid ? undefined : { width: size, height: size };
  const base = `${fluid ? "aspect-square w-full" : "shrink-0"} bg-surface-2 object-cover ${className}`;
  if (!src) {
    return (
      <div
        role={alt ? "img" : undefined}
        aria-label={alt || undefined}
        aria-hidden={alt ? undefined : true}
        style={style}
        className={`${base} flex items-center justify-center bg-gradient-to-br from-primary to-secondary text-on-primary`}
      >
        <svg viewBox="0 0 24 24" width="40%" height="40%" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
          <rect x="9" y="3" width="6" height="11" rx="3" />
          <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
        </svg>
      </div>
    );
  }
  return (
    <img
      src={src}
      alt={alt}
      width={fluid ? 600 : size}
      height={fluid ? 600 : size}
      loading={priority ? "eager" : "lazy"}
      decoding="async"
      referrerPolicy="no-referrer"
      fetchPriority={priority ? "high" : undefined}
      style={style}
      className={base}
    />
  );
}
