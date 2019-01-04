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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Copyright Daniel Wolf 2019
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class QueryGeneratorDialog(context: Context):AlertDialog(context, context.getPreferences().theme.dialogStyle){
    val websiteDomains = setOf("bild.de", "washingtonpost.com", "cnn.com", "bbc.com", "nytimes.com", "huffingtonpost.com",
        "reuters.com", "abcnews.go.com", "timesofindia.indiatimes.com", "theguardian.com", "bloomberg.com",
        "Oneindia.com", "News18.com", "Hindustantimes.com", "Firstpost.com", "Indianexpress.com", "Manoramaonline.com",
        "spiegel.de", "focus.de", "n-tv.de", "welt.de", "faz.net", "stern.de", "t3n.de", "facebook.com", "twitter.com",
        "baidu.com", "yahoo.com", "instagram.com", "vk.com", "wikipedia.org", "qq.com", "taobao.com", "tmail.com",
        "google.co.in", "google.com", "google.de", "reddit.com", "sohu.com", "live.com", "jd.com", "yandex.ru",
        "weibo.com", "sina.com.cn", "google.co.jp", "360.cn", "login.tmail.com", "blogspot.com", "netflix.com",
        "google.com.hk", "linkedin.com", "google.com.br", "google.co.uk", "yahoo.co.jp", "csdn.net", "pages.tmail.com",
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
        "tripadvisor.com", "archive.org", "forbes.com", "airbnb.com", "genius.com", "americanexpress.com", "google.com.ua",
        "businessinsider.com", "bitcoin.com", "bitcoin.de", "glassdor.com", "fiverr.com", "crunchyroll.com",
        "sourceforge.net", "samsung.com", "fedex.com", "target.com", "google.gr", "dell.com", "lenovo.com",
        "playstation.com", "siteadvisor.com", "hola.com", "oracle.com", "cnbc.com", "news.google.de", "upwork.com",
        "icloud.com", "wp.pl", "nike.com", "web.de", "sohu.com", "weibo.com", "csdn.net", "mail.ru", "t.co", "naver.com",
        "github.com", "msn.de", "googleusercontent.com", "lovoo.com","tinder.com","lovoo.de","tinder.de","gmail.com",
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
        "Mercadolivre.com.br", "globo.com", "bet365.com", "fbcdn.net", "tagesschau.de"
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
        ""
        )
    private var job: Job? = null
    private var loadingDialog:AlertDialog? = null

    init {
        setTitle("Query generator")
        val view = layoutInflater.inflate(R.layout.dialog_query_generator, null, false)
        val iterations = view.findViewById<EditText>(R.id.iterations)
        val callDomains = view.findViewById<CheckBox>(R.id.baseDomains)
        val callDeepurls = view.findViewById<CheckBox>(R.id.deepurls)
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
                for(i in 0 until runCount) {
                    urlsToUse.shuffle()
                    for (url in urlsToUse) {
                        openWithChrome(if(url.startsWith("http")) url else "http://$url")
                        delay(20000 + Random.nextLong(0, 25000))
                        DnsVpnService.restartVpn(context, false)
                        delay(3000)
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

    private fun openWithChrome(url:String){
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setPackage("com.android.chrome")
        context.startActivity(intent)
    }
}