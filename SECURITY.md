# Security Policy

## Reporting a vulnerability

Report it privately through GitHub: open the repository's **Security** tab and choose
**Report a vulnerability**, or go to
<https://github.com/verycareful/kern/security/advisories/new>. Do not open a public
issue, pull request or discussion about it.

Include the Kern version (Settings > About > Version), the Android version and
device, and the steps or the file that triggers the problem.

## What counts

Kern opens files from any source, so a crafted document is the main attack surface.
Examples of what to report:

- a file that makes Kern run code, read or write outside the document it opened, or
  reach the network;
- a Markdown or EPUB document that runs script or loads anything from outside Kern;
- a way for another app to make Kern read or overwrite a file the user did not open.

A crash on a malformed file with no further effect is a bug: report it with the bug
report form.

## Supported versions

Kern is in alpha. Fixes go into the latest release only.
