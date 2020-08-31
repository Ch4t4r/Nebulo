All dates in dd.mm.yyy.

**30.08.2020**<br>
1.4.1, Build 63:
- This is a stability update which fixes a few bugs and crashes.

1.4.1.1, Build 65:
- Hotfix for 1.4.1


**29.07.2020**<br>
1.4.0, Build 62:
- When starting Nebulo from a server shortcut that server is now persisted
- Added support for iptables in non-vpn mode. This requires a rooted device
- DNS rule sources can now be updated individuall
- The internal DNS server can now be configured

**07.07.2020**<br>
1.3.0, Build 61:
- There are now new translations
- Battery usage has been reduced a bit
- Nebulo now detects the type of new DoH servers
- ... And more


**30.06.2020**<br>
1.3.0-Beta, Build 59:
- Added new translations
- Added reusing connections, reducing battery usage
- Nebulo now detects the supported mode when adding a DNS-over-HTTPS server
- Nebulo can now be resumed from the main screen
- Fixed input validation of DNS-over-HTTPS not allowing some valid servers
- The PIN now has to be re-entered after some time
- Nebulo can now run in (experimental) non-VPN mode, where it uses a DNS Server instead of a dummy VPN. This requires third-party apps for forwarding DNS queries to Nebulo

**20.01.2020**<br>
1.2.3, Build 58:
- The primary host/url could wrongly be empty when adding a server
- A notification is now shown when Nebulo loses permission to the VPN

**04.01.2020**<br>
1.2.2, Build 57:
- Fixed a crash from the last release


*[Build 56 skipped]*

**01.01.2020**<br>
1.2.1, Build 55:
- Bugfixes for previous release

**01.01.2020**<br>
1.2.0, Build 54:
- Fixed some VoIP apps not connecting when Nebulo is active
- Creating a server shortcut now lets you select one from the list
- Fixed not clearing the export count when deleting logged queries
- Fixed a few crashes

**29.11.2019**<br>
1.1.0, Build 53:
- This version fixes a crash and a bug which caused the query log to not save correctly.

**27.11.2019**<br>
1.0.3, Build 52:
- The DNS Rules now prevent CNAME cloaking
- Fixed a bug which prevented DoH servers with multiple slashes (/) being added properly
- Fixed a crash which rarely happened with query logging active

**23.11.2019**<br>
1.0.2, Build 51:
- Whitelist Dns Rules can now be exported as well
- Fixed wildcard Dns Rules not being exported correctly
- Updated servers

**17.11.2019**<br>
1.0.1, Build 50:
- Bugfixes for previous release

**17.11.2019**<br>
1.0.0, Build 49:
- First non-Beta release!
- A setting has been added which clears the DNS cache
- The automatic crash reporting can now be set to full, minimal and off (previously only on/off was possible)
- The query log now shows an indication when the DNS server might have blocked the host
- The DNS rules are now way faster
- Fixed a few bugs which could cause the app to not forward DNS queries after some time on some devices
- And more

**12.10.2019**<br>
1.0-Beta, Build 48:
- Added a setting to simplify the notification
- The query log now shows the host source of the DNS rule, if the question was answered from the DNS rules
- The language now properly switches in all windows when changing it in the app settings
- Fixed a lot of crashes

**07.10.2019**<br>
1.0-Beta, Build 47:
- The query log now shows the TTL and IP responses
- The query log can now be searched for specific hosts
- The language can now be changed from within the app
- The main screen now displays whether private DNS is active (Android 9+)
- Added news servers
- Design changes
- Improved handling of dns rules

**24.08.2019**<br>
1.0-Beta, Build 46:
- Split the settings into multiple layouts to increase readability
- Overhauled the query log

**21.08.2019**<br>
1.0-Beta, Build 45:
- Split the settings into multiple layouts to increase readability
- Overhauled the query log

**10.08.2019**<br>
1.0-Beta, Build 44:
- The app should now be able to recover itself when the screen has been off for some time
- Dns rules can now be configured to updated periodically
- Host sources are now only downloaded/parsed when their content changed
- The dns rules now support wildcards

**27.07.2019**<br>
1.0-Beta, Build 43:
- The server info in the side bar menu now shows the current latency
- The speed test now only shows servers which support the current network
- Fixed dnsmasq host sources not being imported
- The dns rules view now shows the total amount of rules
- A dialog is now shown when the system kills the app, informing about possible solutions
- Fixed a few crashes


