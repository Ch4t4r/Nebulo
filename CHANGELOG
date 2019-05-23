1.0-Alpha, Build 18:
 - Fixed a crash when the vpn was started while the phone was connecting to a network
 - Fixed the app not using DoH when the network has a search domain

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

1.0-Alpha, Build 16:
 - Added more IP addresses for the keweon null-route feature (it didn't nullroute everything)
 - Changed the .dohserver file format (now contains more information and one file can host multiple servers)
 - The "Add server" button now doesn't scroll with the servers anymore
 - You can now use non-default ports in the URLs
 - The query logging menu item now shows/hides instantly (previously the app had to be restarted once)
 - Fixed a lot of crashes and other bugs

1.0-Alpha, Build 15:
 - The app doesn't crash anymore when accessing the Cache twice at the same time
 - The app doesn't crash anymore when answering with SERVER_FAIL to the device when the upstream server returned an error
 - Made the color of titles in the preferences brighter when using the true black theme

1.0-Alpha, Build 14:
 - Logged queries can now be exported as .csv
 - Adjusted the true black theme
 - Fixes a bug preventing you from inserting two custom servers
 - App now works with Google DNS (yey)
 - Fixed a bug crashing the app when the server encountered an error (i.e. HTTP code 500)
 - Fixed a bug crashing the app sometimes when there was an error transmitting the data

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

1.0-Alpha, Build 12:
 - Small christmas overhaul of the app, no functional changes

1.0-Alpha, Build 11:
 - A dialog is now shown when the app crashes asking whether you want to send the logs to me (If logging is enabled)
 - Crash logs (contains the stacktrace, app and android version) are now always created, even if logging is disabled
 - Searchdomains (Domains only valid inside your own network, e.g. PcDaniel.fritz.box) can now be sent to your DHCP dns server. This can be disabled in the settings. Those requests don't use DoH, but don't leave your network.
 - Fixed the crash when changing the theme twice
 - Added Tasker Action plugin for starting/stopping

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

1.0-Alpha, Build 9:
 - The DNS queries are now logged in the internal logs. This will be removed in future releases, but is useful for debugging right now.
 - The VPN now only uses IPv6 when the device is able to use IPv6 (led to crashes)
 - If the VPN is killed by the system and restarts automatically it now correctly checks if it has permissions to do so (if not it asks for it - that might trigger a popup in some cases)
 - The logs can now be sent via E-mail and as general export, you'll be asked what you want
   --> You can now configure the cache in the settings (enable/disable it, set the cache time for entries, set a maximum cache size [This isn't working properly right now]). The cache is enabled by default.

1.0-Alpha, Build 8:
 - The notification doesn't make sounds anymore
 - The notification now shows how many entries are cached
 - Added logging which collects some debug info for me (only locally on your device, you can send the logs to me by hand)

1.0-Alpha, Build 7:
 - Added the telegram group link to the sidebar menu
 - Added a setting to disallow other VPNs
 - The VPN is now more stable
 - The list of excluded apps now shows apps selected by default (but you won't be able to de-select them)
 - The VPN now restarts automatically when selecting a new server or changing the excluded apps
 - Some internal changes

1.0-Alpha, Build 6:
 - Altered the underlying Vpn library to achieve stability and better performance

1.0-Alpha, Build 5:
 Excluded apps setting:
  - You can now choose whether to show system apps or not
  - A text now shows how many apps are excluded by default

 Bugfixes:
  - When deleting a custom server in the server dialog (by long clicking on it) which was being used, the configuration is now
    reset to default.

- Added a few new entries into the menu

1.0-Alpha, Build 4:
 - You can now configure apps in the settings which shouldn't use dns-over-https -- those apps will bypass SmokeScreen

1.0-Alpha, Build 3:
 - Bugfixes, the app can now start automatically after the phone finished booting

1.0-Alpha, Build 2:
 - First release of the Alpha version.

1.0-Alpha, Build 1:
 - Internal test Build