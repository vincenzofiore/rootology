#!/usr/bin/env bash
# Rootology's rules that must not depend on Claude remembering them (PreToolUse hook, see CLAUDE.md):
# commits and pushes only with the owner's OK, signed off (DCO), never on main; a local run never emails a
# real person; the quality suite's ground truth is changed only when the owner asks.
# It reads the tool call as JSON on stdin and answers with a permission decision, or nothing to let it pass.
set -euo pipefail

input=$(cat)
tool=$(jq -r '.tool_name // ""' <<<"$input")

decide() { # deny|ask, reason
  jq -n --arg d "$1" --arg r "$2" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: $d, permissionDecisionReason: $r}}'
  exit 0
}

if [[ "$tool" == "Bash" ]]; then
  cmd=$(jq -r '.tool_input.command // ""' <<<"$input")
  cwd=$(jq -r '.cwd // ""' <<<"$input")
  git_opts='git( +-[Cc] +[^ ]+)*'

  # The denials first: a command may commit and push at once.
  commit=false; push=false
  grep -Eq "${git_opts} +commit\b" <<<"$cmd" && commit=true
  grep -Eq "${git_opts} +push\b" <<<"$cmd" && push=true
  if $commit; then
    grep -Eq "${git_opts} +commit\b.*( -[a-zA-Z]*s[a-zA-Z]*\b| --signoff\b)" <<<"$cmd" \
      || decide deny "Commits are signed off (DCO): use git commit -s."
    # shortcut: the branch of the session's folder, not of a `cd` inside the command.
    branch=$(git -C "${cwd:-.}" branch --show-current 2>/dev/null || true)
    [[ "$branch" == "main" ]] && decide deny "No commits on main: work happens on develop."
  fi
  if $push && grep -Eq "${git_opts} +push\b.*\bmain\b" <<<"$cmd"; then
    decide deny "main is updated only by the owner."
  fi
  if $commit || $push; then
    decide ask "Commit or push: only with the owner's OK (CLAUDE.md)."
  fi

  if grep -Eq "spring-boot:run" <<<"$cmd" \
     && ! grep -Eq "unset -m '?ROOTOLOGY_|SPRING_MAIL_PORT=1025" <<<"$cmd"; then
    decide deny "A local run must send email only to Mailpit: unset -m 'ROOTOLOGY_SMTP_*' first, or use ./run-local.sh."
  fi
  exit 0
fi

if [[ "$tool" =~ ^(Edit|Write|NotebookEdit)$ ]]; then
  path=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // ""' <<<"$input")
  case "$path" in
    */ontology/OntologyDefinition.java|*/experiments/ExperimentCase.java|*/src/test/resources/experiments/*|*/ExperimentRecordingTest.java)
      decide ask "Quality suite ground truth (CLAUDE.md): change it only when the owner asked." ;;
  esac
fi
exit 0
