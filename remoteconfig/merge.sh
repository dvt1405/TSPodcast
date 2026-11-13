#!/usr/bin/env bash
# Prints <current template> with the conditions and parameter groups of <params file> merged in.
#
#   remoteconfig/merge.sh <current-template.json> <params.json>
#
# - Conditions: same name -> replaced in place; new ones appended (order = evaluation priority).
# - Parameters: every key of the params file is first removed from the top level and from every
#   group, then added to its group (the group is created, or its description updated). Groups left
#   empty are dropped. Everything else in the current template is kept untouched.
set -euo pipefail
[[ $# -eq 2 ]] || { echo "usage: $0 <current-template.json> <params.json>" >&2; exit 2; }

jq --slurpfile p "$2" '
  ($p[0]) as $new
  | ([$new.parameterGroups[] | .parameters | keys[]]) as $ours
  | del(.version, .etag)
  | .conditions = (
      (.conditions // []) as $cur
      | ($new.conditions | map({(.name): .}) | add) as $byName
      | ($cur | map($byName[.name] // .))
        + ($new.conditions | map(select(.name as $n | ($cur | map(.name) | index($n)) == null)))
    )
  | .parameters = ((.parameters // {}) | with_entries(select(.key as $k | $ours | index($k) | not)))
  | .parameterGroups = (
      (.parameterGroups // {})
      | with_entries(.value.parameters |= with_entries(select(.key as $k | $ours | index($k) | not)))
      | reduce ($new.parameterGroups | to_entries[]) as $g (.;
          .[$g.key] = ((.[$g.key] // {}) + {description: $g.value.description}
                       | .parameters = ((.parameters // {}) + $g.value.parameters)))
      | with_entries(select((.value.parameters | length) > 0))
    )
' "$1"
