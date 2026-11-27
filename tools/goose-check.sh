#!/usr/bin/env bash
# The checks after a goose run (CLAUDE.md, agent orchestration): what the review must never have to spot by eye.
# Usage: tools/goose-check.sh <worktree> [base]   (base: the commit the run started from, default HEAD)
# It reads the worktree's uncommitted changes and new files, prints one line per broken rule and exits 1 if any.
set -uo pipefail

dir=${1:?usage: tools/goose-check.sh <worktree> [base]}
base=${2:-HEAD}
cd "$dir" || exit 2
failures=0
fail() { echo "FAIL: $*"; failures=$((failures + 1)); }

# goose leaves its work uncommitted: a commit since the base means it committed on its own.
[[ "$base" == "HEAD" ]] || [[ $(git rev-list --count "$base"..HEAD) == 0 ]] \
  || fail "commits since $base: goose must leave everything uncommitted"

changed=$( { git diff --name-only "$base"; git ls-files --others --exclude-standard; } | sort -u)
untracked=$(git ls-files --others --exclude-standard)

# The quality suite is ground truth.
grep -E 'ontology/OntologyDefinition\.java|experiments/ExperimentCase\.java|src/test/resources/experiments/|ExperimentRecordingTest\.java' \
  <<<"$changed" | while read -r f; do echo "FAIL: quality suite file changed: $f"; done | grep . && failures=$((failures + 1))

# The JaCoCo floors and the PMD ruleset stay as they are.
git diff "$base" -- pom.xml | grep -Eq '^[-+].*<minimum>' && fail "JaCoCo floors changed in pom.xml"
git diff --quiet "$base" -- config/pmd-ruleset.xml || fail "config/pmd-ruleset.xml changed"

# The baselines may only shrink: count their lines before and after.
lines_at_base() { git ls-tree -r --name-only "$base" -- "$1" | while read -r f; do git show "$base:$f"; done | wc -l; }
lines_now() { find "$1" -type f -exec cat {} + 2>/dev/null | wc -l; }
for b in src/test/resources/archunit-store config/cpd-baseline.txt config/pmd-baseline.properties; do
  before=$(lines_at_base "$b"); after=$(lines_now "$b")
  (( after > before )) && fail "$b grew from $before to $after lines (baselines may only shrink)"
done

# Patterns that may only become rarer in the main code: counted at the base and now, so a line that only
# changed around an old, frozen occurrence is not reported.
count_at_base() { git grep -E -c "$1" "$base" -- src/main/java | awk -F: '{n += $NF} END {print n + 0}'; }
count_now() { grep -rEc "$1" src/main/java | awk -F: '{n += $NF} END {print n + 0}'; }
rarer() { # pattern, message
  local before after
  before=$(count_at_base "$1"); after=$(count_now "$1")
  (( after > before )) && fail "$2 ($before before, $after now)"
}
rarer 'ZoneId\.of\(' "new ZoneId.of: a zone comes from the Clock or GardenTime"
rarer '(^|[^A-Za-z])TimeZone([^A-Za-z]|$)' "java.util.TimeZone: the ZoneId.of rule must not be worked around"
rarer '(^|[^A-Za-z])(LocalDate|LocalTime|LocalDateTime|ZonedDateTime|Instant)\.now\(\)' "now() without the Clock bean"
rarer 'getStyle\(\)\.set' "inline style: styles live only in styles/rootology.css"

# UI texts: a straight apostrophe in an added line.
props_added=$( { git diff "$base" -U0 -- 'src/main/resources/i18n/*.properties' | grep '^+[^+]';
                 for f in $(grep -E 'i18n/.*\.properties$' <<<"$untracked"); do sed 's/^/+/' "$f"; done; } )
grep -nE "^\+[^#=]+=.*'" <<<"$props_added" && fail "straight apostrophe in a UI text: use the typographic ’"

if (( failures > 0 )); then
  echo "$failures check(s) failed"
  exit 1
fi
echo "goose-check: all checks passed"
