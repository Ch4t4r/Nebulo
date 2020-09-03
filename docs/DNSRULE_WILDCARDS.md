As mentioned in the [FAQ](../FAQ.md#dns-rules) DNS rules in Nebulo support wildcards.<br>
They can be used to block/whitelist a number of hosts at once.<br>
This is useful if you have a single domain (let's say `example.com`) but with a lot of subdomains (let's say it has a lot of random subdomains like `aahwz.example.com`) which you all want to block.
Without wildcards you would have to create a rule for every domain.<br>
You can also use it to block all hosts which contain a defined word. Let's say you want to child-proof your phone and block every webpage which contains the word `porn` in the host.
Wildcards are your friend for that.<br>
Wildcards in Nebulo work by either including a single asterisk (\*) or double asterisk(\*\*) in the host when creating a custom rule.<br>
The single asterisk (\*) substitutes for any number of letters and numbers, except for a period (.).<br>
The double asterisk (\*\*) substitutes for any number of letters and numbers, including the period (.).
<br><br>
Below this you can find some examples of wildcards and what they block (or whitelist). Feel free to ask questions if something is unclear.
<br>
- `google.com` to block `google.com` and `www.google.com`
- `*google.com` to block `abcgoogle.com`, `google.com`, ... but not `abc.google.com`
- `*.google.com` to block `abc.google.com`, `abxdefg.google.com` but not `abc.xyz.google.com or `google.com`
- `**.google.com` to block `abc.google.com`, `abc.xyz.google.com` but not `google.com`
- `**google.com` to block everything which ends in google.com (`abc.google.com`, `google.com`, `abc.xyz.google.com`, `abcgoogle.com`, ...)
- `google.*` to block `google.com`, `google.de`, `www.google.de` but not `xyz.google.de` or `xyz.google.com` (the `www.` has a special role)
- `google**` to block everything which starts with google
- `**google**` to block everything which contains google (`google.com`, `google.de`, `hellogoogle.xyz`, `xyz.hellogoogle.com`, ...)
- `google**facebook` to block everything which contains google and facebook in the same host (`google-loves-facebook.com` but not `facebook-loves-google.com` or `facebook.com` or `google.com`)

<br>
Coming back to the examples above: if you want to block every domain which contains `porn` in the host, use `**porn**` as the host.
For blocking all subdomains of `example.com` use `**.example.com`.