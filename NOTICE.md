# Notices

## Upstream
shangOS is a fork of **Luma TV** by 0xProbabillity (https://github.com/0xProbabillity/luma-tv),
v2.4 (versionCode 26), forked at commit `8ce6838`, used under the MIT licence. The original copyright
notice is kept in `LICENSE`, as the licence requires. The upstream remote is kept as `upstream` in git.

Changes made here are listed in `CHANGELOG.md`.

Upstream was last checked against Luma 2.5 (`1c1bc52`). Taken from it: the city picker for the weather (Weather location),
adapted to this code base's structure and network rules, and the keyboard window setting. Not taken: the automatic city lookup from the
TV's internet address (GeoJS, ipwho.is), which would send the TV's address to two more services, and the promo-demo restyle (a film tool).

## Not carried over
The upstream author's donation addresses, QR codes, contact links and "Support the author" screen were
removed. They belong to the upstream author and must not appear in this fork.

## Artwork
- The Milky Way and Calm Sea wallpapers are CC0 panoramas from Poly Haven (https://polyhaven.com).
- The Daybreak, Aurora, Ember, Tide, Orchid and Glacier wallpapers, the launcher icon and the TV banner were made for this
  project and are under the project's MIT licence. The wordmark in the banner is converted from Roboto Medium (Apache-2.0,
  Google; https://github.com/googlefonts/roboto).
- No artwork from the upstream project remains.

## Data and services
- Time zone to city data: IANA time zone database `zone.tab` (public domain).
- Weather (only if the user turns it on): Open-Meteo.com, data under CC BY 4.0. "Weather data by Open-Meteo.com".

The in-app list (Settings, About, Licences) shows the same credits with full licence texts.

## Name
"shangOS" is the product name. The Android package id is `dev.madusha.shangos`.
Source: https://github.com/madushas/shangos-launcher
