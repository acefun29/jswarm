# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-07-23

### Fixed

- JitPack build: make `mvnw` executable (`chmod +x` in `jitpack.yml` + git file mode).

## [1.0.0] - 2026-07-23

### Added

- First public release attempt via JitPack (`com.github.acefun29.jswarm`).
- Multi-module library artifacts: core, spi, runtime, adapters, TCK, observability, spring-boot-starter.
- GitHub Actions CI (`./mvnw verify`) and `jitpack.yml` excluding examples modules.

### Notes

- Tag `1.0.0` JitPack build failed (`Permission denied` on `./mvnw`); use **1.0.1**.
- Java packages remain `com.jswarm.*`; only Maven `groupId` uses the JitPack coordinate.
