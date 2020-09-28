package com.frostnerd.smokescreen.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.fragment.SettingsFragment
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.LanguageContextWrapper

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

class SettingsActivity : BaseActivity() {
    companion object {
        private var category:Category? = null

        fun showCategory(context: Context, category: Category) {
            this.category = category
            Intent(context, SettingsActivity::class.java).apply {
                putExtra("category", category)
            }.also {
                context.startActivity(it)
            }
        }
    }

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageContextWrapper.attachFromSettings(this, newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.layoutStyleWithActionbar)
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().replace(android.R.id.content, SettingsFragment().apply {
            arguments = Bundle().apply {
                putSerializable("category", intent.getSerializableExtra("category") ?: category ?: Category.GENERAL)
            }
        }).commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if(item.itemId == android.R.id.home) {
            finish()
            true
        } else super.onOptionsItemSelected(item)
    }

    enum class Category {
        GENERAL, NOTIFICATION, PIN, CACHE, LOGGING, IP, NETWORK, QUERIES, SERVER_MODE
    }
}