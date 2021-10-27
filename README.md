<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.frostnerd.smokescreen" rel="nofollow noopener noreferrer" target="_blank">
  <img style="width:200px; height:200px" src="app/src/main/res/mipmap-xxhdpi/ic_launcher.png" alt="Project logo"></a>
</div>

<div align="center">
    <h3 align="center">Nebulo</h3>
</div>
<div align="center">

  [![Apk size](https://img.shields.io/badge/Apk%20file%20size-4.7%20MB-blue)](#installation) 
  [![License](https://img.shields.io/badge/license-GPLv3-blue.svg)](/LICENSE)
  <a href="https://weblate.frostnerd.com/engage/nebulo/?utm_source=widget">
    <img src="https://weblate.frostnerd.com/widgets/nebulo/-/svg-badge.svg" alt="Übersetzungsstatus" />
  </a> 
[![pipeline status](https://git.frostnerd.com/PublicAndroidApps/smokescreen/badges/master/pipeline.svg)](https://git.frostnerd.com/PublicAndroidApps/smokescreen/commits/master)
</div>

---

# About
Nebulo is a free, open-source, no-root, light-weight dns-over-https, dns-over-tls, and dns-over-http-over-quic client for Android with emphasis on privacy and security. Nebulo is fast, highly-customizable, ad-free, efficient on the battery, contains zero analytics / tracking. Goto [the installation section](#installation) to set up Nebulo on your Android device.

# My mission
My mission is to provide access to dns-over-tls and dns-over-https as a tool against censorship and tracking. Many countries block controversial or government-critical websites using DNS which can possibly be circumvented using either of those protocols.

The second topic, tracking, is nearly as important as the topic of censorship. Many ISPs use their own DNS servers as a way of tracking their users. Using DoH/DoT puts an end to this by encrypting the vulnerable DNS queries.

# How it works
Nebulo uses Android's VPN APIs to create a dummy (local) VPN which intercepts only DNS requests and encrypts them before sending it to a DNS resolver of your choice. This dummy (local) VPN is __not__ a real VPN and does not encrypt any other traffic or hide your real IP. As only one VPN can be active per profile at any given time, you can decide between using Nebulo or a real VPN.

## Non-VPN mode
Look for it in [the FAQ section](FAQ.md).

## What this is based on
Nebulo is wholly an original piece of software: It doesn't use any other dependency for its core DNS capabilities. Inspect [the build file](https://git.frostnerd.com/PublicAndroidApps/smokescreen/blob/master/app/build.gradle#L100) to see what is used under the hood.

## Incompatibilities, compatibilities, and possible limitations
- No other VPN can be active when Nebulo is running (at least when not running in Non-VPN mode mentioned above).
- Nebulo works fine with non-VPN firewalls in place (such as AFWall+), but changes in firewall profiles [could break the VPN](https://git.frostnerd.com/PublicAndroidApps/smokescreen/issues/84), requiring a restart of Nebulo.
- Other means of ad-blocking, like modifying the `/etc/hosts` file manually or using the AdAway app works alongside Nebulo just fine; though, the AdGuard app doesn't (that is, Nebulo does not receive any DNS queries when AdGuard's running).

## Core features
 * Encrypted DNS using dns-over-https, dns-over-tls, and dns-over-http-over-quic protocols.
 * Customizable in-memory DNS cache with configurable cache-expiry.
 * Preset list of privacy-respecting DNS servers.
 * Add any DNS server.
 * DNS speed test.
 * DNS query logging.
 * DNS blocklists.
    * Blocklist rules can be imported from remote URLs or on-device files (supports 4 different formats).
    * Blocks domains with _unspecified IPs_, `0.0.0.0` and `::0000`.
    * Automagically protects from [CNAME cloaking](https://webkit.org/blog/11338/cname-cloaking-and-bounce-tracking-defense/).
 * Advanced settings:
    * Disable IPv4 / IPv6.
    * Allow captive portals.
    * Allow search domains on the current network.
    * ... and more.

# FAQ
For a growing collection of frequenty asked questions, [take a look here](FAQ.md).

# Help wanted
Translations are important to reach as broad of an audience as possible and for non-english speakers to be able to use the app to its full extent. [Head over to the translation guide](TRANSLATING.md) to see how you can help!

# Installation
Nebulo is distributed over Google Play Store, Aurora Store, a custom F-Droid repo, and as a standalone `.apk` file.

## Play Store
[Download the latest version](https://play.google.com/store/apps/details?id=com.frostnerd.smokescreen) from Google Play Store.

## Aurora Store
Search for Nebulo and download it from [the Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore), which is a G-Play mirror.

## Aurora Droid
[Aurora Droid](https://gitlab.com/AuroraOSS/auroradroid) is an alternative to the F-Droid app. Follow the F-Droid instructions below.

## F-Droid
Follow these steps if you're using F-Droid:

1. [Click to add Nebulo](https://fdroid.frostnerd.com/fdroid/repo?fingerprint=74BB580F263EC89E15C207298DEC861B5069517550FE0F1D852F16FA611D2D26) to your F-Droid.
    - Or, add it manually, `fdroid.frostnerd.com/fdroid` with fingerprint `74BB580F263EC89E15C207298DEC861B5069517550FE0F1D852F16FA611D2D26`.
    - Or, add it by [scanning this QR-Code](material/fdroid_qr.jpg).
2. (Optional) For older versions of Nebulo, [click to add this repositorty instead](https://fdroidarchive.frostnerd.com/?fingerprint=74BB580F263EC89E15C207298DEC861B5069517550FE0F1D852F16FA611D2D26).
    -  Or, add it manually, `fdroidarchive.frostnerd.com` with fingerprint `74BB580F263EC89E15C207298DEC861B5069517550FE0F1D852F16FA611D2D26`.
3. Update your repositories (by pulling down to refresh F-Droid).
4. Search for Nebulo and download to install the app.

## Binary
- Grab the _latest_ `.apk` file from [the Nebulo telegram group](https://nebulo.app/community).
- Grab a _signed_ build [on GitLab](https://git.frostnerd.com/PublicAndroidApps/smokescreen/-/jobs/artifacts/master/raw/app/build/outputs/apk/normal/release/app-normal-release.apk?job=build_release).
    - The _signed_ builds aren't always the _latest_ or _stable_, but are merely signed _development_ builds.
    
# Community

[Join the Nebulo community on Telegram](https://nebulo.app/community) to ask for help or connect with the developer. Head over to [the announcements channel](https://nebulo.app/updateChannel) to get updates on upcoming releases and features.<br>

Or, participate in [the XDA-Developers discussion thread](https://forum.xda-developers.com/t/app-5-0-nebulo-dns-changer-for-doh-dot-against-censorship-open-source-no-root.4156645/).

# Issues, and feature requests
Have feature ideas or stumbled upon bugs? Feel free to create issues [on GitLab](https://git.frostnerd.com/PublicAndroidApps/smokescreen/issues) or [on GitHub](https://github.com/Ch4t4r/Nebulo/issues).

# Developer contact

**E-Mail**: [daniel.wolf@frostnerd.com](mailto:daniel.wolf@frostnerd.com)

**Telegram**: [@Ch4t4r](https://t.me/Ch4t4r)

**Skype**: [daniel38452](skype:daniel38452)

# Credits
A list of some extraordinary people who contributed to this project:
 - App icon and notification icon by [RKBDI](http://dribbble.com/rkbdi).
 - Turkish translation by Kemal Oktay Aktoğan
 - Russian translation by [bruleto](https://t.me/bruleto)
 - Dutch translation by Bas Koedijk
 - Portuguese translation by Rafael W. Bohnenberger
 - Indonesian translation by Gloeyisk
 - Catalan translation by Daniel Alomar
 - Spanish translation by Victor Bayas
<br/>

**Want to see your name here? Feel free to contribute!**

# License
This work is licensed under the GNU GPLv3 License. Different license-agreements can be made with the developer for parts of the app, if needed.

Copyright (C) 2021 Daniel Wolf

<br>
Please be aware that I'm not going to tolerate exact copies of this app on the Play Store. This project took a lot of work, not only from me, but many alpha/beta testers and translators. Copying is - per the license - generally allowed, but uploading a nearly identical version to the Play Store would be impersonation as per Google policy.

# Cloning
Feel free to clone this repository. However, there are a few things to consider:
- Nebulo uses some of my own libraries. Those are FOSS as well, you can find the URLs in the `build.gradle`.
   - These libraries are distributed as pre-built binaries using an Nexus3 server. Public credentials are contained in the root `build.gradle`
   - Alternatively, replace the dependencies (`implementation 'com.frostnerd.utilskt:....`) with git sub-modules (`implementation project(...)`) after cloning the libraries.
- Nebulo uses icons from Font Awesome covered under their free license with attribution.
