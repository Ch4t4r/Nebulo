# What is DoQ?
DoQ stands for DNS-over-QUIC. QUIC - like TLS or HTTP(S) is a network protocol used to transfer data. It is secure like TLS, but way faster.
By using it for transferring DNS queries you gain the same security as with DoH or DoT, but should experience a much lower latency.

# Does Nebulo have DoQ?
No, Nebulo does not have DoQ. It lacks the low-level programming code which would be needed to send data via the QUIC protocol. So you won't be able to use addresses starting with `quic://`.

# That sucks, is there anything comparable?
There is! Nebulo implements "DNS-over-HTTP-over-QUIC" (although not official, we call it DoH3), which is DoH just sent over a QUIC connection and not - as usual - over TLS.<br>
It has the same speed (and of course security) as DoQ, the only downside is that not all servers with support DoQ support DoH3. A big benefit of DoH3 however is that Nebulo uses it automatically without you having to configure anything. If a DoH server supports DoH3 it is automatically used.

# Which servers support it?
A lot, actually. A few of them are Cloudflare, Quad9, AdGuard, RethinkDNS (or any other DNS server running in cloudflares network).
