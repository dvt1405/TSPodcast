import { SearchIcon, SparkIcon } from "@/components/icons";
import { t } from "@/lib/i18n";

/** Plain GET form: works without JavaScript. The AI button submits mode=ai. */
export function SearchForm({
  defaultValue = "",
  aiEnabled,
  mode = "podcast",
  autoFocus = false,
  id = "q",
}: {
  defaultValue?: string;
  aiEnabled: boolean;
  mode?: string;
  autoFocus?: boolean;
  id?: string;
}) {
  return (
    <form action="/search" role="search" className="w-full">
      <label htmlFor={id} className="sr-only">
        {t.search.label}
      </label>
      <div className="flex flex-col gap-2 sm:flex-row">
        <div className="flex flex-1 items-center gap-2 rounded-full border border-border bg-surface px-4 shadow-sm focus-within:border-primary">
          <SearchIcon size={18} className="text-subtle" />
          <input
            id={id}
            name="q"
            type="search"
            defaultValue={defaultValue}
            autoFocus={autoFocus}
            placeholder={mode === "ai" ? t.search.aiPlaceholder : t.search.placeholder}
            className="h-12 w-full bg-transparent text-base outline-none placeholder:text-subtle"
            minLength={1}
            maxLength={300}
          />
        </div>
        <div className="flex gap-2">
          {mode === "music" && <input type="hidden" name="mode" value="music" />}
          <button
            type="submit"
            {...(mode === "ai" ? { name: "mode", value: "podcast" } : {})}
            className="h-12 flex-1 rounded-full bg-primary px-6 text-sm font-bold text-on-primary shadow-sm hover:opacity-90 sm:flex-none"
          >
            {t.search.submit}
          </button>
          {aiEnabled && (
            <button
              type="submit"
              name="mode"
              value="ai"
              className="inline-flex h-12 flex-1 items-center justify-center gap-1.5 rounded-full border border-primary bg-primary-soft px-5 text-sm font-bold text-primary hover:opacity-90 sm:flex-none"
            >
              <SparkIcon size={16} />
              {t.search.modeAi}
            </button>
          )}
        </div>
      </div>
    </form>
  );
}
