# Security Policy

## Supported versions

Only the latest release is supported. Please update to the most recent version
before reporting an issue.

## Reporting a vulnerability

Please do **not** open a public issue for security problems.

Report them privately through GitHub's private vulnerability reporting:
open the **Security** tab of this repository and click **Report a vulnerability**.

You can expect an acknowledgement within a few days. Fixes are published as a
new release; the report is disclosed publicly only after a fix is available.

## Scope

This is a Hammerhead Karoo extension. It asks for no permission and uses no
network. It listens to the battery broadcast Android sends to every app,
reads the ride state from the Karoo's extension SDK, and keeps seven days of
battery levels in a file of its own private storage. Its button opens
Android's own battery settings page. Reports about the extension's own code
(e.g. its exported components or the handling of that file) are in scope;
issues in the Karoo OS, the SDK or Android's settings should be reported to
Hammerhead or Google.
