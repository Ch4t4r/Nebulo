package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.log
import com.frostnerd.smokescreen.service.DnsVpnService
import kotlinx.android.synthetic.main.dialog_query_generator.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.Exception
import kotlin.random.Random

/*
 * Copyright (C) 2019 Daniel Wolf (Ch4t4r)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the developer at daniel.wolf@frostnerd.com.
 */
class QueryGeneratorDialog(context: Context):AlertDialog(context, context.getPreferences().theme.dialogStyle){
    val websiteDomains = setOf("bild.de", "washingtonpost.com", "cnn.com", "bbc.com", "nytimes.com", "huffingtonpost.com",
        "reuters.com", "abcnews.go.com", "timesofindia.indiatimes.com", "theguardian.com", "bloomberg.com",
        "Oneindia.com", "News18.com", "Hindustantimes.com", "Firstpost.com", "Indianexpress.com",
        "Manoramaonline.com",
        "spiegel.de", "focus.de", "n-tv.de", "welt.de", "faz.net", "stern.de", "t3n.de", "facebook.com",
        "twitter.com",
        "baidu.com", "yahoo.com", "instagram.com", "vk.com", "wikipedia.org", "qq.com", "taobao.com", "tmail.com",
        "google.co.in", "google.com", "google.de", "reddit.com", "sohu.com", "live.com", "jd.com", "yandex.ru",
        "weibo.com", "sina.com.cn", "google.co.jp", "360.cn", "login.tmail.com", "blogspot.com", "netflix.com",
        "google.com.hk", "linkedin.com", "google.com.br", "google.co.uk", "yahoo.co.jp", "csdn.net",
        "pages.tmail.com",
        "twitch.tv", "google.ru", "google.fr", "alipay.com", "office.com", "ebay.com", "microsoft.com", "bing.com",
        "microsoftonline.com", "aliexpress.com", "msn.com", "naver.com", "ebay-kleinanzeigen.de", "paypal.com",
        "t-online.de", "chip.de", "heise.de", "golem.de", "otto.de", "postbank.de", "whatsapp.com", "mobile.de",
        "wetter.com", "wetter.de", "tumblr.com", "booking.com", "idealo.de", "bahn.de", "amazon.com", "amazon.de",
        "ebay.de", "google.ch", "20min.ch", "blinck.ch", "srf.ch", "ricardo.ch", "bluewin.ch", "sbb.ch",
        "postfinance.ch", "digitec.ch", "admin.ch", "gmx.de", "imdb.net", "gmx.at", "gmx.de", "tribunnews.com",
        "stackoverflow.com", "apple.com", "wordpress.com", "imgur.com", "wikia.com", "amazon.co.uk", "pinterest.com",
        "adobe.com", "amazon.in", "dropbox.com", "quora.com", "google.es", "google.cn", "amazonaws.com",
        "salesforce.com", "chase.com", "spotify.com", "telegram.org", "steampowered.com", "skype.com", "sky.de",
        "sky.com", "teamspeak.com", "maps.google.com", "9gag.com", "vw.de", "discord.gg", "nytimes.com",
        "stackexchange.com", "craigslist.com", "soundcloud.com", "vimeo.com", "panda.tv", "ask.com",
        "steamcommunity.com", "softonic.com", "dailymotion.com", "ebay.co.uk", "godaddy.com", "discordapp.com",
        "vice.com", "walmart.com", "alibaba.com", "amazon.es", "cnet.com", "google.pl", "yelp.com", "duckduckgo.com",
        "blogger.com", "wellsfargo.com", "deviantart.com", "wikihow.com", "dailymail.co.uk", "shutterstock.com",
        "gamepedia.com", "amazon.ca", "udemy.com", "ikea.de", "ikea.com", "speedtest.com", "medium.com", "hulu.com",
        "tripadvisor.com", "archive.org", "forbes.com", "airbnb.com", "genius.com", "americanexpress.com",
        "google.com.ua",
        "businessinsider.com", "bitcoin.com", "bitcoin.de", "glassdor.com", "fiverr.com", "crunchyroll.com",
        "sourceforge.net", "samsung.com", "fedex.com", "target.com", "google.gr", "dell.com", "lenovo.com",
        "playstation.com", "siteadvisor.com", "hola.com", "oracle.com", "cnbc.com", "news.google.de", "upwork.com",
        "icloud.com", "wp.pl", "nike.com", "web.de", "sohu.com", "weibo.com", "csdn.net", "mail.ru", "t.co",
        "naver.com",
        "github.com", "msn.de", "googleusercontent.com", "lovoo.com", "tinder.com", "lovoo.de", "tinder.de",
        "gmail.com",
        "viber.com", "hp.com", "snapchat.com", "minecraft.net", "minecraft.de", "minecraft.com", "mojang.com",
        "bitmoji.com", "messenger.com", "cleanmasterofficial.com", "king.com", "itunes.apple.com", "line.me",
        "flipboard.com", "translate.google.com", "uber.com", "pandora.com", "wish.com", "tiktok.com",
        "fortnite.com", "epicgames.com", "geoguessr.com", "asoftmurmur.com", "camelcamelcamel.com",
        "hackertyper.net", "xkcd.com", "flickr.com", "bit.ly", "w3.org", "europa.eu", "wp.com", "statcounter.com",
        "jimdo.com", "weebly.com", "mozilla.org", "myspace.com", "stumpleupon.com", "gravatar.com",
        "digg.com", "wixsite.com", "wix.com", "e-recht24.de", "slideshare.net", "telegraph.co.uk", "amzn.to",
        "livejournal.com", "bing.com", "time.com", "immobilienscout24.de", "check24.de", "computerbild.de",
        "dhl.de", "chefkoch.de", "booking.com", "mediamarkt.de", "idealo.de", "zdf.de", "gutefrage.net",
        "pr0gramm.com", "statista.com", "germanglobe.com", "alexa.com", "tribunnews.com",
        "Bukalapak.com", "Detik.com", "Google.co.id", "Tokopedia.com", "kompas.com", "Liputan6.com", "okezone.com",
        "Sindonews.com", "grid.id", "Kumparan.com", "Merdeka.com", "Blibli.com", "Kapanlagi.com", "Uzone.id",
        "Alodokter.com", "cnnindonesia.com", "viva.co.id", "viva.com", "brilio.net", "vidio.com", "Tempo.co",
        "suara.com", "bola.net", "shopee.co.id", "wowkeren.com", "popads.net", "Academia.edu", "imdb.com",
        "Instructure.com", "Etsy.com", "Bankofamerica.com", "force.com", "zillow.com", "bestbuy.com",
        "Mercadolivre.com.br", "globo.com", "bet365.com", "fbcdn.net", "tagesschau.de", "kochbar.de",
        "kochhaus.de", "kochmedia.com", "lidl.de", "aldi.de", "cookpad.com", "allrecipes.com", "marmiton.org",
        "foodnetwork.com", "delish.com", "tasteofhome.com", "russianfood.com", "geniuskitchen.com",
        "archdaily.com", "freshome.com", "pulitzer.org", "fandom.com", "purepeople.com", "tmz.com", "digitaspy.com",
        "gala.fr", "fashion-press.net", "desired.de", "fanfiction.net", "fontawesome.com", "thechive.com",
        "sephora.com", "myfitnesspal.com", "fitnessblender.com", "beauty.hotpepper.jp", "ulta.com",
        "bodybuilding.com",
        "natura.net", "livestrong.com", "menshealth.com", "womanshealth.com", "cosme.net", "weightwatchers.com",
        "noom.com", "dhzw.com", "weightwatchers.de", "weightwatchers.ca", "slism.jp", "bmi-rechner.net",
        "weightwatchers.co.uk",
        "agoda.com", "hotels.com", "jalan.net", "mariott.com", "hilton.com", "airbnb.fr", "airbnb.co.uk",
        "airbnb.com.br",
        "trivago.com", "airbnb.es", "airbnb.it", "tmall.com", "rakuten.co.jp", "allegro.pl", "target.com",
        "gearbest.com",
        "homedepot.com", "accuweather.com", "wetteronline.de", "globo.com", "sohu.com", "espn.com", "foxnews.com",
        "weather.com", "sports.yahoo.com", "marca.com", "news.mail.ru", "mail.ru", "ibm.com", "intel.com", "amd.com",
        "acer.com", "corsair.com", "lenovo.com.cn", "oneplus.net", "hardware.fr", "pcgarage.ro", "seagate.com",
        "evga.com",
        "gigabyte.com", "epson.com", "ampproject.org", "office.com", "microsoftonline.com", "miui.com",
        "sharepoint.com",
        "onlinevideoconverter.com", "trello.com", "feedly.com", "zendesk.com", "dcinside.com", "huawei.com",
        "evernote.com",
        "norton.com", "mcafee.com", "android.com", "prezi.com", "xda-developers.com", "genius.com", "go.com",
        "patreon.com",
        "gfycat.com", "primevideo.com", "pandora.com", "youtube.com", "ostfalia.de", "usatoday.com", "politico.com",
        "npr.org", "latimes.com", "nbcnews.com",
        "cbsnews.com", "nypost.com", "dw.de", "fr-online.de", "heute.de", "sueddeutsche.de", "taz.de", "thelocal.de",
        "ndtv.com",
        "indiatoday.intoday.in", "thehindu.com", "admob.com", "vwfs.de", "deutsche-bank.de", "dzbank.de", "kfw.de",
        "stw-on.de", "commerzbank.de", "unicreditgroup.eu", "pornhub.com", "youporn.com", "xnxx.com", "redtube.com",
        "livejasmin.com", "xvideos.com",
        "xhamster.com", "porn555.com", "bongacams.com", "speakol.com", "bodelen.com", "etsy.com", "txxx.com",
        "cricbuzz.com",
        "soso.com", "ettoday.net", "indeed.com", "bilibili.com", "exosrv.com", "xinhuanet.com", "thomann.de",
        "pinterest.de",
        "mydealz.de", "dkb.de", "vodafone.de", "1und1.de", "comdirect.de", "wikibooks.org", "wechat.com", "duden.de",
        "edeka.de", "zalando.de", "saturn.de", "giga.de", "arbeitsagentur.de", "deutschepost.de", "linguee.de",
        "transfermarkt.de",
        "flirtcafe.de", "real.de", "holidaycheck.de", "payback.de", "immowelt.de", "pcwelt.de",
        "notebooksbilliger.de",
        "ardmediathek.de", "epochtimes.de", "conrad.de", "dpd.de", "myhermes.de", "meinestadt.de", "stepstone.de",
        "berliner-sparkasse.de", "netzwelt.de", "markt.de", "computerbase.de", "ndr.de", "br.de", "wdr.de",
        "bonprix.de",
        "motor-talk.de", "tvspielfilm.de", "tagesspiegel.de", "hornbach.de", "tripadvisor.de", "rtl.de", "ard.de",
        "eventim.de", "sport1.de", "obi.de", "moviepilot.de", "thalia.de", "merkur.de", "galeria-kaufhof.de",
        "gamestar.de",
        "immonet.de", "filmstarts.de", "dastelefonbuch.de", "autoscout24.de", "unitymedia.de", "strato.de",
        "goethe.de",
        "targobank.de", "consorsbank.de", "tchibo.de", "aol.de", "kleiderkreisel.de", "rp-online.de",
        "uni-muenchen.de",
        "berlin.de", "home24.de", "geizhals.de", "aldi-sued.de", "elster.de", "ab-in-den-urlaub.de", "autobild.de",
        "pearl.de", "porn.com", "Onlinesbi.com", "Hotstar.com", "Flipkart.com", "Irctc.co.in", "Hdfcbank.com",
        "Icicibank.com",
        "Billdesk.com", "Droom.in", "Uidai.gov.in", "Freejobalert.com", "Rediff.com", "Xilbalar.com", "Naukri.com",
        "Bookmyshow.com", "Manoramaonline.com", "Paytm.com", "W3schools.com", "Justdial.com", "Ibps.in",
        "Epfindia.gov.in",
        "Rly-rect-appn.in", "chaturbate.com", "mlb.com", "wellsfargo.com", "xfinity.com", "intuit.com",
        "homedepot.com",
        "mercadolivre.com.br", "uol.com.br", "olx.com.br", "americanas.com.br", "sp.gov.br", "bet365.com",
        " fazenda.gov.br",
        "terra.com.br", "itau.com.br", "bol.uol.com.br", "vivo.com.br", "abril.com.br", "sambaporno.com",
        "correios.com.br",
        "santander.com.br", "eis.de", "outlook.com", "outlook.de", "bluewin.ch", "nativeplanet.com", "gizbot.com",
        "mobilesyrup.com", "virustotal.com", "betanews.com", "webmd.com", "news.ycombinator.com", "producthunt.com",
        "recode.net", "glassdoor.ca", "filterlists.com", "finya.de", "linkfire.com", "cnet.com", "thehackernews.com",
        "openload.co", "autotrader.co.uk", "ispreview.co.uk", "miamiherald.com", "app.adjust.com", "dm.de", "api.onetracker.app",
        "mobiles.co.uk", "firebog.net", "kicx.in", "fabsdeals.com", "trade.aliexpress.com", "clk.ink", "androidauthority.com",
        "dnscrypt.info", "olymptrade.com", "oload.info", "apkmb.com", "17track.net", "getalinkandshare.com",
        "uplod.ws", "sadeempc.com", "rayjump.com", "incibe.es", "primbon.com", "teufel.de", "adobeid.services.adobe.com",
        "publicdns.xyz", "droidviews.com", "sadeemapk.com", "repubblica.it", "airtel.in", "myshadow.org",
        "nseindia.com", "civey.com", "pcmag.com", "kaskus.co.id", "trakt.tv", "theintercept.com", "nzz.ch", "abgeordnetenwatch.de",
        "cilip.de", "nordkurier.de", "phrack.org", "lgtm.com", "arstechnica.com", "thehill.com", "washingtonian.com",
        "thegazette.com", "fusion-festival.de", "digitale-gesellschaft.ch", "hollywoodreporter.com", "techcrunch.com",
        "motherboard.vice.com", "bleepingcomputer.com", "zdnet.com", "coindesk.com", "lifehacker.com.au", "wired.com",
        "theverge.com", "kotaku.com", "fortnitetracker.com", "fortniteintel.com", "spieletipps.de", "ign.com",
        "fortniteinsider.com", "dotesports.com", "gamesradar.com", "pcgamer.com", "gameinformer.com", "pockettactics.com",
        "nintendo.com", "ubisoft.com", "retrogamer.net", "gamestm.co.uk", "xbox.com", "activisionblizzard.com",
        "ea.com", "sony.com", "mobilesyrup.com", "newrelic.com", "hackr.io", "laravel.com", "angular.io", "reactjs.org",
        "vuejs.org", "emberjs.com", "backbonejs.com", "developers.cloudflare.com", "cloudflare.com", "curiositybox.com",
        "mobafire.com", "humblebundle.com", "denic.de", "eingeek.de", "sogou.com", "bitly.com", "goo.gl", "coccoc.com",
        "hna.de", "cambridge.org", "wiley.com", "nike.com", "lowes.com", "macys.com", "newegg.com", "bhphotovideo.com",
        "ticketmaster.com", "wayfair.com", "groupon.com", "nordstrom.com", "rakuten.com", "oup.com",
        "sephora.com", "iherb.com", "kohls.com", "gap.com", "redbubble.com", "autotrader.com", "cargurus.com",
        "bodybuilding.com", "rei.com", "zappos.com", "walgreens.com", "shutterfly.com", "cars.com", "forever21.com",
        "directv.com", "overstock.com", "ifixit.com", "cvs.com", "barnesandnoble.com", "yoox.com", "jcpenney.com",
        "staples.com"
    )
    val websiteUrls = setOf("https://www.heise.de/newsticker/meldung/10-Jahre-Bitcoin-Am-Anfang-war-die-Blockchain-4262899.html",
        "https://www.heise.de/newsticker/meldung/Gehackte-Daten-Politiker-beklagen-schweren-Angriff-auf-die-Demokratie-4265847.html",
        "https://www.golem.de/news/xbox-microsoft-arbeitet-an-force-feedback-tasten-1901-138510.html",
        "http://www.spiegel.de/plus/platz-da-jungs-a-00000000-0002-0001-0000-000161665893",
        "https://www.tagesschau.de/ausland/trump-2217.html",
        "https://www.waz.de/panorama/pabuk-tropensturm-thailand-auslaeufer-treffen-auf-kueste-id216127671.html",
        "https://www.bild.de/news/ausland/news-ausland/drama-in-escape-room-fuenf-maedchen-sterben-bei-brand-in-polen-59366016.bild.html",
        "https://www.gamepro.de/artikel/the-legend-of-zelda-fan-entdeckt-die-mysterioese-minus-welt,3338917.html",
        "https://www.moviepilot.de/news/scrubs-diese-folge-wurde-vom-sender-eiskalt-gestrichen-1115069",
        "https://web.de/magazine/panorama/schuesse-koelner-innenstadt-streit-rocker-milieu-33499604",
        "https://www.washingtonpost.com/world/europe/four-countries-now-have-links-to-american-detained-in-russia-as-international-spillover-widens/2019/01/04/25d93b2e-1029-11e9-8f0c-6f878a26288a_story.html",
        "https://www.alexa.com/topsites",
        "https://breaking-news-saarland.de/wegen-parkendem-auto-drehleiterwagen-hat-probleme-zur-einsatzstelle-zu-kommen/",
        "https://www.androidpit.de/android-updates-diese-hersteller-pflegen-ihre-smartphones-am-besten",
        "https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Expect-CT",
        "https://indianexpress.com/article/technology/tech-news-technology/proposed-changes-in-it-rules-will-lead-to-over-censorship-undermine-encryption-warns-mozilla-5522034/lite/",
        "https://www.itproportal.com/features/uncovering-the-hidden-risks-in-encrypted-data/",
        "https://www.bleepingcomputer.com/ransomware/decryptor/how-to-decrypt-the-fileslocker-ransomware-with-fileslockerdecrypter/",
        "https://dontkillmyapp.com/oneplus",
        "https://www.scmagazineuk.com/uncaptcha2-manages-bypass-googles-recaptcha-system/article/1522085",
        "https://www.securityweek.com/massive-data-leak-targets-german-officials-including-merkel",
        "https://www.bbc.co.uk/news/world-asia-china-46733174",
        "https://t3n.de/news/it-sicherheit-angriffe-1135404/",
        "https://www.youtube.com/watch?v=JsB57FtaxXQ",
        "https://www.reddit.com/r/aww/comments/bly7vk/that_doggo_found_all_this_at_a_traffic_stop_in_a/",
        "https://css-tricks.com/snippets/css/a-guide-to-flexbox/",
        "https://hellsoft.se/how-to-service-on-android-part-1-63c43d7301b3?gi=c7f17ffe6dd3",
        "https://elemental.medium.com/how-do-vegans-survive-f6617ba678e4",
        "https://humanparts.medium.com/baby-weight-8c7dcb3986ab",
        "https://onezero.medium.com/sex-with-robots-was-never-the-point-4b4b662ec78a",
        "https://medium.com/better-humans/project-planning-tips-and-tales-6d05a0a100a0",
        "https://news.yahoo.com/latest-fighting-israels-gaza-blockade-173349542.html",
        "https://news.yahoo.com/warren-buffett-says-trade-war-bad-whole-world-112659466--sector.html",
        "https://edition.cnn.com/2019/05/08/middleeast/iran-nuclear-deal-intl/index.html",
        "https://www.nytimes.com/interactive/2019/05/07/us/politics/donald-trump-taxes.html",
        "https://www.foxnews.com/politics/trump-blasts-hit-job-nytimes-story",
        "https://eu.usatoday.com/story/life/2019/05/08/royal-baby-photos-meghan-markle-prince-harry-pose-newborn/1120765001/",
        "https://www.reuters.com/news/us",
        "https://www.reuters.com/article/us-usa-crime-cult/alleged-new-york-sex-cult-slave-to-testify-at-founders-sex-trafficking-case-idUSKCN1SE12W",
        "https://www.politico.com/magazine/story/2019/05/08/self-deportation-trump-immigration-policy-trend-226801",
        "https://www.politico.com/story/2019/05/08/elizabeth-warren-opioid-crisis-policy-1310255",
        "https://www.latimes.com/local/lanow/la-me-nipsey-hussle-opportunity-zone-tax-crenshaw-gentrification-20190508-story.html",
        "https://www.nbcnews.com/politics/2020-election/kamala-harris-blows-past-democratic-rivals-fundraising-communities-color-n1000031",
        "https://www.cbsnews.com/news/colorado-shooting-stem-school-highlands-ranch-student-says-suspect-devon-erickson-talked-about-committing-this-kind/",
        "https://pagesix.com/2019/05/07/jeremy-scotts-met-gala-afterparty-was-unexpectedly-a-disaster/",
        "https://abcnews.go.com/GMA/Culture/prince-harry-meghan-debut-newborn-son-windsor-castle/story?id=62874576",
        "https://store.steampowered.com/app/730/CounterStrike_Global_Offensive",
        "https://www.paypal.com/de/webapps/mpp/working-capital",
        "https://www.instagram.com/cristiano",
        "https://l.instagram.com/?u=http%3A%2F%2Ffacebook.com%2Fcristiano&e=ATM55Etg3b7-a3AA9XLXh2CcxQjynmbdXAvnC4FmuyDzM2i7nijBlpj-AKwh0wlcOjvuwnWalndziR9qUihSEL1a",
        "https://twitter.com/cristiano?lang=de",
        "https://www.stw-on.de/wolfenbuettel/essen/menus/mensa-ostfalia",
        "https://github.com/r0x0r/pywebview",
        "https://betanews.com/2019/05/02/mozilla-bans-obfuscated-code/",
        "https://defaultreasoning-com.cdn.ampproject.org/v/s/defaultreasoning.com/2019/04/30/vcf-3-automatically-replace-ssl-certificates/amp/?amp_js_v=a2&amp_gsa=1#referrer=https%3A%2F%2Fwww.google.com&amp_tf=From%20%251%24s&ampshare=https%3A%2F%2Fdefaultreasoning.com%2F2019%2F04%2F30%2Fvcf-3-automatically-replace-ssl-certificates%2F",
        "https://taylorswift.lnk.to/MeYD",
        "https://www.recode.net/2018/1/23/16905844/media-landscape-verizon-amazon-comcast-disney-fox-relationships-chart",
        "https://fm4.orf.at/player/live",
        "https://www.ispreview.co.uk/index.php/2019/04/big-uk-broadband-isps-have-big-concerns-about-dns-over-https.html/2",
        "https://www.washingtonexaminer.com/news/yang-proposal-could-mean-prison-for-owners-like-zuckerberg-and-buffett",
        "https://www.heise.de/autos/artikel/Neu-Polestar-2-4321854.html",
        "https://www.amazon.de/dp/B00NTQ6K7E?tag=cb-noscript-21",
        "https://www.publicdns.xyz/country/eg.html",
        "https://www.airtel.in/digital-tv",
        "https://theintercept.com/2019/05/09/alexandria-ocasio-cortez-bernie-sanders-bank-legislation/",
        "https://www.nzz.ch/international/deutschland/die-europaeer-sind-im-konflikt-zwischen-iran-und-den-usa-hilflos-ld.1480698?utm_source=pocket-newtab",
        "https://www.abgeordnetenwatch.de/blog/2019-05-03/parteispenden-stopp-von-daimler-politik-im-panikmodus",
        "https://www.cilip.de/2019/05/09/bka-rentner-hat-islands-groessten-justizirrtum-verantwortet/",
        "https://www.nordkurier.de/mueritz/siegfried-stangs-stellungnahme-zum-fusion-festival-0935444605.html",
        "http://phrack.org/papers/jit_exploitation.html",
        "https://lgtm.com/blog/ghostscript_CVE-2018-19134_exploit",
        "https://arstechnica.com/information-technology/2019/05/hackers-breached-3-us-antivirus-companies-researchers-reveal/",
        "https://www.microsoft.com/en-us/research/blog/evercrypt-cryptographic-provider-offers-developers-greater-security-assurances/",
        "https://thehill.com/policy/technology/442817-pompeo-warns-us-may-not-share-intel-with-uk-if-it-lets-huawei-into-5g",
        "https://www.washingtonian.com/2019/05/05/what-happened-after-my-13-year-old-son-joined-the-alt-right/",
        "https://www.bloomberg.com/news/articles/2019-05-06/who-to-sue-when-a-robot-loses-your-fortune",
        "https://www.thegazette.com/subject/news/nation-and-world/liquid-death-sells-water-to-tech-bros-who-are-too-cool-for-alcohol-20190507",
        "https://spiegel.de/article.do?id=1266413",
        "https://www.tagesschau.de/ausland/pompeo-deutschland-irak-iran-101.html",
        "https://www.heise.de/-4416766",
        "https://devblogs.microsoft.com/commandline/announcing-wsl-2/",
        "https://19.mediaconventionberlin.com/de/user/21140",
        "https://www.politico.com/story/2019/05/06/pompeo-arctic-china-russia-1302649",
        "https://smw.ch/en/article/doi/smw.2019.20071/",
        "https://smw.ch/en/article/doi/smw.2019.20071/",
        "https://www.theguardian.com/world/2019/may/04/5g-mobile-networks-threat-to-world-weather-forecasting",
        "https://daringfireball.net/linked/2019/05/01/cook-maestri-intel",
        "https://www.zdnet.com/article/a-hacker-is-wiping-git-repositories-and-asking-for-a-ransom/",
        "https://bugzilla.mozilla.org/show_bug.cgi?id=1548973",
        "https://www.digitale-gesellschaft.ch/2019/04/30/kein-leistungsschutzrecht-in-der-schweiz-zivilgesellschaft-wirkt/",
        "https://www.bbc.com/news/uk-england-suffolk-48117678",
        "https://www.theregister.co.uk/2019/05/02/cisco_vulnerabilities/",
        "http://blog.chriszacharias.com/a-conspiracy-to-kill-ie6",
        "https://www.hollywoodreporter.com/news/scientology-ship-quarantined-measles-diagnosis-1206662",
        "https://techcrunch.com/2019/04/30/aws-opens-up-its-managed-blockchain-as-a-service-to-everybody/",
        "https://motherboard.vice.com/en_us/article/d3np4y/hackers-steal-ransom-citycomp-airbus-volkswagen-oracle-valuable-companies",
        "https://economictimes.indiatimes.com/news/et-explains/an-india-china-maneouvre-could-soon-leave-worlds-oil-powers-toothless/articleshow/69095599.cms?from=mdr",
        "https://www.bleepingcomputer.com/news/security/hacked-docker-hub-database-exposed-sensitive-data-of-190k-users/",
        "https://rp-online.de/leben/auto/news/kba-geht-neuem-manipulationsverdacht-bei-daimler-nach-diesel-suv-betroffen_aid-38098097",
        "https://www.golem.de/news/bundesinstitut-fuer-risikobewertung-benutzername-dummy-passwort-doof-1905-141148.html",
        "https://www.euractiv.de/section/eu-aussenpolitik/news/exclusiv-deutschland-blockiert-eu-plaene-gegen-spyware-handel-mit-diktatoren/",
        "https://www.chefkoch.de/rezepte/1611411268351373/Nudelteig-fuer-perfekte-Pasta.html",
        "https://www.netzwelt.de/datenschutz/95572_2-vorratsdatenspeicherung-so-lange-daten-deutschland-gespeichert.html#aktueller-stand-januar-2019",
        "https://www.coindesk.com/binance-may-consider-bitcoin-rollback-following-40-million-hack",
        "https://www.dailymail.co.uk/news/article-5447125/Hilarious-moment-woman-reacts-losing-eyebrow.html",
        "https://www.lifehacker.com.au/2019/04/chinas-people-monitoring-software-being-deployed-in-darwin/",
        "https://www.theverge.com/2019/5/6/18534687/microsoft-windows-10-linux-kernel-feature",
        "https://kotaku.com/how-hollywood-didnt-screw-up-the-detective-pikachu-movi-1834662397",
        "https://verysmartbrothas.theroot.com/ayesha-curry-said-what-many-moms-feel-1834635837",
        "https://www.spieletipps.de/artikel/10015/1/",
        "https://www.ign.com/wikis/fortnite/Weekly_Challenges",
        "https://hackr.io/blog/top-10-web-development-frameworks-in-2019",
        "https://fontawesome.com/icons?d=gallery&q=c",
        "https://developers.cloudflare.com/1.1.1.1/dns-over-https/wireformat/",
        "https://www.bild.de/regional/berlin/berlin-aktuell/ein-ermittler-packt-aus-polizei-frust-ueber-drogenpark-goerli-in-berlin-61829802.bild.html",
        "https://www.bild.de/bild-plus/politik/inland/politik-inland/um-steuerloch-zu-stopfen-groko-will-renten-milliarden-pluendern-61829146,view=conversionToLogin.bild.html",
        "https://www.myhomebook.de/gardening/pflanzen/bedeutung-beliebte-schnittblumen?ref=1",
        "https://www.bild.de/sport/fussball/fussball/mino-raiola-spielberater-von-fifa-gesperrt-platzen-jetzt-transfers-61832452.bild.html",
        "https://www.bild.de/sport/fussball/fussball/fc-bayern-seit-19-jahren-wurde-der-verein-immer-auswaerts-meister-61822484.bild.html",
        "https://www.bild.de/bild-plus/unterhaltung/leute/leute/die-lochis-youtube-zwillinge-machen-schluss-61812062,view=conversionToLogin.bild.html",
        "https://www.bild.de/news/inland/news-inland/lotto-eurojackpot-geknackt-deutsche-und-polen-holen-sich-90-millionen-gewinn-61831572.bild.html",
        "https://www.bild.de/bild-plus/regional/duesseldorf/duesseldorf-aktuell/blumenladen-mord-von-neuss-killer-war-dsds-kandidat-61827850,view=conversionToLogin.bild.html",
        "https://www.bild.de/unterhaltung/tv/tv/samba-zoff-bei-lets-dance-massimo-sinat-belehrt-juror-joachim-llambi-61833342.bild.html",
        "https://www.bild.de/video/clip/elefant/elefant-haengt-ueber-zaun-viralpress-61819274.bild.html"
        )
    private var job: Job? = null
    private var loadingDialog:AlertDialog? = null

