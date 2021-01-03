# Non-VPN mode
As explained in the [FAQ](../FAQ.md) Nebulo has a non-VPN mode where it does not require the dummy VPN.<br>
As this dummy VPN is normally used to force Android to send all DNS queries to Nebulo you have to configure this yourself.<br>
If your phone is rooted you simply have to enable the iptables setting (which you also only see when your phone is rooted). If you want to know how it works scroll down.<br><br>
If your phone is not rooted you have to use third-party apps to forward DNS queries to Nebulo.

## Server port
Use this setting to configure the port Nebulo hosts the DNS server on.
It cannot be lower than `1024` and not higher than `65534`.<br>
The default is `11053`.

## Supported third-party apps
Some apps which can be used to forward the DNS queries from the device to Nebulo in non-VPN mode.<br>
This list is not exhaustive and there might be more apps.
Feel free to let me know if you found another one which does :)

### NetGuard
[NetGuard](https://github.com/M66B/NetGuard) is a firewall app you can use to block internet access of apps on your device.
It has a lot of customization options.<br>
By using it together with Nebulo you greatly increase your privacy and security as you now can keep your DNS queries safe while also controlling what your apps do with their internet access.
<br><br>
To combine Nebulo in non-VPN mode with NetGuard, first make sure you have the F-Droid version of NetGuard installed (or the one from their website).<br>
Then follow these steps (Click on the step for a screenshot):
 - Open the settings of Netguard, go to Advanced options
 - [Enable the settings "Filter traffic" and "Filer UDP traffic"](../material/faq/netguard_step2.png)
 - [Click on "Port forwarding"](../material/faq/netguard_step3.png)
 - [Add the following rule (plus button in the top bar)](../material/faq/netguard_step4.png):
    - Protocol UDP
    - Source port: 53
    - Destination address: 127.0.0.1
    - Destination port: The port you configured in the settings for Nebulos non-VPN mode (default 11053)
    - Destination app: nobody
  - Repeat, but use TCP as protocol
  - Go back to Advanced options
  - [Scroll down, set both "VPN DNS" fields to `127.0.0.1`](../material/faq/netguard_step7.png)
  - Start both Nebulo (with non-VPN mode enabled) and NetGuard

That's it, now both are running at the same time! You should see the query count increase in Nebulos notification.


### RethinkDNS
[RethinkDNS](https://rethinkdns.com) is an app very similar to Nebulo which also offers encrypted DNS and has additional features like a firewall and the capability to use an upstream SOCKS5 proxy.
By chaining Nebulo behind RethinkDNS you can keep all features Nebulo offers, whilst also being able to use a firewall or any VPN app which supports SOCKS5, like Orbot.
Setting it up is simple:
1. Open RethinkDNS, click on the `DNS` setting
2. Click on configure, select `DNS Proxy` from the drop-down
3. Click on add, use `127.0.0.1` for IP Address and `11053` (or your configured Non-VPN mode port) for port.
4. Start Nebulo in Non-VPN mode, then start RethinkDNS
(5. Not required, but recommended doing) In RethinkDNS, go to settings and exclude Nebulo from Firewall and DNS

### Orbot/V2Ray
Orbot/V2Ray can be used together with Nebulo through RehinkDNS.
Follow the steps [above](#rethinkdns), additionally do the following:
1. In RethinkDNS, go to settings and exclude Orbot/V2Ray from Firewall and DNS
2. Scroll down a bit, enable the `SOCKS5 proxy` and `HTTP(S) Proxy` option
  - For SOCKS5, set the port to 9050 (10808 if using V2Ray), App to Orbot/V2Ray
  - For HTTPS, set the port to 8118 (10809 if using V2Ray), App to Orbot/V2Ray
  
#### V2Ray without RethinkDNS
[V2Ray](https://github.com/hetykai/V2Ray-Android) is an app supporting multiple protocols like Shadowsocks to hide your identity and anonymize your browsing.<br>
To use Nebulo with it you manually have to edit your config file to use the DNS server Nebulo hosts in non-VPN mode.
It is available at 127.0.0.1 and the port you configured in the settings (default 11053).<br>
You can use [this documentation](https://www.v2ray.com/en/configuration/dns.html) from V2Ray to see how you have to configure it.<br><br>
After configuring both apps simply start them and the query count in Nebulos notification should increase.
As this is complicated and cannot be done with V2Rays UI I recommend using RethinkDNS as described above.

## Battery usage
There shouldn't be any difference in Nebulos battery usage when using non-VPN mode.
If you use third-party apps to forward DNS queries to Nebulo they do use battery though.

## Iptables
As mentioned above you can use iptables on a rooted phone to forward DNS queries to Nebulo in non-VPN mode (iptables mode).<br>
When enabled it creates a few iptable rules when Nebulo is started and removes them when it is stopped.
A notification will appear if anything goes wrong when creating the rules, warning you about it.<br>
 
### IPv6 support
At this time close to all devices don't have the proper version of ip6tables to allow Nebulo to forward DNS queries with it.<br>
If your device has IPv6 this most likely causes all DNS queries to bypass Nebulo.
This is of course not desired.<br>
To mitigate this problem I suggest to activate the "Disable IPv6" setting in iptables mode.
It prevents _all_ IPv6 connections on Port 53 (IPv6 connectivity is still going to work!).
This forces Android to fallback to IPv4, where Nebulo is going to receive the queries.

### Checking support
It is recommended to check for iptables support before enabling the iptables mode.
To do that click on the "Check for iptables" entry.<br>
It will tell you whether your device supports iptables, iptables for IPv4 only or doesn't support iptables at all.

### Rules used
The rules Nebulo uses in iptables mode:
 - Creating redirect for IPv4: `iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:<port>"` and `iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:<port>"`
 - Creating redirect for IPv6: `ip6tables -t nat -I PREROUTING -p udp --dport 53 -j DNAT --to-destination [::1]:<port>"` and `ip6tables -t nat -I PREROUTING -p tcp --dport 53 -j DNAT --to-destination [::1]:<port>"` (As mentioned most devices don't support this command)
 - Disabling IPv6 for Port 53: `ip6tables -A OUTPUT -p udp --destination-port 53 -j DROP` and `ip6tables -A OUTPUT -p tcp --destination-port 53 -j DROP`
 - Removing redirect for IPv4: `iptables -t nat -D OUTPUT -p udp --dport 53 -j DNAT --to-destination <ip>:<port>"` and `iptables -t nat -D OUTPUT -p tcp --dport 53 -j DNAT --to-destination <ip>:<port>"`
 - Removing redirect for IPv6: `ip6tables -t nat -D PREROUTING -p udp --dport 53 -j DNAT --to-destination [::1]:<port>` and `ip6tables -t nat -D PREROUTING -p tcp --dport 53 -j DNAT --to-destination [::1]:<port>`
 - Re-enabling IPv6 for Port 53: `ip6tables -D OUTPUT -p udp --destination-port 53 -j DROP` and `ip6tables -D OUTPUT -p tcp --destination-port 53 -j DROP`

### iptables cleanup
As mentioned Nebulo automatically cleans up after itself, removing the iptables rules it created.<br>
If Nebulo is force-stopped (either by you or by the system) it isn't able to cleanup after itself.
This causes the rules to remain and because Nebulo doesn't host the DNS server anymore will cause you to not have any connection.<br>
To remove the rules simply open Nebulo again (you don't have to start it, opening the app is enough).
It detects that the rules were not properly removed and will delete them.
