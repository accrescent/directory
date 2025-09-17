# Contributing guidelines

Thank you for your interest in contributing to Accrescent's directory server! Here are a few
resources to get you started.

## Development setup

The directory server is built using the [Quarkus framework]. You'll need the following installed to
build and develop the directory server and its related libraries:

- A JDK 25 environment
- Docker

The most convenient way to develop the directory server is to either run `quarkus dev` using the
[Quarkus CLI] or directly run `./gradlew quarkusDev`. If the Docker daemon is running, these
commands will start dependent containers via [Quarkus Dev Services] and a development version of the
directory server with live reload enabled.

To learn more, we strongly recommend reading [Quarkus' documentation].

## Code style

Kotlin code style in not fully enforced in CI, but has the following guidelines:

- Wrap lines at 100 columns. This isn't a hard limit, but will be enforced unless wrapping a line
  looks uglier than extending it by a few columns.
- Don't use glob imports. You can have Intellij IDEA create single name imports automatically by
  going to `File -> Settings -> Editor -> Code Style -> Kotlin` and enabling "Use single name
  import".
- Format via Intellij IDEA's formatter. You can do this by navigating to `Code -> Reformat Code` and
  checking "Rearrange entries" and "Cleanup code" before clicking "Run".

## Code conventions

We make a strong effort to avoid unnecessary third-party libraries. When a third-party library is
needed, it should be well-maintained, widely used, and ideally written in a memory-safe way (i.e.
including little to no native code, only using the safe subsets of Kotlin/Java, etc.).

Official libraries from the Quarkus framework are almost always acceptable.

## Licensing

Contributing to the directory server requires signing a Contributor License Agreement (CLA). To sign
[Accrescent's CLA], just make a pull request, and our CLA bot will direct you. If you've already
signed the CLA for another Accrescent project, you won't need to do so again.

We require all code to have valid copyright and licensing information. If your contribution creates
a new file, be sure to add the following header in a code comment:

```
Copyright <current-year> Logan Magee

SPDX-License-Identifier: AGPL-3.0-only
```

## Vulnerability reports

This GitHub repository has [private vulnerability reporting] enabled. If you have a security issue
to report, either [submit a report] privately on GitHub or email us at <security@accrescent.app>.
Also be sure to read this repository's [security policy] before creating a report.

[Accrescent's CLA]: https://gist.github.com/lberrymage/1be5c6a041131b9fd0b54b442023ad21
[private vulnerability reporting]: https://github.blog/security/supply-chain-security/private-vulnerability-reporting-now-generally-available/
[Quarkus CLI]: https://quarkus.io/guides/cli-tooling
[Quarkus Dev Services]: https://quarkus.io/guides/dev-services
[Quarkus framework]: https://quarkus.io/
[Quarkus' documentation]: https://quarkus.io/guides/
[security policy]: SECURITY.md
[submit a report]: https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability#privately-reporting-a-security-vulnerability
