# FAQ
A selection of common questions and a collection of technical aspects of Nebulo. Feel free to ask a question in the issues and I'll add it here as well.

## Non-VPN mode
Since 1.4.0 Nebulo can run without requiring the dummy VPN. In this mode Nebulo hosts a DNS server locally, which forwards all DNS queries it receives according to the settings you configured in Nebulo.<br>
In this mode you manually have to forward all the DNS queries your decice creates to Nebulos local DNS server (normally this is what the dummy VPN is used for).<br>
If your device is rooted Nebulo has an inbuilt solution using `iptables`. If it isn't rooted you have to use third-party apps which are able to forward the DNS queries to Nebulo.
Known third-party apps this works with are NetGuard and V2Ray (although there might be others). You can find instructions on how to configure these apps to work together with Nebulo in the settings.<br>
Please note that the App exclusion setting inside the general category won't have any effect in non-VPN mode. You have to configure excluded apps inside the third-party app you are using.

## Query logging
### What do the icons mean in the query log?
There are 4 icons in the query log:
- Database/server icon: cache is enabled and the DNS response came from cache
- Flag: The response came from the DNS rules OR the upstream DNS server replied with 0.0.0.0 or ::1
- Left pointing arrow: The answer was forwarded to a DNS server
- Questionmark: Unknown what happened with the query (normally you shouldn't see that)

## ESNI
### Is ESNI supported?
Currently no. The Android platform and the libraries I am using lack support for ESNI (https://git.frostnerd.com/PublicAndroidApps/smokescreen/-/issues/237)

### Do I need ESNI?
Most likely no. It would make it harder for government/ISPs to block access to a DNS server though.