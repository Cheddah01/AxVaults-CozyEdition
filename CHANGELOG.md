# Cozy Edition changelog

## 2.16.3-cozy.2 — 2026-10-09

- Merge upstream 2.16.1 (`7a9167d`), including removal of blacklisted items already stored in vaults.
- Update AxAPI from 2.2.0-DEV-NEW-VERSION-5 to 2.2.0-DEV-NEW-VERSION-7.
- Preserve the Cozy upgrade prompt, save/shutdown cleanup, disabled stock notifier, Java 25 and Paper 26.2 target.
- Keep existing configuration and vault data without schema changes or migration.
- Verify the build and 17 regression tests on Paper 26.2; run the same compiled tests against Paper 26.3 build 151 beta. Live blacklist interactions, item serialization, native adapter loading, PlugManX and MySQL still need server checks.

## 2.16.3-cozy.1 — 2026-09-25

- Build against Paper 26.2 build 117 and Java 25, with API 26.2, for a single JAR on Paper 26.2 and 26.3.
- Preserve the custom vault upgrade prompt, lifecycle fixes, database schema, item serializer and configuration. No data migration.
- Give fork builds a Cozy version suffix and disable stock upstream update notifications, including previously enabled saved settings.
- Update the test mocking library for Java 25; verify the same compiled tests on Paper 26.3 build 40 alpha.
- Confirm upstream master still matches the fork's 2.16.0 base; no upstream merge required.
- Verify availability of official AxAPI native adapters for both versions. Live item serialization and MySQL checks remain required.

## 2.16.2

- Drain pending vault saves and release menus, workers, databases, commands, hooks and drivers during shutdown.

## 2.16.1

- Add the configurable Unlock More Vaults selector prompt while preserving player data and permissions.
