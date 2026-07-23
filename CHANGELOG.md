# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-07-23

### Added

- First public release via JitPack (`com.github.acefun29.jswarm`).
- Multi-module library artifacts: core, spi, runtime, adapters, TCK, observability, spring-boot-starter.
- GitHub Actions CI (`./mvnw verify`) and `jitpack.yml` excluding examples modules.

### Notes

- Git tag / Maven `${revision}` / consumer `<version>` are all `1.0.0` (no `v` prefix).
- Java packages remain `com.jswarm.*`; only Maven `groupId` uses the JitPack coordinate.
