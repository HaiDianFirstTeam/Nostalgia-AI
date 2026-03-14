package com.haidianfirstteam.nostalgiaai.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.haidianfirstteam.nostalgiaai.R
import com.haidianfirstteam.nostalgiaai.ui.BaseActivity

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        title = getString(R.string.about_title)
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, AboutActivity::class.java)
    }
}