**30.06.2019**<br>
1.0-Beta, Build 42:
- Fixed crashes from previous release

**22.07.2019**<br>
1.0-Beta, Build 41:
- Dns rule sources can now be used as whitelists
- The host sources now indicate how many rules were duplicate in a file
- Added new servers
- User-defined servers can now be edited
- The server list now only shows server which support the current network (IPv4 or IPv6)
- Host sources can now be edited by clicking on them
- The dns rules now don't open a separate window anymore
- Fixed a few crashes

**15.07.2019**<br>
1.0-Beta, Build 40:
- Performance and memory improvements
- All servers in the server dialog can now be deleted by long-clicking them
- Google Play can now be removed from the list of bypass apps
- You can now whitelist domains in the dns rules. If whitelisted they will be sent to the dns server, regardless of whether they are present in a file you provide

**11.07.2019**<br>
1.0-Beta, Build 39:
 - This is a performance focused update which should make dns queries faster, use less memory and be less battery consuming.

**11.07.2019**<br>
1.0-Beta, Build 38:
 - This is a performance focused update which should make dns queries faster, use less memory and be less battery consuming.

**08.07.2019**<br>
1.0-Beta, Build 37:
 - Fixed some crashes
 - A few design improvements
 - The dns rule import is now a bit faster
 - Imported dns rules now take less space on the device

**30.06.2019**<br>
1.0-Beta, Build 36:
- You can now export the dns rules
- Dns rules can now be imported from files
- Fixed dns rules not being imported from very small sources
- The rule import can now recognize non-default letters (such as umlauts)
- Moved the dns rules to the sidebar menu
- Fixed a crash which occurred on some device

**28.06.2019**<br>
1.0-Beta, Build 35:
- Fixed some crashes related to the dns rule import
- Aborting the dns rule import now doesn't freeze the UI anymore
- Small performance improvement for dns-over-https
- The host sources for the dns rules now show how many rules were imported from them

**28.06.2019**<br>
1.0-Beta, Build 34:
 - Fixed crashes from previous release

**28.06.2019**<br>
1.0-Beta, Build 33:
 - Added Turkish, Indonesian, Russian and Dutch translations
 - The notification shown when the app crashes when automatic crash reporting is disabled now has a button to send the log files
 - Replaced some of the icons
 - Fixed a few crashes
 - Added a view to test dns server speeds
 - You can now specify custom IP addresses for hosts. Hosts can be imported from URLs and added by hand.
 - A few design tweaks

**19.05.2019**<br>
1.0-Beta, Build 32:
 - Overhauled the UI a bit
 - Dns-over-TLS now works more reliable in bad networks
 - Added a setting to intercept foreign DNS traffic (for example because of async DNS)
 - Added a view to the drawer which shows the current server in use and the IP-addresses it resolves to
 - The app now uses different internal addresses
 - Fixed a few crashes and app freezes

**17.05.2019**<br>
1.0-Beta, Build 31:
- Added partial German translation
- Added settings to disable the stop/pause buttons of the notification
- A notification is now shown if the app crashes and automatic crash reporting is enabled
- Fixed a few crashes

**16.05.2019**<br>
1.0-Beta, Build 30:
 - Added new notification icon
 - Fixed the privacy policy not loading
 - Clarified the blacklist/whitelist text in the notification
 - Fixed a few crashes

**14.05.2019**<br>
1.0-Beta, Build 29:
 - Added new app icon 
 - Fixed a lot of crashes
 - Added automatic crash reporting. This reporting is privacy-friendly and opt-in.
 - Added a setting to clear logged queries

**29.04.2019**<br>
1.0-Beta, Build 28:
- The stop button in the notification works again
- A higher timeout is now used for DoH servers (should improve network on bad connections)
- Fixed some DoT servers not working
- UncensoredDns is now used as default server
- Small performance improvements

**30.03.2019**<br>
1.0-Beta, Build 27:
- DoH servers which return invalid responses from time to time (e.g PowerDNS) now won't crash the app
- Fixed a lot of other crashes
- Logs can now be shared again when the app crashes
- The notification shown when the app has no/a bad connection is now turned off by default and can be enabled in the settings
- Added a pause button to the notification

