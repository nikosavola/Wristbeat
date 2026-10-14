# Security Policy

## Reporting a Vulnerability

I take the security of this project seriously. If you believe you have found a security vulnerability, please report it
to me responsibly.

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please use the
[GitHub Private Vulnerability Reporting](https://docs.github.com/en/code-security/security-advisories/working-with-repository-security-advisories/configuring-private-vulnerability-reporting-for-a-repository)
feature through [Security Advisories](https://github.com/nikosavola/Wristbeat/security/advisories), or
contact @nikosavola directly.

### What to include in a report

To help me understand and fix the issue, please include as much information as possible:

- A description of the vulnerability and its potential impact.
- Steps to reproduce the issue (a minimal working example is highly appreciated).
- Any potential mitigations you've identified.

### Process

1. Acknowledge the report.
1. Investigate and confirm the issue.
1. Develop and test a fix.
1. Release a new version.
1. Credit the reporter in the release notes, if they wish.

Wristbeat is a Bluetooth peripheral with no network access, so the interesting surface is small: how a
session is started and stopped, what a receiver connected over the air can make the watch do, and what
the GATT server answers to a malformed request.