package com.frostnerd.smokescreen.util

import android.os.AsyncTask

class RunnableAsyncTask(val runnable: () -> Unit) : AsyncTask<Void, Void, Void>() {
    companion object {
        fun launch(runnable: () -> Unit) {
            RunnableAsyncTask(runnable).execute()
        }
    }

    override fun doInBackground(vararg params: Void?): Void? {
        runnable()
        return null
    }
}