**24.03.2019**<br>
1.0-Beta, Build 26:
- The app doesn't crash anymore for servers which only have IPv4 addresses when the network only has IPv6 addresses
- Fixed the no connection notification not vanishing properly
- The no connection notification can now be dismissed
- The app had the wrong servers of UncensoredDNS

**23.03.2019**<br>
1.0-Beta, Build 25:
- Improved handling of IPv6/IPv6 - the app now works reliable in networks which only support either
- Added new DNS servers
- A notification is now shown when there is no/a bad connection
- The app doesn\'t crash anymore if an unknown host is used - it shows the aforementioned notification instead

**15.03.2019**<br>
1.0-Beta, Build 24:
 - Fixed crashes from previous release

**15.03.2019**<br>
1.0-Beta, Build 23:
 - Non-default ports are now shown for DoT servers
 - Added a dialog to highlight changes
 - Fixed the server import not working on OS versions below Android 7
 - You can now pin protect the app in the settings

**11.03.2019**<br>
1.0-Beta, Build 22:
 - Small fixes for DoT

**11.03.2019**<br>
1.0-Beta, Build 21:
 - Added Dns-over-Tls (DoT). Either dns-over-tls or dns-over-https can be used.

**05.03.2019**<br>
1.0-Beta, Build 20:
 - This is the first beta release of the app.
 - Performance improvements
 - Decreased apk size
 - Added custom cache time for NXDOMAIN responses
 - Fixed some crashes

**07.01.2019**<br>
1.0-Alpha, Build 19:
 - Set the license of the project to GPLv3
 - Added shortcuts on the launcher to quickly start the app with preconfigured servers (and to switch easily between them)
 - Disabling IPv4/IPv6 doesn't break Internet access anymore
 - Added settings to allow/deny IPv6 or IPv4 traffic entirely. Defaults to allow
 - Increased speed of the dns resolution
 - Fixed some crashes of the underlying VPN architecture

**05.01.2019**<br>
1.0-Alpha, Build 18:
 - Fixed a crash when the vpn was started while the phone was connecting to a network
 - Fixed the app not using DoH when the network has a search domain

