# Help wanted
Whilst English is the second most spoken language there are a lot of people out there which don't speak it. To appeal to as big of an audience as possible and to make it possible for everyone to enjoy the security and privacy this app offers.

This is where you come in: I speak German and English but can't deliver any other translations. This is why I'd like to ask anyone who is willing to help to consider contributing translations or checking existing translations.

# How it's done
I use an web-based translations editor called [Weblate](weblate.org) (open-source software, available under the GPLv3) to make managing translations easy.
I host my [own instance of Weblate](https://weblate.frostnerd.com) -- [creating an account](https://weblate.frostnerd.com/accounts/register/) is free and neither does the site contain ads, nor does it use any tracking.

See below for a quick tutorial on how to use Weblate.

# The current state
Right now the app only contains German and English translations. Your help is needed.

<a href="https://weblate.frostnerd.com/engage/nebulo/?utm_source=widget">
<img src="https://weblate.frostnerd.com/widgets/nebulo/-/multi-green.svg" alt="Translation state" />
</a>

# Privacy policy
Privacy is important and as such the Weblate server is configured to collect as little data as possible.
You can generally use the service without creating an account, but if you do the following data is collected:
 - Your e-mail address, used for logging in. This e-mail address is only going to be used for Weblate related topics.
    - We won't contact you for non-Weblate related topics. Furthermore this e-mail address isn't given out to any third party.
 - Your IP-address (only saved when trying to log in, or logging in), used in an audit log to combat spam and malicious actors.
 - A session ID as a cookie to keep you logged in (only stored when logging in)
    - A cookie is a small file on your device the website can read from and write to
    
Without creating an account (or trying to log in), no personal data is collected. IP addresses aren't logged on the HTTP server.
It is analyzed and [publicly displayed](https://weblate.frostnerd.com/stats/) which users contributed the most translations and which users were active the most.
No collected data is shared with any third party.
No collected data is analyzed automatically except for the usages described above.

You can remove your account at any time. Simply do this by opening [this page](https://weblate.frostnerd.com/accounts/remove/) whilst logged in. You'll receive an e-mail with a link you have to click to delete your account. Deleting your account deletes your profile, completely removing your e-mail address from the database. Your IP addresses used in the past won't be deleted and stay in the audit log.

Feel free to contact me at daniel.wolf@frostnerd.com if you wish to know what data is saved about you, or if you wish it to be deleted.

# Quick tutorial
Here's a quick tutorial on how to contribute a translation.

1. Open [Weblate](https://weblate.frostnerd.com).
2. Sign in or create an account (free, no tracking, no ads)
   - Your e-mail address will be saved with your hashed password. Your e-mail address is only used for the purpose of Weblate.
   - After creating an account you'll receive an e-mail with a confirmation link.
3. When logged in, [open the project](https://weblate.frostnerd.com/projects/nebulo/).
4. [Select one of the components](material/translating/project_overview.png) you want to contribute a translation to
5. If necessary, accept the contributor agreement ( [at the top of the page](material/translating/agreement.png))
6. Choose an existing language, or [start translating to a new language](material/translating/start_translation.png)
   - English is the default, it can't be edited
7. You'll be [shown a page](material/translating/component_overview.png) which gives an overview of the current translation state
8. [Select](material/translating/translation_overview.png) one of "All strings", "Strings needing action", "Not translated strings" or "Strings needing action without suggestions" or "Translated strings".
9. You'll be taken to the [translation editor](material/translating/translation_editor.png) (I choose "Not translated strings")
10. You can now select the translation source (English texts) and enter translations or suggestions for the language.
   - You can also switch to [Zen mode](material/translating/translation_editor_zen.png) using the button in the right corner.
11. There is much more this tool is able to do, feel free to experiment (or read [the docs](https://docs.weblate.org/en/weblate-3.6.1/index.html))!

# License
All your translations become part of this project and are thus managed under the [GPLv3 license](LICENSE) .
The copyright stays with you, but you grant me (Daniel Wolf) and this project (Nebulo) the right to make use of all translations you create and the right to distribute them.