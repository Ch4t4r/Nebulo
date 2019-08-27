<div align="center">
  <a href="" rel="noopener">
  <img width=200px height=200px src="app/src/main/res/mipmap-xxhdpi/ic_launcher.png" alt="Project logo"></a>
</div>

<div align=center>
    <h3 align="center">Nebulo</h3>
</div>
<div align="center">

  [![Apk size](https://img.shields.io/badge/Apk%20file%20size-4.7%20MB-blue)]() 
  [![License](https://img.shields.io/badge/license-GPLv3-blue.svg)](/LICENSE)
  <a href="https://weblate.frostnerd.com/engage/nebulo/?utm_source=widget">
    <img src="https://weblate.frostnerd.com/widgets/nebulo/-/svg-badge.svg" alt="Übersetzungsstatus" />
  </a> 
[![pipeline status](https://git.frostnerd.com/PublicAndroidApps/smokescreen/badges/master/pipeline.svg)](https://git.frostnerd.com/PublicAndroidApps/smokescreen/commits/master)
</div>

---

# About
Nebulo is a free, open-source, non-root and small sized DNS changer utilizing dns-over-https and dns-over-tls to bring privacy and security to your phone.
It is fast, contains no ads or tracking and offers a lot of flexibility.

# Our mission
Our mission is to provide access to dns-over-tls and dns-over-https as a tool against censorship and tracking. Many countries block controversial or government-critical websites using DNS which can possibly be circumvented using either of those protocols.
The second topic, tracking, is nearly as important as the topic of censorship. Many ISPs use their own DNS servers as a way of tracking their users. Using DoH/DoT puts an end to this by encrypting your dns queries.

# How it works
Nebulo uses the VPN API of the Android system to create a dummy VPN which intercepts all packets for the dns servers of your device. This dummy VPN is __not__ a real VPN and does not tunnel your packets - it only handles dns packets. As only one VPN can be activate at any given time you have to decice between using Nebulo or a real VPN.

## What this is based on
Nebulo is a completely original piece of software. It doesn't use any other dependency under the hood for the dns capabilities. Check the [dependencies](https://git.frostnerd.com/PublicAndroidApps/smokescreen/blob/master/app/build.gradle#L100) to see what is used for everything build around DoH/DoT.

## Incompatibilities, compatibilities and possible problems 
- As mentioned no other VPN can be active when Nebulo is running.
- Nebulo works fine with non-VPN firewalls in place (such as AFWall+), but changes in firewall profiles [could break the VPN](https://git.frostnerd.com/PublicAndroidApps/smokescreen/issues/84), requiring a restart of Nebulo.
- Other means of ad-blocking, like modifying the `/etc/hosts` file or the `AdAway` app go hand in hand with Nebulo as well. 
    - The AdGuard app might cause Nebulo not to receive DNS queries

## Core features
The app consists of a few core features:
 * Dns forwarding using dns-over-https and dns-over-tls
 * A customizable in-memory DNS cache
    * You can configure minimum caching time and default caching times
 * A list of servers with a lot of default entries
    * You can add your own servers as well
 * A DNS speed test
 * Query logging
 * DNS rules, where you can specify your own IP addresses for hosts
    * Rules can be imported from files and URLs (supports 4 different formats)
 * Highly customizable settings
    * Disable IPv4/IPv6
    * Allow captive portals
    * Allow search domains on the current network
    * ... And more

# Help wanted
Requesting your support: the app is getting closer to a proper release but it's still missing an important aspect: translations.
Translations are important to reach as broad of an audience as possible and for non-english speakers to be able to use the app to it's full extent.
Head over to the [translation guide](TRANSLATING.md) to see how you can help!

# Installation
The app is distributed over the play store, F-Droid and as .apk file.

## Play store
Go to the [play store](https://play.google.com/store/apps/details?id=com.frostnerd.smokescreen&), download the app and have fun.

## F-Droid
1. Add the repository on your F-Droid app by clicking [this link](https://fdroid.frostnerd.com/fdroid/repo?fingerprint=74BB580F263EC89E15C207298DEC861B5069517550FE0F1D852F16FA611D2D26).
    - Or add it yourself, fdroid.frostnerd.com/fdroid with fingerprint 74BB580F263EC89E15C207298DEC861B5069517550FE0F1D852F16FA611D2D26
    - You can use the [QR-Code as well](material/fdroid_qr.jpg) .
    - I recommend using [Aurora Droid](https://gitlab.com/AuroraOSS/auroradroid) , it contains the repository by default
2. Update your repositories (by pulling down to refresh)
3. Search for Nebulo
4. Download the app.
5. Don't forget to check for updates sometimes.


## Binary
The file is distributed as .apk file in two places:
- In the [telegram group](https://t.me/joinchat/I54nRleveRGP8IPmcIdySg)
- In the automated build system (CI) here in GitLab. [Click here](https://git.frostnerd.com/PublicAndroidApps/smokescreen/-/jobs/artifacts/master/raw/app/build/outputs/apk/normal/release/app-normal-release.apk?job=build_release) to download the latest signed build.
    - The latest signed build isn't always the current release, keep in mind that those are merely signed development builds.
    
# Community
Want to be always up-to-date on the development of this app? Looking for a way to contact the developer?

Join our [Telegram group](https://t.me/joinchat/I54nRleveRGP8IPmcIdySg)!
(Alternativly [the channel](https://t.me/NebuloUpdates) , which contains only updates and nothing else)

# Issues, feature requests and questions
Have an idea, question or encountered a bug? Feel free to create an issue [right here in GitLab](https://git.frostnerd.com/PublicAndroidApps/smokescreen/issues).

# Developer contact
There are several ways for you to contact the developer:

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
<br/>
<br/>

**Want to see your name here? Feel free to contribute!**
 
 
# License
This work is licensed under the GNU GPLv3 License. Different license-agreements can be made with the developer, if needed.


Copyright (C) 2019   Daniel Wolf

<br>
<br>
Please be aware that I'm not going to tolerate exact copies of this app on the Play Store. This project took a lot of work, not only from me, but many alpha/beta testers and translators. Copying is - per the license - generally allowed, but uploading a nearly identical version to the Play Store would be impersonation as per Google policy.

# Third-party content
This work contains third-party content, namely:
- [sentry-java](https://github.com/getsentry/sentry-java) for crash-reporting
   - License: [BSD 3-Clause revised ](https://opensource.org/licenses/BSD-3-Clause)
- [Material Design Icons](https://material.io/tools/icons/)
   - License: [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
   - The notification and launcher icons are remixes of the cloud icon
- [ANR-WatchDog](https://github.com/SalomonBrys/ANR-WatchDog)
   - License: [MIT](https://opensource.org/licenses/MIT)
- [Font Awesome icons](https://fontawesome.com/)
   - License: Font Awesome Pro License, held by Daniel Wolf
- [Weblate](https://weblate.org) for managing translations
   - License: GPLv3
- [FABProgressCircle](https://github.com/JorgeCastilloPrz/FABProgressCircle) for showing a loading indicator around floating action buttons
   - License: [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [LeakCanary](https://github.com/square/leakcanary) for finding memory leaks
   - License:[Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [LeakSentry](https://github.com/square/leakcanary/tree/master/leakcanary-leaksentry) for finding memory leaks
   - License:[Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Gson](https://github.com/google/gson)
   - License:[Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [OkHttp](https://github.com/square/okhttp)
   - License:[Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Pcap4j](https://github.com/kaitoy/pcap4j)
   - License:[MIT](https://opensource.org/licenses/MIT)


# Cloning
Feel free to clone this software. However, there are a few things to notice:
- This app uses some of my own libraries which are only accessible when logged into this GitLab instance. Signing up is free and no tracking is in place.
   - These libraries are distributed using an Artifactory server. **This server is not public, but I do hand out credentials on request**.
   - Alternatively, replace the dependencies (`implementation 'com.frostnerd.utilskt:....`) with git sub-modules (`implementation project(...)`) after cloning the libraries.
- I own a Font Awesome Pro license and use a lot of their icons. Most of the icons used in this project are accessible with a Font Awesome Free license, but not all necessarily are. Either you have to own a license yourself, or check whether a particular icon is also usable with the Free license.

</br>

</br>

</br>