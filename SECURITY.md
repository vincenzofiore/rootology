# Security

Rootology is a prototype used by a few invited people. Its security work is tracked in
issue #41 (OWASP review) and #40 (personal data).

## Reporting a vulnerability

Please do not open a public issue. Report it privately through GitHub:
**Security > Report a vulnerability** on this repository. You will get an answer within a few days.

Please include what you found, how to reproduce it and what it could expose. Do not access
other people's data beyond what is needed to show the problem.

## Supported versions

Only the latest code on `develop` (and `main`, when the owner updates it) is maintained.

## Automated checks

Every push and pull request runs CodeQL (Java and workflows), the full Maven dependency
graph is sent to GitHub for Dependabot, OSV-Scanner checks dependencies, pull requests go
through dependency review, and secret scanning with push protection is on.