    init {
        setTitle("Query generator")
        val view = layoutInflater.inflate(R.layout.dialog_query_generator, null, false)
        val iterations = view.findViewById<EditText>(R.id.iterations)
        val callDomains = view.findViewById<CheckBox>(R.id.baseDomains)
        val callDeepurls = view.findViewById<CheckBox>(R.id.deepurls)
        val useChrome = view.findViewById<CheckBox>(R.id.useChrome)
        val useRandomDelay = view.findViewById<CheckBox>(R.id.randomTimeout)
        val delay = view.findViewById<EditText>(R.id.delay)

        setView(view)
        setButton(DialogInterface.BUTTON_POSITIVE, "Go") { dialog, _ ->
            val urlsToUse:MutableList<String> = mutableListOf()
            if(callDomains.isChecked) {
                urlsToUse.addAll(websiteDomains)
            }
            if(callDeepurls.isChecked) {
                urlsToUse.addAll(websiteUrls)
            }
            val runCount = iterations.text.toString().toIntOrNull() ?: 1
            job = GlobalScope.launch {
                context.log("Generating queries for ${urlsToUse.size} urls $runCount times", "[QueryGenerator]")
                val logFileWriter = BufferedWriter(FileWriter(File(context.filesDir, "querygenlog.txt"), true))
                val callWithChrome = useChrome.isChecked
                var domainCount = 0
                for(i in 0 until runCount) {
                    urlsToUse.shuffle()
                    for (url in urlsToUse) {
                        openUrl(callWithChrome, if(url.startsWith("http")) url else "http://$url")
                        logFileWriter.write(System.currentTimeMillis().toString() + " '" + url + "'\n")
                        logFileWriter.flush()
                        val delayMs = delay.text.toString().toLong() + if(randomTimeout.isChecked) Random.nextLong(0, 20000) else 0
                        delay(delayMs)
                        if(++domainCount % 20 == 0 && callWithChrome) {
                            killChrome()
                        }
                        DnsVpnService.restartVpn(context, false)
                        delay(1500)
                    }
                }
                job = null
                loadingDialog?.cancel()
            }
            showLoadingDialog()
            dialog.dismiss()
        }
        show()
    }

    private fun killChrome() {
        try {
            val process = Runtime.getRuntime().exec("su && ps -ef | grep chrome | grep -v grep | awk '{print \$2}' | xargs kill -9")
            process.waitFor()
        } catch (ex:Exception) {
            ex.printStackTrace()
        }
    }

    private fun showLoadingDialog() {
        loadingDialog = AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
            .setTitle("Generating queries")
            .setCancelable(false)
            .setNegativeButton("Stop") { dialog, _ ->
                job?.cancel()
                dialog.dismiss()
            }
            .setMessage("Currently importing queries...").create()
        loadingDialog?.setCanceledOnTouchOutside(false)
        if(job == null) {
            loadingDialog?.cancel()
            loadingDialog = null
        } else loadingDialog?.show()
    }

    private fun openUrl(withChrome:Boolean, url:String){
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        if(withChrome) intent.setPackage("com.android.chrome")
        else intent.setPackage("com.aplustech.minimalbrowserxfree")
        context.startActivity(intent)
    }
}