**05.01.2019**<br>
1.0-Alpha, Build 17:
 - The Server import feature now has a name and doesn't just show "Smokescreen" anymore
 - The server import feature now doesn't open all files anymore
 - The server import now doesn't show up in the list of recents anymore
 - Added a setting to not send queries over DoH if a captive portal (login site before internet access is granted) is detected
 - The app now restarts quietly when the network changes
 - The app now doesn't try to send DoH packets anymore if there is no network connection (it still tries if there is no Internet)
 - The cache now works properly and doesn't cause timeouts and such after time
 - Added a "Minimum cache time" preference
 - The "Export queries" setting now shows how many queries will be exported
 - The internal log now logs queries better (don't confuse this with the query logging feature ;))
 - Fixed a lot of crashes

**02.01.2019**<br>
1.0-Alpha, Build 16:
 - Added more IP addresses for the keweon null-route feature (it didn't nullroute everything)
 - Changed the .dohserver file format (now contains more information and one file can host multiple servers)
 - The "Add server" button now doesn't scroll with the servers anymore
 - You can now use non-default ports in the URLs
 - The query logging menu item now shows/hides instantly (previously the app had to be restarted once)
 - Fixed a lot of crashes and other bugs

**30.12.2018**<br>
1.0-Alpha, Build 15:
 - The app doesn't crash anymore when accessing the Cache twice at the same time
 - The app doesn't crash anymore when answering with SERVER_FAIL to the device when the upstream server returned an error
 - Made the color of titles in the preferences brighter when using the true black theme

**29.12.2018**<br>
1.0-Alpha, Build 14:
 - Logged queries can now be exported as .csv
 - Adjusted the true black theme
 - Fixes a bug preventing you from inserting two custom servers
 - App now works with Google DNS (yey)
 - Fixed a bug crashing the app when the server encountered an error (i.e. HTTP code 500)
 - Fixed a bug crashing the app sometimes when there was an error transmitting the data

**28.12.2018**<br>
1.0-Alpha, Build 13:
 - App now uses a different library for handling the database (faster & more reliable)
 - Reduced the .apk size by ~200kb
 - Added optimizations to the app making it a little bit faster
 - The VPN now restarts automatically when changing servers
 - Added query logging
   --> Enabling it saves all your queries (and responses from the server)
   --> After enabling it restart the app to add a new entry to the sidebar menu (will happen automatically later)
   --> In this new menu entry you can find a list of all queries, click one to see detailed information.
 - Fixed a crash which happened when trying to open the Navigationdrawer too fast
 - Added a setting to hide the notification from the lock screen
 - Added a setting to hide the notification icon from the notification bar (< Android O only)
 - A dialog is now shown when enabling "start on boot" when the app has battery optimizations enabled
 - Added a setting to null-terminate domains blocked by keweon
   --> This only shows up when keweon is used as primary server

**23.12.2018**<br>
1.0-Alpha, Build 12:
 - Small christmas overhaul of the app, no functional changes

**22.12.2018**<br>
1.0-Alpha, Build 11:
 - A dialog is now shown when the app crashes asking whether you want to send the logs to me (If logging is enabled)
 - Crash logs (contains the stacktrace, app and android version) are now always created, even if logging is disabled
 - Searchdomains (Domains only valid inside your own network, e.g. PcDaniel.fritz.box) can now be sent to your DHCP dns server. This can be disabled in the settings. Those requests don't use DoH, but don't leave your network.
 - Fixed the crash when changing the theme twice
 - Added Tasker Action plugin for starting/stopping

**20.12.2018**<br>
1.0-Alpha, Build 10:
 - Added a QuickSettings tile (the ones in the expanded navigation dropdown of the system) for starting/stopping the app
 - Logging can now be disabled in the settings
   --> Existing logs can be deleted there as well
 - Split the settings into groups
 - Added settings to enable/disable IPv6 and IPv4 in general
   --> By default IPv6 and IPv4 are used if the device has an  address of the respective type assigned. You can force usage in the settings as well
 - Added a button to the notification to stop it without opening the app
 - The VPN now restarts automatically when changing any relevant settings (cache enabled, ipv6/ipv4 enabled, excluded apps changed...)
 - Added a setting to keep the DNS cache across launches of the app. This is very useful if you want to cache old requests indefinitely
 - The back key now works properly to exit the app
 - Other minor fixes

**19.12.2018**<br>
1.0-Alpha, Build 9:
 - The DNS queries are now logged in the internal logs. This will be removed in future releases, but is useful for debugging right now.
 - The VPN now only uses IPv6 when the device is able to use IPv6 (led to crashes)
 - If the VPN is killed by the system and restarts automatically it now correctly checks if it has permissions to do so (if not it asks for it - that might trigger a popup in some cases)
 - The logs can now be sent via E-mail and as general export, you'll be asked what you want
   --> You can now configure the cache in the settings (enable/disable it, set the cache time for entries, set a maximum cache size [This isn't working properly right now]). The cache is enabled by default.

**18.12.2018**<br>
1.0-Alpha, Build 8:
 - The notification doesn't make sounds anymore
 - The notification now shows how many entries are cached
 - Added logging which collects some debug info for me (only locally on your device, you can send the logs to me by hand)

**17.12.2018**<br>
1.0-Alpha, Build 7:
 - Added the telegram group link to the sidebar menu
 - Added a setting to disallow other VPNs
 - The VPN is now more stable
 - The list of excluded apps now shows apps selected by default (but you won't be able to de-select them)
 - The VPN now restarts automatically when selecting a new server or changing the excluded apps
 - Some internal changes

**16.12.2018**<br>
1.0-Alpha, Build 6:
 - Altered the underlying Vpn library to achieve stability and better performance

**14.12.2018**<br>
1.0-Alpha, Build 5:
 Excluded apps setting:
  - You can now choose whether to show system apps or not
  - A text now shows how many apps are excluded by default

 Bugfixes:
  - When deleting a custom server in the server dialog (by long clicking on it) which was being used, the configuration is now
    reset to default.

- Added a few new entries into the menu

**13.12.2018**<br>
1.0-Alpha, Build 4:
 - You can now configure apps in the settings which shouldn't use dns-over-https -- those apps will bypass SmokeScreen

**13.12.2018**<br>
1.0-Alpha, Build 3:
 - Bugfixes, the app can now start automatically after the phone finished booting

**12.12.2018**<br>
1.0-Alpha, Build 2:
 - First release of the Alpha version.

1.0-Alpha, Build 1:
 - Internal test Build